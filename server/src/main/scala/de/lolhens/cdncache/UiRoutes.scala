package de.lolhens.cdncache

import cats.effect.IO
import cats.syntax.semigroupk._
import io.circe.Json
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.scalatags._
import org.http4s.server.Router
import org.http4s.server.staticcontent.WebjarService.WebjarAsset
import org.http4s.server.staticcontent.{ResourceServiceBuilder, WebjarServiceBuilder}

class UiRoutes(cache: Cache) {
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
        case request@GET -> Root =>
          Ok(MainPage())
      }
    )
  }
}

object UiRoutes {
  def webjarUri(asset: WebjarAsset) =
    s"assets/${asset.library}/${asset.version}/${asset.asset}"
}
