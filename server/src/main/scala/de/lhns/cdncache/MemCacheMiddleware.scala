package de.lhns.cdncache

import cats.data.OptionT
import cats.effect.{Concurrent, IO}
import cats.syntax.applicative._
import cats.syntax.functor._
import fs2.{Chunk, Stream}
import org.http4s._
import org.log4s.getLogger

import java.util.concurrent.ConcurrentHashMap

class MemCacheMiddleware[F[_] : Concurrent] private() {
  private[this] val logger = getLogger
  private val cacheMap = new ConcurrentHashMap[Uri.Path, Response[F]]()

  def apply(routes: HttpRoutes[F]): HttpRoutes[F] = HttpRoutes { request =>
    if (request.method == Method.GET) {
      val uriPath = request.pathInfo

      OptionT.fromOption[F](Option(cacheMap.get(uriPath)))
        .flatTransform[Response[F]] {
          case Some(r) =>
            logger.debug(s"Cache hit: $r")

            OptionT.liftF(r.pure[F]).value

          case _ =>
            logger.debug(s"Cache miss: $request")

            routes(request).flatMap { response =>
              if (response.status == Status.Ok) {
                OptionT.liftF(
                  response
                    .as[Chunk[Byte]]
                    .map { chunk =>
                      val newResponse: Response[F] = response.copy(body = Stream.chunk(chunk).covary[F])
                      cacheMap.put(uriPath, newResponse)
                      newResponse
                    }
                )
              } else {
                OptionT.some[F](response)
              }
            }.value
        }
    } else {
      routes(request)
    }
  }
}

object MemCacheMiddleware {
  def apply[F[_] : Concurrent]: IO[MemCacheMiddleware[F]] =
    IO(new MemCacheMiddleware[F]())
}
