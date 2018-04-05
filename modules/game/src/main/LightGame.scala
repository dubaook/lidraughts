package lidraughts.game

import draughts.{ Color, Status, Mode }
import org.joda.time.DateTime

case class LightGame(
    id: Game.ID,
    whitePlayer: Player,
    blackPlayer: Player,
    status: Status
) {
  def playable = status < Status.Aborted
  def player(color: Color): Player = color.fold(whitePlayer, blackPlayer)
  def players = List(whitePlayer, blackPlayer)
  def playerByUserId(userId: String): Option[Player] = players.find(_.userId contains userId)
  def winner = players find (_.wins)
  def wonBy(c: Color): Option[Boolean] = winner.map(_.color == c)
}

object LightGame {

  import Game.{ BSONFields => F }

  def projection = lidraughts.db.dsl.$doc(
    F.whitePlayer -> true,
    F.blackPlayer -> true,
    F.winnerColor -> true,
    F.status -> true
  )
}
