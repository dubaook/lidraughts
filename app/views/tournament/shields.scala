package views.html.tournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.rating.PerfType
import lidraughts.tournament.Tournament

import controllers.routes

object shields {

  private val section = st.section(cls := "tournament-shields__item")

  def apply(history: lidraughts.tournament.TournamentShield.History)(implicit ctx: Context) =
    views.html.base.layout(
      title = "Tournament shields",
      moreCss = cssTag("tournament.leaderboard"),
      wrapClass = "full-screen-force"
    ) {
        main(cls := "page-menu")(
          views.html.user.bits.communityMenu("shield"),
          div(cls := "page-menu__content box box-pad")(
            h1("Tournament shields"),
            div(cls := "tournament-shields")(
              history.sorted.map {
                case (categ, awards) => {
                  section(
                    h2(
                      span(cls := "shield-trophy")(categ.iconChar.toString),
                      categ.name
                    ),
                    ul(
                      awards.map { aw =>
                        li(
                          userIdLink(aw.owner.value.some),
                          a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date))
                        )
                      }
                    )
                  )
                }
              }
            )
          )
        )
      }
}
