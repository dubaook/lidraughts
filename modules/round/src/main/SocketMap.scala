package lidraughts.round

import scala.concurrent.duration._

import lidraughts.game.Game
import lidraughts.hub.actorApi.map.{ Tell, TellIfExists, Exists }
import lidraughts.hub.TrouperMap

private object SocketMap {

  def make(
    makeHistory: (String, Boolean) => History,
    dependencies: RoundSocket.Dependencies,
    socketTimeout: FiniteDuration
  ): SocketMap = {

    var historyPersistenceEnabled = false

    import dependencies._
    val bus = system.lidraughtsBus

    lazy val socketMap: SocketMap = new TrouperMap[RoundSocket](
      mkTrouper = (id: Game.ID) => new RoundSocket(
        dependencies = dependencies,
        gameId = id,
        history = makeHistory(id, historyPersistenceEnabled),
        keepMeAlive = () => socketMap touch id
      ),
      accessTimeout = socketTimeout
    )
    system.scheduler.schedule(30 seconds, 30 seconds) {
      socketMap.monitor("round.socketMap")
    }
    system.scheduler.schedule(10 seconds, 4001 millis) {
      socketMap tellAll lidraughts.socket.actorApi.Broom
    }
    bus.subscribeFuns(
      'startGame -> {
        case msg: lidraughts.game.actorApi.StartGame => socketMap.tellIfPresent(msg.game.id, msg)
      },
      'roundSocket -> {
        case TellIfExists(id, msg) => socketMap.tellIfPresent(id, msg)
        case Tell(id, msg) => socketMap.tell(id, msg)
        case Exists(id, promise) => promise success socketMap.exists(id)
      },
      'deploy -> {
        case m: lidraughts.hub.actorApi.Deploy =>
          socketMap tellAll m
          logger.warn("Enable history persistence")
          historyPersistenceEnabled = true
          // if the deploy didn't go through, cancel persistence
          system.scheduler.scheduleOnce(10.minutes) {
            logger.warn("Disabling round history persistence!")
            historyPersistenceEnabled = false
          }
      }
    )

    socketMap
  }
}
