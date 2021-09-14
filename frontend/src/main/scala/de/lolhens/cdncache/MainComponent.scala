package de.lolhens.cdncache

import cats.effect.IO
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.ScalaComponent.BackendScope
import japgolly.scalajs.react.internal.CoreGeneral.ReactEventFromInput
import japgolly.scalajs.react.util.EffectCatsEffect._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

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
    def componentDidMount: IO[Unit] =
      for {
        modeFiber <- Backend.mode.start
        entriesFiber <- Backend.cacheEntries.start
        mode <- modeFiber.joinWithNever
        _ <- $.modStateAsync(_.copy(mode = Some(mode)))
        entries <- entriesFiber.joinWithNever
        _ <- $.modStateAsync(_.copy(entries = Some(entries)))
      } yield ()

    def render: VdomElement = {
      val state = $.state.unsafeRunSync()

      <.div(
        ^.cls := "container my-4 d-flex flex-column",
        <.h2(^.cls := "align-self-center mb-4", Backend.appConfig.cdnUri),
        <.input(
          ^.id := "search",
          ^.cls := "align-self-center form-control mb-4",
          ^.tpe := "text",
          ^.placeholder := "Search...",
          ^.onChange ==> { e: ReactEventFromInput =>
            val value = e.target.value
            $.modState(_.copy(filter = value))
          }
        ),
        <.div(^.cls := "d-flex flex-row mb-3",
          state.mode match {
            case None => "Loading..."
            case Some(mode) =>
              <.button(^.cls := s"btn btn-${if (mode.record) "danger" else "primary"}",
                if (mode.record) "Stop Recording"
                else "Start Recording",
                ^.onClick --> IO.defer {
                  val newMode = mode.copy(record = !mode.record)
                  $.modStateAsync(_.copy(mode = Some(newMode))) >>
                    Backend.setMode(newMode)
                })
          },
          <.div(^.cls := "flex-fill"),
          <.div(s"Memory cache ${if (Backend.appConfig.enableMemCache) "enabled" else "disabled"}")
        ),
        <.table(^.cls := "table",
          <.thead(
            <.tr(
              <.th(^.scope := "col", "URI"),
              <.th(^.scope := "col", "Content-Type"),
              <.th(^.scope := "col", "Content-Length"),
            )
          ),
          <.tbody(
            {
              val filterLowerCase = state.filter.toLowerCase
              state.entries.getOrElse(Seq.empty).filter(_.uri.toLowerCase.contains(filterLowerCase))
            }.toVdomArray { entry =>
              <.tr(
                <.th(^.scope := "row", entry.uri),
                <.td(entry.contentType),
                <.td(entry.contentLength.map(_ + " B")),
              )
            }
          )
        )
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
