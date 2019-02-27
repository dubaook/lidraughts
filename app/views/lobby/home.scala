package views.html.lobby

import play.api.libs.json.{ Json, JsObject }
import play.twirl.api.Html

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.HTTPRequest
import lidraughts.common.String.html.safeJsonValue
import lidraughts.game.Pov

import controllers.routes

object home {

  def apply(
    data: JsObject,
    userTimeline: Vector[lidraughts.timeline.Entry],
    forumRecent: List[lidraughts.forum.MiniForumPost],
    tours: List[lidraughts.tournament.Tournament],
    events: List[lidraughts.event.Event],
    relays: List[lidraughts.relay.Relay],
    simuls: List[lidraughts.simul.Simul],
    featured: Option[lidraughts.game.Game],
    leaderboard: List[lidraughts.user.User.LightPerf],
    tournamentWinners: List[lidraughts.tournament.Winner],
    puzzle: Option[lidraughts.puzzle.DailyPuzzle],
    streams: lidraughts.streamer.LiveStreams.WithTitles,
    lastPost: List[lidraughts.blog.MiniPost],
    playban: Option[lidraughts.playban.TempBan],
    currentGame: Option[lidraughts.app.mashup.Preload.CurrentGame],
    nbRounds: Int
  )(implicit ctx: Context) = views.html.base.layout(
    title = "",
    fullTitle = Some("lidraughts.org • " + trans.freeOnlineDraughts.txt()),
    moreJs = frag(
      jsAt(s"compiled/lidraughts.lobby${isProd ?? (".min")}.js", async = true),
      embedJs {
        val playbanJs = playban.fold("null")(pb => safeJsonValue(Json.obj("minutes" -> pb.mins, "remainingSeconds" -> (pb.remainingSeconds + 3))))
        val gameJs = currentGame.fold("null")(cg => safeJsonValue(cg.json))
        val transJs = safeJsonValue(i18nJsObject(translations))
        s"""window.lidraughts=window.lidraughts||{};window.customWS=true;lidraughts_lobby={data:${safeJsonValue(data)},playban:$playbanJs,currentGame:$gameJs,i18n:$transJs}"""
      }
    ),
    moreCss = responsiveCssTag("lobby"),
    draughtsground = false,
    responsive = true,
    openGraph = lidraughts.app.ui.OpenGraph(
      image = staticUrl("images/large_tile.png").some,
      title = "The best free, adless draughts server",
      url = netBaseUrl,
      description = trans.siteDescription.txt()
    ).some,
    asyncJs = true
  ) {
      main(cls := "lobby")(
        div(cls := "lobby__side")(
          ctx.noKid option st.section(id := "streams_on_air")(views.html.streamer.bits liveStreams streams),
          events.map(bits.spotlight),
          relays.map(bits.spotlight),
          !ctx.isBot option frag(
            lidraughts.tournament.Spotlight.select(tours, ctx.me, 3) map { views.html.tournament.homepageSpotlight(_) },
            simuls.find(_.spotlightable) take 2 map { views.html.simul.bits.homepageSpotlight(_) } toList
          ),
          ctx.me map { u =>
            div(cls := "timeline", dataHref := routes.Timeline.home)(
              views.html.timeline entries userTimeline,
              // userTimeline.size >= 8 option
              a(cls := "more", href := routes.Timeline.home)(trans.more.frag(), " »")
            )
          } getOrElse {
            div(cls := "about-side")(
              trans.xIsAFreeYLibreOpenSourceDraughtsServer.frag("Lidraughts", a(cls := "blue", href := routes.Plan.features)(trans.really.txt())),
              " ",
              a(cls := "blue", href := "/about")(trans.aboutX.frag("lidraughts.org"), "...")
            )
          }
        ),
        currentGame.map(bits.currentGameInfo) orElse
          playban.map(bits.playbanInfo) getOrElse
          bits.lobbyApp,
        div(cls := "lobby__table")(
          div(cls := "lobby__start")(
            a(href := routes.Setup.hookForm, cls := List(
              "fat button config_hook" -> true,
              "disabled" -> (playban.isDefined || currentGame.isDefined || ctx.isBot)
            ), trans.createAGame.frag()),
            a(href := routes.Setup.friendForm(none), cls := List(
              "fat button config_friend" -> true,
              "disabled" -> currentGame.isDefined
            ), trans.playWithAFriend.frag()),
            a(href := routes.Setup.aiForm, cls := List(
              "fat button config_ai" -> true,
              "disabled" -> currentGame.isDefined
            ), trans.playWithTheMachine.frag())
          ),
          div(cls := "lobby__counters")(
            a(id := "nb_connected_players", href := ctx.noBlind.option(routes.User.list.toString))(trans.nbPlayers.frag(nbPlayersPlaceholder)),
            a(id := "nb_games_in_play", href := ctx.noBlind.option(routes.Tv.games.toString))(
              trans.nbGamesInPlay.pluralFrag(nbRounds, span(nbRounds))
            )
          )
        ),
        featured map { g =>
          div(cls := "lobby__tv")(
            gameFen(Pov first g, tv = true),
            views.html.game.bits.vstext(Pov first g)(ctx.some)
          )
        },
        puzzle map { p =>
          div(cls := "lobby__puzzle", title := trans.clickToSolve.txt())(
            raw(p.html),
            div(cls := "vstext")(
              trans.puzzleOfTheDay.frag(),
              br,
              p.color.fold(trans.whitePlays, trans.blackPlays)()
            )
          )
        },
        ctx.noBot option bits.underboards(tours, simuls, leaderboard, tournamentWinners),
        ctx.noKid option div(cls := "lobby__forum", dataUrl := routes.ForumPost.recent)(
          div(cls := "undertable_top")(
            a(cls := "more", href := routes.ForumCateg.index)(trans.more.frag(), " »"),
            span(cls := "title text", dataIcon := "d")(trans.latestForumPosts.frag())
          ),
          div(cls := "undertable_inner scroll-shadow-hard")(
            div(cls := "content")(views.html.forum.post recent forumRecent)
          )
        ),
        bits.lastPosts(lastPost),
        div(cls := "lobby__support")(
          a(href := routes.Plan.index)(
            iconTag(patronIconChar),
            strong("Lidraughts Patron"),
            span(trans.directlySupportLidraughts.frag()),
            iconTag(patronIconChar)
          )
        /*a(href := routes.Page.swag)(
            iconTag(""),
            strong("Swag Store"),
            span(trans.playDraughtsInStyle.frag())
          )*/
        ),
        div(cls := "lobby__about")(a(href := "/about")(trans.aboutX.frag("lidraughts.org")))
      )
    }

  private val translations = List(
    trans.realTime,
    trans.correspondence,
    trans.nbGamesInPlay,
    trans.player,
    trans.time,
    trans.joinTheGame,
    trans.cancel,
    trans.casual,
    trans.rated,
    trans.variant,
    trans.mode,
    trans.list,
    trans.graph,
    trans.filterGames,
    trans.youNeedAnAccountToDoThat,
    trans.oneDay,
    trans.nbDays,
    trans.aiNameLevelAiLevel,
    trans.yourTurn,
    trans.rating,
    trans.createAGame,
    trans.quickPairing,
    trans.lobby,
    trans.custom,
    trans.anonymous
  )

  private val nbPlayersPlaceholder = strong("--,---")
}
