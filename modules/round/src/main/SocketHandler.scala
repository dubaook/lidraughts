package lidraughts.round

import scala.concurrent.Promise
import scala.util.Try

import akka.actor._
import akka.pattern.ask
import draughts.format.Uci
import draughts.{ Centis, MoveMetrics, Color }
import play.api.libs.json.{ JsObject, Json }

import actorApi._, round._
import lidraughts.chat.Chat
import lidraughts.common.IpAddress
import lidraughts.game.{ Pov, PovRef, Game }
import lidraughts.hub.actorApi.map._
import lidraughts.hub.actorApi.round.{ Berserk, RematchYes, RematchNo, Abort, Resign }
import lidraughts.hub.actorApi.shutup.PublicSource
import lidraughts.socket.actorApi.{ Connected => _, _ }
import lidraughts.socket.Handler
import lidraughts.socket.Socket.Uid
import lidraughts.user.User
import lidraughts.chat.Chat
import makeTimeout.short

private[round] final class SocketHandler(
    roundMap: ActorRef,
    socketHub: ActorRef,
    hub: lidraughts.hub.Env,
    messenger: Messenger,
    evalCacheHandler: lidraughts.evalCache.EvalCacheSocketHandler,
    selfReport: SelfReport,
    bus: lidraughts.common.Bus
) {

  private def controller(
    gameId: Game.ID,
    chat: Option[Chat.Setup], // if using a non-game chat (tournament, simul, ...)
    socket: ActorRef,
    uid: Uid,
    ref: PovRef,
    member: Member,
    me: Option[User]
  ): Handler.Controller = {

    def send(msg: Any): Unit = { roundMap ! Tell(gameId, msg) }

    member.playerIdOption.fold[Handler.Controller](({
      case ("p", o) => socket ! Ping(uid, o)
      case ("talk", o) => o str "d" foreach { messenger.watcher(gameId, member, _) }
      case ("outoftime", _) => send(QuietFlag) // mobile app BC
      case ("flag", o) => clientFlag(o, none) foreach send
    }: Handler.Controller) orElse evalCacheHandler(member, me) orElse lidraughts.chat.Socket.in(
      chatId = Chat.Id(s"$gameId/w"),
      member = member,
      socket = socket,
      chat = messenger.chat,
      publicSource = PublicSource.Watcher(gameId).some
    )) { playerId =>
      ({
        case ("p", o) => socket ! Ping(uid, o)
        case ("move", o) => parseMove(o) foreach {
          case (move, blur, lag) =>
            val promise = Promise[Unit]
            promise.future onFailure {
              case _: Exception => socket ! Resync(uid.value)
            }
            send(HumanPlay(playerId, move, blur, lag, promise.some))
            member push ackEvent
        }
        case ("rematch-yes", _) => send(RematchYes(playerId))
        case ("rematch-no", _) => send(RematchNo(playerId))
        case ("takeback-yes", _) => send(TakebackYes(playerId))
        case ("takeback-no", _) => send(TakebackNo(playerId))
        case ("draw-yes", _) => send(DrawYes(playerId))
        case ("draw-no", _) => send(DrawNo(playerId))
        case ("draw-claim", _) => send(DrawClaim(playerId))
        case ("resign", _) => send(Resign(playerId))
        case ("resign-force", _) => send(ResignForce(playerId))
        case ("draw-force", _) => send(DrawForce(playerId))
        case ("abort", _) => send(Abort(playerId))
        case ("moretime", _) => send(Moretime(playerId))
        case ("outoftime", _) => send(QuietFlag) // mobile app BC
        case ("flag", o) => clientFlag(o, playerId.some) foreach send
        case ("bye2", _) => socket ! Bye(ref.color)
        case ("talk", o) if chat.isEmpty => o str "d" foreach { messenger.owner(gameId, member, _) }
        case ("hold", o) => for {
          d ← o obj "d"
          mean ← d int "mean"
          sd ← d int "sd"
        } send(HoldAlert(playerId, mean, sd, member.ip))
        case ("berserk", _) => member.userId foreach { userId =>
          hub.actor.tournamentApi ! Berserk(gameId, userId)
          member push ackEvent
        }
        case ("rep", o) => for {
          d ← o obj "d"
          name ← d str "n"
        } selfReport(member.userId, member.ip, s"$gameId$playerId", name)
      }: Handler.Controller) orElse lidraughts.chat.Socket.in(
        chatId = chat.fold(Chat.Id(gameId))(_.id),
        publicSource = chat.map(_.publicSource),
        member = member,
        socket = socket,
        chat = messenger.chat
      )
    }
  }

  def watcher(
    pov: Pov,
    uid: Uid,
    user: Option[User],
    ip: IpAddress,
    userTv: Option[String]
  ): Fu[JsSocketHandler] = join(pov, none, uid, user, ip, userTv = userTv)

  def player(
    pov: Pov,
    uid: Uid,
    user: Option[User],
    ip: IpAddress
  ): Fu[JsSocketHandler] =
    join(pov, Some(pov.playerId), uid, user, ip, userTv = none)

  private def join(
    pov: Pov,
    playerId: Option[String],
    uid: Uid,
    user: Option[User],
    ip: IpAddress,
    userTv: Option[String]
  ): Fu[JsSocketHandler] = {
    val join = Join(
      uid = uid,
      user = user,
      color = pov.color,
      playerId = playerId,
      ip = ip,
      userTv = userTv
    )
    // non-game chat, for tournament or simul games; only for players
    val chatSetup = playerId.isDefined ?? {
      pov.game.tournamentId.map(Chat.tournamentSetup) orElse pov.game.simulId.map(Chat.simulSetup)
    }
    socketHub ? Get(pov.gameId) mapTo manifest[ActorRef] flatMap { socket =>
      Handler(hub, socket, uid, join) {
        case Connected(enum, member) =>
          // register to the TV channel when watching TV
          if (playerId.isEmpty && pov.game.isRecentTv)
            hub.channel.tvSelect ! lidraughts.socket.Channel.Sub(member)
          (controller(pov.gameId, chatSetup, socket, uid, pov.ref, member, user), enum, member)
      }
    }
  }

  private def parseMove(o: JsObject) = for {
    d ← o obj "d"
    move <- d str "u" flatMap Uci.Move.apply orElse parseOldMove(d)
    blur = d int "b" contains 1
  } yield (move, blur, parseLag(d))

  private def parseOldMove(d: JsObject) = for {
    orig ← d str "from"
    dest ← d str "to"
    prom = d str "promotion"
    move <- Uci.Move.fromStrings(orig, dest, prom)
  } yield move

  private def parseLag(d: JsObject) = MoveMetrics(
    d.int("l") orElse d.int("lag") map Centis.ofMillis,
    d.str("s") flatMap { v => Try(Centis(Integer.parseInt(v, 36))).toOption }
  )

  private def clientFlag(o: JsObject, playerId: Option[String]) =
    o str "d" flatMap Color.apply map { ClientFlag(_, playerId) }

  private val ackEvent = Json.obj("t" -> "ack")
}
