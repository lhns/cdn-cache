package de.lolhens.cdncache

import cats.data.Kleisli
import cats.effect.IO
import cats.syntax.option._
import de.lolhens.http4s.spa._
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.server.Router
import org.http4s.server.staticcontent.ResourceServiceBuilder
import org.http4s.{HttpRoutes, Uri}

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

  val toRoutes: HttpRoutes[IO] = {
    import org.http4s.dsl.io._
    Router(
      "/" -> appController.toRoutes,

      "/api" -> HttpRoutes.of {
        case GET -> Root / "mode" =>
          for {
            mode <- cache.modeRef.get
            response <- Ok(mode.asJson)
          } yield response

        case request@POST -> Root / "mode" =>
          for {
            mode <- request.as[Json].map(_.as[Mode].toTry.get)
            _ <- cache.modeRef.set(mode)
            response <- Ok("")
          } yield response

        case GET -> Root / "cache" / "entries" =>
          for {
            entries <- cache.listEntries.compile.toList
            response <- Ok(entries.asJson)
          } yield response
      },
    )
  }
}