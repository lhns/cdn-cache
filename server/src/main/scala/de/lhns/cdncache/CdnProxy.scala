package de.lhns.cdncache

import cats.effect.kernel.Async
import cats.syntax.functor._
import de.lolhens.http4s.brotli.BrotliMiddleware
import de.lolhens.http4s.proxy.Http4sProxy._
import org.http4s.Uri.Path
import org.http4s.client.Client
import org.http4s.{HttpRoutes, HttpVersion, Uri}

class CdnProxy[F[_] : Async](
                              client: Client[F],
                              cdnUri: Uri,
                            ) {
  val toRoutes: HttpRoutes[F] = {
    val httpApp = client.toHttpApp

    HttpRoutes.of { request =>
      val newRequest = request
        .withHttpVersion(HttpVersion.`HTTP/1.1`)
        .withDestination(
          request.uri
            .withSchemeAndAuthority(cdnUri)
            .withPath(Path(
              segments = cdnUri.path.segments ++ request.pathInfo.segments,
              absolute = true,
              endsWithSlash = request.pathInfo.endsWithSlash
            ))
        )

      httpApp(newRequest)
        .map(BrotliMiddleware.decompress(_))
    }
  }
}

object CdnProxy {
  def apply[F[_] : Async](
                           client: Client[F],
                           cdnUri: Uri,
                         ): CdnProxy[F] =
    new CdnProxy(client, cdnUri)
}
