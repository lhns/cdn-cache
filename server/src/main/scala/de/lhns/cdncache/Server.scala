package de.lhns.cdncache

import cats.effect._
import cats.syntax.semigroupk._
import com.comcast.ip4s._
import com.github.markusbernhardt.proxy.ProxySearch
import de.lhns.cdncache.AppConfig.CdnConfig
import io.circe.syntax._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.server.middleware.{CORS, ErrorAction, GZip}
import org.http4s.server.{Router, Server}
import org.http4s.{HttpApp, HttpRoutes}
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


    val cdnSettings: Map[String, CdnSettings] = Option(System.getenv("CDN_SETTINGS"))
      .toRight(new IllegalArgumentException("Missing variable: CDN_SETTINGS"))
      .flatMap(io.circe.parser.decode[Map[String, CdnSettings]](_))
      .toTry.get

    val cachePath = Option(System.getenv("CACHE_PATH"))
      .toRight(new IllegalArgumentException("Missing variable: CACHE_PATH"))
      .map(Paths.get(_))
      .toTry.get

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
      modeRef <- Resource.eval(Ref[IO].of(Mode.default))
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
                .pipe(CORS.policy(_))

            routeUri.renderString -> routes
        }.toSeq: _*
      ).pipe(GZip(_))

      healthRoutes = HttpRoutes.of[IO] {
        case GET -> Root / "health" => Ok()
      }

      _ <- serverResource(
        host"0.0.0.0",
        port"8080",
        (healthRoutes <+> proxyRoutes).orNotFound
      )

      _ <- serverResource(
        host"0.0.0.0",
        port"8081",
        new UiRoutes(
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
        ).toRoutes.orNotFound
      )
    } yield ()

  def serverResource(host: Host, port: Port, http: HttpApp[IO]): Resource[IO, Server] =
    EmberServerBuilder.default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(
        ErrorAction.log(
          http = http,
          messageFailureLogAction = (t, msg) => IO(logger.debug(t)(msg)),
          serviceErrorLogAction = (t, msg) => IO(logger.error(t)(msg))
        ))
      .build
}
