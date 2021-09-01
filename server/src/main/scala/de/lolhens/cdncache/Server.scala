package de.lolhens.cdncache

import cats.data.OptionT
import cats.effect._
import cats.syntax.semigroupk._
import com.github.markusbernhardt.proxy.ProxySearch
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Uri}
import org.log4s.getLogger

import java.net.ProxySelector
import java.nio.file.{Path, Paths}
import scala.util.chaining._

object Server extends IOApp {
  private[this] val logger = getLogger

  override def run(args: List[String]): IO[ExitCode] = {
    synchronized {
      ProxySelector.setDefault(
        Option(new ProxySearch().tap { s =>
          s.addStrategy(ProxySearch.Strategy.JAVA)
          s.addStrategy(ProxySearch.Strategy.ENV_VAR)
        }.getProxySelector)
          .getOrElse(ProxySelector.getDefault)
      )
    }

    val cdnUri = Uri.unsafeFromString(
      Option(System.getenv("CDN_URI"))
        .getOrElse(throw new IllegalArgumentException("Missing variable: CDN_URI"))
    )

    val cachePath = Paths.get(
      Option(System.getenv("CACHE_PATH"))
        .getOrElse(throw new IllegalArgumentException("Missing variable: CACHE_PATH"))
    )

    val enableMemCache =
      Option(System.getenv("ENABLE_MEM_CACHE"))
        .flatMap(_.toBooleanOption)
        .getOrElse(false)

    logger.info(s"CDN_URI: $cdnUri")
    logger.info(s"CACHE_PATH: $cachePath")
    logger.info(s"ENABLE_MEM_CACHE: $enableMemCache")

    applicationResource(
      cdnUri,
      cachePath,
      enableMemCache,
    ).use(_ => IO.never)
  }

  private def applicationResource(
                                   cdnUri: Uri,
                                   cachePath: Path,
                                   enableMemCache: Boolean,
                                 ): Resource[IO, Unit] =
    for {
      ec <- Resource.eval(IO.executionContext)
      modeRef <- Resource.eval(Ref[IO].of(Mode(record = false)))
      cdnProxy <- CdnProxy(cdnUri)
      cdnCacheMiddleware = FsCacheMiddleware(cachePath, modeRef)
      memCacheMiddlewareOption <- Resource.eval(
        OptionT.whenF(enableMemCache)(
          MemCacheMiddleware[IO]
        ).value
      )

      healthRoutes = HttpRoutes.of[IO] {
        case GET -> Root / "health" => Ok()
      }

      _ <- BlazeServerBuilder[IO](ec)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(
          (healthRoutes <+>
            cdnCacheMiddleware(cdnProxy.toRoutes)
              .pipe(routes =>
                memCacheMiddlewareOption.fold(routes)(_ (routes))
              ))
            .orNotFound
        )
        .resource

      _ <- BlazeServerBuilder[IO](ec)
        .bindHttp(8081, "0.0.0.0")
        .withHttpApp(new UiRoutes(
          cdnCacheMiddleware,
          appConfig = AppConfig(
            cdnUri = cdnUri.renderString,
            enableMemCache = enableMemCache,
          )
        ).toRoutes.orNotFound)
        .resource
    } yield ()
}
