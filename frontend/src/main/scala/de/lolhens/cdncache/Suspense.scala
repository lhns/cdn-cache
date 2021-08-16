package de.lolhens.cdncache

import cats.data.{EitherT, OptionT}
import cats.effect.{IO, SyncIO}
import cats.syntax.option._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.util.EffectCatsEffect._
import japgolly.scalajs.react.vdom.html_<^.VdomNode

// The builtin react Suspense has some restrictions
object Suspense {

  case class Props(fallback: () => VdomNode,
                   asyncBody: IO[VdomNode])

  case class State(body: VdomNode,
                   asyncBody: Option[IO[VdomNode]])

  object State {
    def fromProps(props: Props): State = {
      props.asyncBody.syncStep.unsafeRunSync() match {
        case Right(sync) =>
          State(sync, none)

        case Left(async) =>
          State(props.fallback(), async.some)
      }
    }
  }

  class Backend($: BackendScope[Props, State]) {
    def shouldUpdate(nextProps: Props, nextState: State): SyncIO[Boolean] = {
      (for {
        props <- EitherT.right[Boolean]($.props)
        _ <- {
          (for {
            _ <- EitherT.cond[SyncIO](nextProps != props, (), true)
            _ <- EitherT.right($.modState(_ => State.fromProps(nextProps)))
            _ <- EitherT.leftT[SyncIO, Unit](false)
          } yield ())
            .recover {
              case true => ()
            }
        }
        state <- EitherT.right[Boolean]($.state)
      } yield
        nextState != state)
        .merge
    }

    private def waitForAsyncBody: IO[Unit] =
      (for {
        state <- OptionT.liftF($.state.to[IO])
        async <- OptionT.fromOption[IO](state.asyncBody)
        body <- OptionT.liftF(async)
        _ <- OptionT.liftF($.modStateAsync(_.copy(body = body, asyncBody = none)))
      } yield ())
        .value
        .void

    def start: IO[Unit] = waitForAsyncBody

    def update: IO[Unit] = waitForAsyncBody

    def render(state: State): VdomNode = state.body
  }

  private val Component =
    ScalaComponent.builder[Props]
      .initialStateFromProps(State.fromProps)
      .renderBackend[Backend]
      .shouldComponentUpdate(e => e.backend.shouldUpdate(e.nextProps, e.nextState))
      .componentDidMount(_.backend.start)
      .componentDidUpdate(_.backend.update)
      .build

  def apply[A](asyncValue: IO[A])
              (toVdomNode: Option[A] => VdomNode): Unmounted[Props, State, Backend] = {
    Component(Props(() => toVdomNode(none), asyncValue.map(e => toVdomNode(e.some))))
  }
}
