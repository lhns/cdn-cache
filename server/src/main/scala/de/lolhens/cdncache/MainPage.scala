package de.lolhens.cdncache

import buildinfo.BuildInfo
import io.circe.Json
import org.http4s.server.staticcontent.WebjarService.WebjarAsset
import scalatags.Text.all._

import scala.language.implicitConversions

object MainPage {
  private lazy val frontendWebjarAsset: WebjarAsset = WebjarAsset(
    BuildInfo.frontendName,
    BuildInfo.frontendVersion,
    BuildInfo.frontendAsset
  )

  private val integrity = attr("integrity")
  private val crossorigin = attr("crossorigin")
  private val async = attr("async").empty

  private implicit def string2Json(string: String): Json = Json.fromString(string)

  def importmap(json: Json): Tag = script(
    tpe := "importmap",
    raw(json.spaces2)
  )

  def apply(title: String,
            metaAttributes: Map[String, String] = Map.empty): Tag = html(
    head(
      meta(charset := "utf-8"),
      tag("title")(title),
      meta(name := "viewport", content := "width=device-width, initial-scale=1"),
    )(
      metaAttributes.map {
        case (key, value) => meta(name := key, content := value)
      }.toSeq: _*
    ),
    body(
      link(
        href := "https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css",
        rel := "stylesheet",
        integrity := "sha384-EVSTQN3/azprG1Anm3QDgpJLIm9Nao0Yz1ztcQTwFspd3yD65VohhpuuCOmLASjC",
        crossorigin := "anonymous",
      ),
      link(
        rel := "stylesheet",
        href := "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.5.0/font/bootstrap-icons.css"
      ),
      script(
        src := "https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/js/bootstrap.bundle.min.js",
        integrity := "sha384-MrcW6ZMFYlzcLA8Nl+NtUVF0sA7MsXsP1UyJoMp4YLEuNSfAP+JcXn/tWtIaxVXM",
        crossorigin := "anonymous",
      ),
      link(
        rel := "stylesheet",
        href := "/assets/main.css"
      ),
      // ES Module Shims: Import maps polyfill for modules browsers without import maps support (all except Chrome 89+)
      script(
        async,
        src := "https://ga.jspm.io/npm:es-module-shims@0.10.1/dist/es-module-shims.min.js"
      ),
      /*
      JSPM Generator Import Map
      https://generator.jspm.io/
      */
      importmap(
        Json.obj(
          "imports" -> Json.obj(
            "react" -> "https://ga.jspm.io/npm:react@17.0.2/dev.index.js",
            "react-dom" -> "https://ga.jspm.io/npm:react-dom@17.0.2/dev.index.js",
          ),
          "scopes" -> Json.obj(
            "https://ga.jspm.io/" -> Json.obj(
              "object-assign" -> "https://ga.jspm.io/npm:object-assign@4.1.1/index.js",
              "scheduler" -> "https://ga.jspm.io/npm:scheduler@0.20.2/dev.index.js",
              "scheduler/tracing" -> "https://ga.jspm.io/npm:scheduler@0.20.2/dev.tracing.js",
            )
          )
        )
      ),
      div(id := "root"),
      script(tpe := "module", src := UiRoutes.webjarUri(frontendWebjarAsset))
    )
  )
}
