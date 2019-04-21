package views.html.board

import play.api.libs.json.JsObject

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.rating.PerfType.iconByVariant

import controllers.routes

object puzzleEditor {

  def apply(data: JsObject, pov: lidraughts.game.Pov)(implicit ctx: Context) = views.html.base.layout(
    title = "Puzzle editor",
    moreCss = cssTag("analyse.free"),
    moreJs = frag(
      analyseTag,
      analyseNvuiTag,
      embedJs(s"""lidraughts=lidraughts||{};lidraughts.user_analysis={data:${safeJsonValue(data)},i18n:${userAnalysisI18n()},explorer:{endpoint:"$explorerEndpoint",tablebaseEndpoint:"$tablebaseEndpoint"}};""")
    ),
    draughtsground = false,
    zoomable = true
  ) {
      main(cls := "analyse")(
        pov.game.synthetic option st.aside(cls := "analyse__side")(
          views.html.base.bits.mselect(
            "analyse-variant",
            span(cls := "text", dataIcon := iconByVariant(pov.game.variant))(if (pov.game.variant.fromPosition) draughts.variant.Standard.name else pov.game.variant.name),
            lidraughts.pref.Pref.puzzleVariants.map { v =>
              a(dataIcon := iconByVariant(v), href := routes.UserAnalysis.parse(v.key))(v.name)
            }
          )
        ),
        div(cls := "analyse__board main-board")(draughtsgroundSvg),
        div(cls := "analyse__tools"),
        div(cls := "analyse__controls")
      )
    }
}
