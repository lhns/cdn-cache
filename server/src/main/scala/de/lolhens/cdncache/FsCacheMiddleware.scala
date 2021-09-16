package de.lolhens.cdncache

import cats.data.OptionT
import cats.effect.std.Queue
import cats.effect.{IO, Ref}
import cats.syntax.option._
import de.lolhens.cdncache.FsCacheMiddleware.CacheObjectMetadata
import de.lolhens.fs2.utils.Fs2Utils._
import fs2.io.file.{Files, Path => Fs2Path}
import fs2.{Chunk, Pull, Stream}
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.dsl.io._
import org.http4s.headers.{`Content-Length`, `Content-Type`}
import org.http4s.{Header, HttpRoutes, StaticFile}
import scodec.bits.ByteVector

import java.nio.file.Path

class FsCacheMiddleware private(
                                 cachePath: Path,
                                 val modeRef: Ref[IO, Mode]
                               ) {
  def listEntries: Stream[IO, CacheEntry] =
    Files[IO].list(Fs2Path.fromNioPath(cachePath)).parEvalMap(8)(path =>
      Files[IO].readAll(path).takeWhile(_ != '\n').through(fs2.text.utf8.decode).compile.string.map { metadataString =>
        val metadata = decode[CacheObjectMetadata](metadataString).toTry.get
        CacheEntry(
          uri = metadata.uri.getOrElse("<unknown>"),
          contentType = metadata.contentType.map(Header[`Content-Type`].value),
          contentLength = metadata.contentLength
        )
      }
    )

  private def span[F[_], O](stream: Stream[F, O])(f: O => Boolean): Stream[F, (Chunk[O], Stream[F, O])] = {
    def go(buffer: Chunk[O], s: Stream[F, O]): Pull[F, (Chunk[O], Stream[F, O]), Unit] =
      s.pull.uncons.flatMap {
        case Some((hd, tl)) =>
          hd.indexWhere(f) match {
            case None => go(buffer ++ hd, tl)
            case Some(idx) =>
              val pfx = hd.take(idx)
              val b2 = buffer ++ pfx
              Pull.output1((b2, tl.cons(hd.drop(idx + 1))))
          }
        case None =>
          if (buffer.nonEmpty) Pull.output1((buffer, Stream.empty))
          else Pull.done
      }

    go(Chunk.empty, stream).stream
  }

  def apply(routes: HttpRoutes[IO]): HttpRoutes[IO] = HttpRoutes {
    case request@GET -> _ =>
      val path = request.uri.path.toAbsolute.renderString
      val pathKey = cachePath.resolve(ByteVector.encodeUtf8(path).toTry.get.sha256.toBase64UrlNoPad)
      val fs2Path = fs2.io.file.Path.fromNioPath(pathKey)

      OptionT.liftF(Files[IO].exists(fs2Path))
        .filter(cached => cached) // The requested resource was cached
        .flatMap { _ =>
          StaticFile.fromFile(pathKey.toFile, Some(request)).semiflatMap { response =>
            for {
              queue <- Queue.bounded[IO, Option[Chunk[Byte]]](1)
              _ <- span(response.body)(_ == '\n').flatMap {
                case (head, tail) => Stream.chunk(head) ++ tail
              }.enqueueNoneTerminatedChunks(queue).compile.drain.start
              metadataChunkOption <- queue.take
              newResponse = metadataChunkOption match {
                case None =>
                  response.withBodyStream(Stream.empty)

                case Some(metadataChunk) =>
                  val metadata = metadataChunk.toByteVector.decodeUtf8.flatMap(decode[CacheObjectMetadata](_)).toTry.get
                  val dataStream = Stream.fromQueueNoneTerminatedChunk(queue)
                  val metadataSize = metadataChunk.size + 1
                  response
                    .withBodyStream(dataStream)
                    .putHeaders(response.contentLength.map[Header.ToRaw](contentLength =>
                      `Content-Length`(contentLength - metadataSize)).toSeq: _*
                    )
                    .withContentTypeOption(metadata.contentType)
              }
            } yield
              newResponse
          }
        }.orElse {
        // The requested resource was not cached
        OptionT.liftF(modeRef.get)
          .filter(mode => mode.record) // Record mode is active
          .flatMap(_ =>
            // The requested resource should be fetched and cached
            routes(request)
              .map { response =>
                response.withBodyStream(
                  response.body.extract { stream =>
                    val metadata = CacheObjectMetadata(
                      uri = path.some,
                      contentType = response.contentType,
                      contentLength = response.contentLength
                    )

                    for {
                      _ <- Files[IO].writeAll(fs2Path)(
                        Stream(metadata.asJson.noSpaces + "\n").through(fs2.text.utf8.encode) ++
                          stream
                      ).compile.drain
                    } yield ()
                  }.flatMap(e => e._1 ++ Stream.exec(e._2))
                )
              }
          )
      }

    case _ =>
      OptionT.none
  }
}

object FsCacheMiddleware {
  def apply(
             cachePath: Path,
             modeRef: Ref[IO, Mode]
           ): FsCacheMiddleware =
    new FsCacheMiddleware(cachePath, modeRef)

  case class CacheObjectMetadata(
                                  uri: Option[String],
                                  contentType: Option[`Content-Type`],
                                  contentLength: Option[Long]
                                )

  object CacheObjectMetadata {
    implicit val contentTypeCodec: Codec[`Content-Type`] = Codec.from(
      Decoder[String].emapTry(Header[`Content-Type`].parse(_).toTry),
      Encoder[String].contramap(Header[`Content-Type`].value)
    )

    implicit val codec: Codec[CacheObjectMetadata] = deriveCodec
  }
}
