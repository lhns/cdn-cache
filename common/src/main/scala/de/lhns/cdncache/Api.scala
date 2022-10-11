package de.lhns.cdncache

import cats.effect.IO
import de.lolhens.remoteio.{Rest, Rpc}
import org.http4s.Method.{DELETE, GET, POST}
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{EntityDecoder, EntityEncoder}

object Api {
  private implicit val unitDecoder: EntityDecoder[IO, Unit] = EntityDecoder.void
  private implicit val unitEncoder: EntityEncoder[IO, Unit] = EntityEncoder.unitEncoder

  val getMode = Rpc[IO, Unit, Mode](Rest)(GET -> path"mode")
  val setMode = Rpc[IO, Mode, Unit](Rest)(POST -> path"mode")
  val deleteEntry = Rpc[IO, String, Unit](Rest)(DELETE -> path"cache/entries")
  val listEntries = Rpc[IO, Unit, Seq[CacheEntry]](Rest)(GET -> path"cache/entries")
}
