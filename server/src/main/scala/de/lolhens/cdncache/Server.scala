package de.lolhens.cdncache

import cats.effect._
import cats.syntax.semigroupk._
import org.http4s.blaze.server.BlazeServerBuilder
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
      modeRef <- Resource.eval(Ref[IO].of(Mode(record = true)))
      cache <- Cache(cacheUri, cachePath, modeRef)
      server1Fiber <- BlazeServerBuilder[IO](ec)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(cache().orNotFound)
        .resource
        .start
      server2Fiber <- BlazeServerBuilder[IO](ec)
        .bindHttp(8081, "0.0.0.0")
        .withHttpApp(uiService(modeRef).orNotFound)
        .resource
        .start
      _ <- server1Fiber.joinWithNever
      _ <- server2Fiber.joinWithNever
    } yield ()

  def webjarUri(asset: WebjarAsset) =
    s"assets/${asset.library}/${asset.version}/${asset.asset}"

  def uiService(modeRef: Ref[IO, Mode]): HttpRoutes[IO] = {
    import org.http4s.dsl.io._
    Router(
      "/assets" -> {
        (WebjarServiceBuilder[IO].toRoutes: HttpRoutes[IO]) <+>
          ResourceServiceBuilder[IO]("/assets").toRoutes
      },

      "/api" -> HttpRoutes.of {
        case GET -> Root / "test" =>
          Ok("Test")
      },

      "/" -> HttpRoutes.of {
        case request@GET -> Root =>
          Ok(MainPage())
      }
    )
  }
}
