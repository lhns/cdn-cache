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
                    mode: Option[Mode]
                  )

  object State {
    val empty: State = State(None)
  }

  class Backend($: BackendScope[Props, State]) {
    def componentDidMount: IO[Unit] =
      Backend.mode.flatMap(mode =>
        $.modStateAsync(state =>
          state.copy(mode = Some(mode))
        )
      )

    def render: VdomElement = {
      val state = $.state.unsafeRunSync()

      <.div(
        ^.cls := "container my-4 d-flex flex-column",
        <.h1(
          ^.id := "settings",
          ^.position := "relative",
          <.i(
            ^.cls := "bi bi-gear bi-select",
            ^.position := "absolute",
            ^.right := "0",
            ^.top := "0.8rem",
            ^.onClick --> IO {
              println("Settings")
            }
          )
        ),
        <.input(
          ^.id := "search",
          ^.cls := "align-self-center form-control form-control-lg mb-4",
          ^.tpe := "text",
          ^.placeholder := "Search...",
          ^.onChange ==> { e: ReactEventFromInput =>
            val value = e.target.value
            $.modState(e => e)
          }
        ),
        <.div(^.cls := "d-flex flex-row",
          state.mode match {
            case None => "Loading..."
            case Some(mode) => mode.toString
          },
          <.button("Toggle Mode", ^.onClick --> IO.defer {
            state.mode match {
              case None => IO.unit
              case Some(mode) =>
                val newMode = mode.copy(record = !mode.record)
                $.modStateAsync(_.copy(mode = Some(newMode))) >>
                  Backend.setMode(newMode)
            }
          })
        ),
        Suspense(Backend.cacheEntries) {
          case None => "Loading entries..."
          case Some(entries) => entries.toVdomArray { entry =>
            <.div(entry.toString)
          }
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
