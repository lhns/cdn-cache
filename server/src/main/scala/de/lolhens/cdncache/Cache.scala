package de.lolhens.cdncache

import cats.data.OptionT
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.{IO, Ref}
import com.github.markusbernhardt.proxy.ProxySearch
import de.lolhens.cdncache.Cache.CacheObjectMetadata
import de.lolhens.fs2.utils.Fs2Utils._
import de.lolhens.http4s.brotli.BrotliMiddleware
import de.lolhens.http4s.proxy.Http4sProxy._
import fs2.io.file.{Files, Path => Fs2Path}
import fs2.{Chunk, Pull, Stream}
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.headers.{`Content-Length`, `Content-Type`}
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.{Header, HttpRoutes, StaticFile, Uri}
import scodec.bits.ByteVector

import java.net.{ProxySelector, http}
import java.nio.file.Path

class Cache(
             client: Client[IO],
             cdnUri: Uri,
             cachePath: Path,
             val modeRef: Ref[IO, Mode]
           ) {
  def listEntries: Stream[IO, CacheEntry] =
    Files[IO].list(Fs2Path.fromNioPath(cachePath)).parEvalMap(8)(path =>
      Files[IO].readAll(path).takeWhile(_ != '\n').through(fs2.text.utf8.decode).compile.string.map { metadataString =>
        val metadata = decode[CacheObjectMetadata](metadataString).toTry.get
        CacheEntry(
          uri = ByteVector.fromValidBase64(path.fileName.toString).decodeUtf8.toTry.get,
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

  def toRoutes: HttpRoutes[IO] = {
    import org.http4s.dsl.io._
    HttpRoutes {
      case request@GET -> uriPath =>
        val path = cachePath.resolve(ByteVector.encodeUtf8(uriPath.toAbsolute.renderString).toTry.get.toBase64UrlNoPad)
        OptionT.liftF(Files[IO].exists(path)).flatMap { cached =>
          if (cached) {
            StaticFile.fromFile(path.toFile, Some(request)).semiflatMap { response =>
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
          } else {
            OptionT.liftF(modeRef.get).flatMap(mode =>
              if (mode.record) {
                OptionT.liftF(
                  client.toHttpApp(request.withDestination(
                    request.uri.withSchemeAndAuthority(cdnUri)
                  ))
                    .map(BrotliMiddleware.decompress(_))
                    .map(response => response.withBodyStream(
                      response.body.extract { stream =>
                        val metadata = CacheObjectMetadata(
                          contentType = response.contentType,
                          contentLength = response.contentLength
                        )

                        for {
                          _ <- Files[IO].writeAll(path)(
                            Stream(metadata.asJson.noSpaces + "\n").through(fs2.text.utf8.encode) ++
                              stream
                          ).compile.drain
                        } yield ()
                      }.flatMap(e => e._1 ++ Stream.exec(e._2))
                    ))
                )
              } else {
                OptionT.none
              }
            )
          }
        }
    }
  }
}

object Cache {
  private lazy val proxySelector =
    Option(ProxySearch.getDefaultProxySearch.getProxySelector).getOrElse(ProxySelector.getDefault)

  def apply(
             cdnUri: Uri,
             cachePath: Path,
             modeRef: Ref[IO, Mode]
           ): Resource[IO, Cache] =
    for {
      client <- JdkHttpClient[IO](
        http.HttpClient.newBuilder()
          .sslParameters {
            val params = javax.net.ssl.SSLContext.getDefault.getDefaultSSLParameters
            // workaround for https://github.com/http4s/http4s-jdk-http-client/issues/200
            if (Runtime.version().feature() == 11)
              params.setProtocols(params.getProtocols.filter(_ != "TLSv1.3"))
            params
          }
          .proxy(proxySelector)
          .build()
      )
    } yield
      new Cache(client, cdnUri, cachePath, modeRef)

  case class CacheObjectMetadata(
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
