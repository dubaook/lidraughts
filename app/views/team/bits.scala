package views.html.team

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.paginator.Paginator

import controllers.routes

object bits {

  def menu(currentTab: Option[String])(implicit ctx: Context) = ~currentTab |> { tab =>
    st.nav(cls := "page-menu__menu subnav")(
      (ctx.teamNbRequests > 0) option
        a(cls := tab.active("requests"), href := routes.Team.requests())(
          ctx.teamNbRequests, " join requests"
        ),
      ctx.me.??(_.canTeam) option
        a(cls := tab.active("mine"), href := routes.Team.mine())(
          trans.myTeams()
        ),
      a(cls := tab.active("all"), href := routes.Team.all())(
        trans.allTeams()
      ),
      ctx.me.??(_.canTeam) option
        a(cls := tab.active("form"), href := routes.Team.form())(
          trans.newTeam()
        )
    )
  }

  private[team] def teamTr(t: lidraughts.team.Team)(implicit ctx: Context) = tr(cls := "paginated")(
    td(cls := "subject")(
      a(dataIcon := "f", cls := List(
        "team-name text" -> true,
        "mine" -> myTeam(t.id)
      ), href := routes.Team.show(t.id))(t.name),
      shorten(t.description, 200)
    ),
    td(cls := "info")(
      p(trans.nbMembers.plural(t.nbMembers, t.nbMembers.localize))
    )
  )

  private[team] def layout(title: String, openGraph: Option[lidraughts.app.ui.OpenGraph] = None)(body: Frag)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("team"),
      moreJs = infiniteScrollTag,
      openGraph = openGraph
    )(body)
}
