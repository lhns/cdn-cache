package de.lolhens.cdncache

import cats.data.Kleisli
import cats.effect.IO
import cats.syntax.option._
import de.lolhens.http4s.spa._
import de.lolhens.remoteio.Rest
import io.circe.syntax._
import org.http4s.server.Router
import org.http4s.server.middleware.GZip
import org.http4s.server.staticcontent.ResourceServiceBuilder
import org.http4s.{HttpRoutes, Uri}

import scala.util.chaining._

class UiRoutes(
                cache: FsCacheMiddleware,
                appConfig: AppConfig,
              ) {
  private val app = SinglePageApp(
    title = "CDN Cache Config",
    metaAttributes = Map(
      "appconfig" -> appConfig.asJson.noSpaces
    ),
    webjar = webjars.frontend.webjarAsset,
    dependencies = Seq(
      SpaDependencies.react17,
      SpaDependencies.bootstrap5,
      SpaDependencies.bootstrapIcons1,
      SpaDependencies.mainCss
    ),
  )

  private val appController = SinglePageAppController[IO](
    mountPoint = Uri.Root,
    controller = Kleisli.pure(app),
    resourceServiceBuilder = ResourceServiceBuilder[IO]("/assets").some
  )

  private val apiRoutes: HttpRoutes[IO] = Rest.toRoutes(
    Api.getMode.impl(_ => cache.modeRef.get),
    Api.setMode.impl(cache.modeRef.set),
    Api.deleteEntry.impl(cache.deleteEntry),
    Api.listEntries.impl(_ => cache.listEntries.compile.toList),
  )

  val toRoutes: HttpRoutes[IO] = {
    Router(
      "/" -> appController.toRoutes,
      "/api" -> apiRoutes,
    ).pipe(GZip(_))
  }
}