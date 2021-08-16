package de.lolhens.cdncache

import cats.effect._
import cats.syntax.semigroupk._
import io.circe.Json
import io.circe.syntax._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.scalatags._
import org.http4s.server.Router
import org.http4s.server.staticcontent.WebjarService.WebjarAsset
import org.http4s.server.staticcontent.{ResourceServiceBuilder, WebjarServiceBuilder}
import org.http4s.{HttpRoutes, Uri}

import java.nio.file.{Path, Paths}

object Server extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    applicationResource(
      Uri.unsafeFromString(args(0)),
      Paths.get(args(1))
    ).use(_ => IO.never)

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
        .withHttpApp(uiService(cache).orNotFound)
        .resource
    } yield ()

  def webjarUri(asset: WebjarAsset) =
    s"assets/${asset.library}/${asset.version}/${asset.asset}"

  def uiService(cache: Cache): HttpRoutes[IO] = {
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
