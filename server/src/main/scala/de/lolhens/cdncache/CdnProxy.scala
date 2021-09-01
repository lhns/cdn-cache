package de.lolhens.cdncache

import cats.effect.IO
import cats.effect.kernel.Resource
import de.lolhens.http4s.brotli.BrotliMiddleware
import de.lolhens.http4s.proxy.Http4sProxy._
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.{HttpRoutes, Uri}

class CdnProxy(
                client: Client[IO],
                cdnUri: Uri,
              ) {
  val toRoutes: HttpRoutes[IO] = {
    val httpApp = client.toHttpApp

    HttpRoutes.of { request =>
      val newRequest = request.withDestination(
        request.uri.withSchemeAndAuthority(cdnUri)
      )

      httpApp(newRequest)
        .map(BrotliMiddleware.decompress(_))
    }
  }
}

object CdnProxy {
  def apply(cdnUri: Uri): Resource[IO, CdnProxy] =
    for {
      client <- JdkHttpClient.simple[IO]
    } yield
      new CdnProxy(client, cdnUri)
}
