package de.lolhens.cdncache

import cats.effect.IO
import cats.syntax.semigroupk._
import de.lolhens.http4s.spa.{ImportMap, ResourceBundle, SinglePageApp, Stylesheet}
import io.circe.Json
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.staticcontent.WebjarService.WebjarAsset
import org.http4s.server.staticcontent.{ResourceServiceBuilder, WebjarServiceBuilder}

class UiRoutes(
                cache: FsCacheMiddleware,
                appConfig: AppConfig,
              ) {
  private val mainPage = SinglePageApp(
    webjar = uri"/assets/" -> webjars.frontend.webjarAsset,
    importMap = ImportMap.react17,
    resourceBundles = Seq(
      ResourceBundle.bootstrap5,
      ResourceBundle.bootstrapIcons1,
      ResourceBundle(stylesheets = Seq(Stylesheet(uri"/assets/main.css")))
    )
  )

  val toRoutes: HttpRoutes[IO] = {
    import org.http4s.dsl.io._
    Router(
      "/assets" -> {
        (WebjarServiceBuilder[IO].toRoutes: HttpRoutes[IO]) <+>
          ResourceServiceBuilder[IO]("/assets").toRoutes
      },

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

      "/" -> HttpRoutes.of {
        case GET -> Root =>
          IO(mainPage(
            title = "CDN Cache Config",
            metaAttributes = Map(
              "appconfig" -> appConfig.asJson.noSpaces
            )
          ))
      }
    )
  }
}

object UiRoutes {
  def webjarUri(asset: WebjarAsset) =
    s"assets/${asset.library}/${asset.version}/${asset.asset}"
}
