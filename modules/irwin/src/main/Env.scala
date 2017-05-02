package lila.irwin

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lila.tournament.TournamentApi

final class Env(
    config: Config,
    system: ActorSystem,
    scheduler: lila.common.Scheduler,
    tournamentApi: TournamentApi,
    userCache: lila.user.Cached,
    db: lila.db.Env
) {

  private val reportColl = db(config getString "collection.report")
  private val requestColl = db(config getString "collection.request")

  val api = new IrwinApi(
    reportColl = reportColl,
    requestColl = requestColl
  )

  scheduler.future(5 minutes, "irwin tournament leaders") {
    tournamentApi.allCurrentLeadersInStandard flatMap api.requests.fromTournamentLeaders
  }
  scheduler.future(15 minutes, "irwin leaderboards") {
    lila.common.Future.applySequentially(lila.rating.PerfType.standard) { pt =>
      userCache.top200Perf(pt.id) flatMap { users =>
        api.requests.fromLeaderboard(users.take(50).map(_.user.id))
      }
    }
  }

  system.lilaBus.subscribe(system.actorOf(Props(new Actor {
    def receive = {
      case lila.hub.actorApi.report.Created(userId, "cheat") => api.requests.insert(userId, _.Report)
      case lila.hub.actorApi.report.Processed(userId, "cheat") => api.requests.drop(userId)
    }
  })), 'report)
}

object Env {

  lazy val current: Env = "irwin" boot new Env(
    db = lila.db.Env.current,
    config = lila.common.PlayApp loadConfig "irwin",
    tournamentApi = lila.tournament.Env.current.api,
    userCache = lila.user.Env.current.cached,
    scheduler = lila.common.PlayApp.scheduler,
    system = lila.common.PlayApp.system
  )
}
