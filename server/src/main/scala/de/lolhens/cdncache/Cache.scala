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
import fs2.io.file.Files
import fs2.{Chunk, Stream}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.{Header, HttpRoutes, StaticFile, Uri}
import scodec.bits.ByteVector

import java.net.{ProxySelector, http}
import java.nio.file.Path

class Cache(
             client: Client[IO],
             cdnUri: Uri,
             cachePath: Path,
             modeRef: Ref[IO, Mode]
           ) {
  def apply(): HttpRoutes[IO] = {
    import org.http4s.dsl.io._
    HttpRoutes {
      case request@GET -> uriPath =>
        val path = cachePath.resolve(ByteVector.encodeUtf8(uriPath.toAbsolute.renderString).toTry.get.toBase64UrlNoPad)
        println(path)
        OptionT.liftF(Files[IO].exists(path)).flatMap { cached =>
          if (cached) {
            println("cached")
            StaticFile.fromFile(path.toFile, Some(request)).semiflatMap { response => // wrong content length
              for {
                queue <- Queue.bounded[IO, Option[Chunk[Byte]]](1)
                _ <- response.body.split(_ == '\n').enqueueNoneTerminated(queue).compile.drain.start
                metadataChunk <- queue.take.map(_.getOrElse(Chunk.empty))
                metadata = io.circe.parser.parse(metadataChunk.toByteVector.decodeUtf8.toTry.get).flatMap(_.as[CacheObjectMetadata]).toTry.get
                dataStream = Stream.fromQueueNoneTerminated(queue).intersperse(Chunk[Byte]('\n')).flatMap(Stream.chunk)
              } yield
                response.withBodyStream(dataStream).withContentTypeOption(metadata.contentType)
            }
            //Ok(Files[IO].readAll(Fs2Path.fromNioPath(path)))
          } else {
            OptionT.liftF(
              client.toHttpApp(request.withDestination(
                request.uri.withSchemeAndAuthority(cdnUri)
              ))
                .map(BrotliMiddleware.decompress(_))
                .map(response => response.withBodyStream(
                  response.body.extract { stream =>
                    val metadata = CacheObjectMetadata(contentType = response.contentType)
                    for {
                      _ <- Files[IO].createDirectories(path.getParent)
                      _ <- Files[IO].writeAll(path)(
                        Stream(metadata.asJson.noSpaces + "\n").through(fs2.text.utf8.encode) ++
                          stream
                      ).compile.drain
                    } yield ()
                  }.flatMap(e => e._1 ++ Stream.exec(e._2))
                ))
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

  case class CacheObjectMetadata(contentType: Option[`Content-Type`])

  object CacheObjectMetadata {
    implicit val contentTypeCodec: Codec[`Content-Type`] = Codec.from(
      Decoder[String].emapTry(Header[`Content-Type`].parse(_).toTry),
      Encoder[String].contramap(Header[`Content-Type`].value)
    )

    implicit val codec: Codec[CacheObjectMetadata] = deriveCodec
  }
}
