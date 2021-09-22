package de.lolhens.cdncache

import cats.effect.IO
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.ScalaComponent.BackendScope
import japgolly.scalajs.react.internal.CoreGeneral.ReactEventFromInput
import japgolly.scalajs.react.util.EffectCatsEffect._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.html.TableCell

import scala.concurrent.duration._

object MainComponent {
  case class Props()

  case class State(
                    mode: Option[Mode],
                    entries: Option[Seq[CacheEntry]],
                    filter: String
                  )

  object State {
    val empty: State = State(None, None, "")
  }

  class Backend($: BackendScope[Props, State]) {
    private def fetchState: IO[Unit] =
      for {
        modeFiber <- Backend.mode.start
        entriesFiber <- Backend.cacheEntries.start
        mode <- modeFiber.joinWithNever
        _ <- $.modStateAsync(_.copy(mode = Some(mode)))
        entries <- entriesFiber.joinWithNever
        _ <- $.modStateAsync(_.copy(entries = Some(entries)))
      } yield ()

    def componentDidMount: IO[Unit] = {
      lazy val tick: IO[Unit] =
        fetchState >>
          tick.delayBy(8.seconds)

      tick
    }

    def render: VdomElement = {
      val state = $.state.unsafeRunSync()

      <.div(
        ^.cls := "container my-4 d-flex flex-column",
        Backend.appConfig.cdns.sortBy(_.routeUri).toVdomArray(cdn =>
          <.div(
            ^.key := cdn.routeUri,
            ^.cls := "d-flex flex-row",
            <.h3(
              ^.cls := "align-self-center",
              cdn.routeUri
            ),
            <.div(^.cls := "flex-fill"),
            <.div(
              ^.cls := "d-flex flex-column align-items-end mb-2",
              <.h4(^.cls := "mb-0", cdn.uri),
              <.div(s"Memory cache ${if (cdn.enableMemCache) "enabled" else "disabled"}")
            )
          )
        ),
        <.div(^.cls := "d-flex flex-row mt-4 mb-4",
          <.div(^.cls := "flex-fill"),
          state.mode match {
            case None => "Loading..."
            case Some(mode) =>
              <.button(^.cls := s"btn btn-${if (mode.record) "danger" else "primary"}",
                if (mode.record) <.div(
                  "Stop Recording",
                  <.i(^.cls := "bi bi-stop-fill ms-2")
                ) else <.div(
                  "Start Recording",
                  <.i(^.cls := "bi bi-record-fill ms-2")
                ),
                ^.onClick --> {
                  val newMode = mode.copy(record = !mode.record)
                  $.modStateAsync(_.copy(mode = Some(newMode))) >>
                    Backend.setMode(newMode)
                })
          }
        ),
        {
          val headers = Seq[VdomTagOf[TableCell]](
            <.th(^.scope := "col", "URI"),
            <.th(^.scope := "col", "Content-Type"),
            <.th(^.scope := "col", "Content-Encoding"),
            <.th(^.scope := "col", "Content-Length"),
            <.th(^.scope := "col", ^.width := "0"),
          )

          <.table(^.cls := "table",
            <.thead(
              <.tr(headers: _*)
            ),
            <.tbody(
              <.tr(
                <.td(
                  ^.colSpan := headers.size,
                  <.input(
                    ^.id := "search",
                    ^.cls := "align-self-center form-control",
                    ^.tpe := "text",
                    ^.placeholder := "Search...",
                    ^.onChange ==> { e: ReactEventFromInput =>
                      val value = e.target.value
                      $.modState(_.copy(filter = value))
                    }
                  )
                )
              ), {
                val filterLowerCase = state.filter.toLowerCase
                state.entries.getOrElse(Seq.empty).filter(_.uri.toLowerCase.contains(filterLowerCase))
              }.toVdomArray { entry =>
                <.tr(
                  ^.key := entry.uri,
                  <.th(^.scope := "row", entry.uri),
                  <.td(entry.contentType),
                  <.td(entry.contentEncoding),
                  <.td(entry.contentLength.map(_ + " B")),
                  <.td(
                    <.button(^.cls := "btn btn-danger",
                      <.i(^.cls := "bi bi-trash-fill"),
                      ^.onClick --> {
                        Backend.deleteEntry(entry.uri) >>
                          fetchState
                      }
                    )
                  ),
                )
              }
            )
          )
        }
      )
    }
  }

  val Component =
    ScalaComponent.builder[Props]
      .initialState(State.empty)
      .backend(new Backend(_))
      .render(_.backend.render)
      .componentDidMount(_.backend.componentDidMount)
      .build
}
