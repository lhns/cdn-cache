package de.lolhens.cdncache

import cats.effect._
import cats.syntax.semigroupk._
import com.github.markusbernhardt.proxy.ProxySearch
import de.lolhens.cdncache.AppConfig.CdnConfig
import io.circe.parser.{decode => decodeJson}
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.server.Router
import org.log4s.getLogger

import java.net.ProxySelector
import java.nio.file.{Files, Path, Paths}
import scala.util.chaining._

object Server extends IOApp {
  private[this] val logger = getLogger

  override def run(args: List[String]): IO[ExitCode] = {
    ProxySelector.setDefault(
      Option(new ProxySearch().tap { s =>
        s.addStrategy(ProxySearch.Strategy.JAVA)
        s.addStrategy(ProxySearch.Strategy.ENV_VAR)
      }.getProxySelector)
        .getOrElse(ProxySelector.getDefault)
    )

    val cdnSettings: Map[String, CdnSettings] = decodeJson[Map[String, CdnSettings]](
      Option(System.getenv("CDN_SETTINGS"))
        .getOrElse(throw new IllegalArgumentException("Missing variable: CDN_SETTINGS"))
    ).toTry.get

    val cachePath = Paths.get(
      Option(System.getenv("CACHE_PATH"))
        .getOrElse(throw new IllegalArgumentException("Missing variable: CACHE_PATH"))
    )

    logger.info(s"CDN_SETTINGS: ${cdnSettings.asJson.spaces2}")
    logger.info(s"CACHE_PATH: $cachePath")

    require(Files.isDirectory(cachePath), s"directory not found: $cachePath")

    applicationResource(
      cdnSettings,
      cachePath,
    ).use(_ => IO.never)
  }

  private def applicationResource(
                                   cdnSettings: Map[String, CdnSettings],
                                   cachePath: Path,
                                 ): Resource[IO, Unit] =
    for {
      modeRef <- Resource.eval(Ref[IO].of(Mode(record = false)))
      client <- JdkHttpClient.simple[IO]

      cdnCacheMiddleware = FsCacheMiddleware(cachePath, modeRef)
      memCacheMiddleware <- Resource.eval(MemCacheMiddleware[IO])

      proxyRoutes = Router[IO](
        cdnSettings.iterator.map {
          case (name, settings) =>
            val routeUri = CdnSettings.routeUri(name)
            val cdnProxy = CdnProxy(client, settings.uri)
            val routes =
              cdnProxy.toRoutes
                .pipe(cdnCacheMiddleware(_))
                .pipe(routes =>
                  if (settings.memCacheOrDefault) memCacheMiddleware(routes)
                  else routes
                )

            routeUri.renderString -> routes
        }.toSeq: _*
      )

      healthRoutes = HttpRoutes.of[IO] {
        case GET -> Root / "health" => Ok()
      }

      _ <- BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp((healthRoutes <+> proxyRoutes).orNotFound)
        .resource

      _ <- BlazeServerBuilder[IO]
        .bindHttp(8081, "0.0.0.0")
        .withHttpApp(new UiRoutes(
          cdnCacheMiddleware,
          appConfig = AppConfig(
            cdnSettings.iterator.map {
              case (name, settings) =>
                CdnConfig(
                  CdnSettings.routeUri(name).renderString,
                  settings.uri.renderString,
                  settings.memCacheOrDefault
                )
            }.toSeq
          )
        ).toRoutes.orNotFound)
        .resource
    } yield ()
}
