package de.lolhens.cdncache

import cats.effect._
import com.github.markusbernhardt.proxy.ProxySearch
import org.http4s.Uri
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._

import java.net.ProxySelector
import java.nio.file.{Path, Paths}
import scala.util.chaining._

object Server extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    ProxySelector.setDefault(
      Option(new ProxySearch().tap { s =>
        s.addStrategy(ProxySearch.Strategy.JAVA)
        s.addStrategy(ProxySearch.Strategy.ENV_VAR)
      }.getProxySelector)
        .getOrElse(ProxySelector.getDefault)
    )

    val cdnUri = Uri.unsafeFromString(
      Option(System.getenv("CDN_URI"))
        .getOrElse(throw new IllegalArgumentException("Missing variable: CDN_URI"))
    )

    val cachePath = Paths.get(
      Option(System.getenv("CACHE_PATH"))
        .getOrElse(throw new IllegalArgumentException("Missing variable: CACHE_PATH"))
    )

    applicationResource(
      cdnUri,
      cachePath
    ).use(_ => IO.never)
  }

  private def applicationResource(cacheUri: Uri, cachePath: Path): Resource[IO, Unit] =
    for {
      ec <- Resource.eval(IO.executionContext)
      modeRef <- Resource.eval(Ref[IO].of(Mode(record = false)))
      cache <- Cache(cacheUri, cachePath, modeRef)

      _ <- BlazeServerBuilder[IO](ec)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(cache.toRoutes.orNotFound)
        .resource

      _ <- BlazeServerBuilder[IO](ec)
        .bindHttp(8081, "0.0.0.0")
        .withHttpApp(new UiRoutes(cache).toRoutes.orNotFound)
        .resource
    } yield ()
}
