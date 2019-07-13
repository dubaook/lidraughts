package controllers

import lidraughts.api._
import lidraughts.socket.Socket.Sri
import lidraughts.user.UserContext
import lidraughts.common.Form.trueish
import lidraughts.common.IsMobile

import play.api.mvc.RequestHeader

trait RequestGetter {

  protected def get(name: String)(implicit ctx: UserContext): Option[String] = get(name, ctx.req)

  protected def get(name: String, req: RequestHeader): Option[String] =
    req.queryString get name flatMap (_.headOption) filter (_.nonEmpty)

  protected def getSocketSri(name: String)(implicit ctx: UserContext): Option[Sri] =
    get(name) map Sri.apply

  protected def getInt(name: String)(implicit ctx: UserContext) =
    get(name) flatMap parseIntOption

  protected def getInt(name: String, req: RequestHeader): Option[Int] =
    req.queryString get name flatMap (_.headOption) flatMap parseIntOption

  protected def getLong(name: String)(implicit ctx: UserContext) =
    get(name) flatMap parseLongOption

  protected def getLong(name: String, req: RequestHeader) =
    get(name, req) flatMap parseLongOption

  protected def getBool(name: String)(implicit ctx: UserContext) =
    (getInt(name) exists trueish) || (get(name) exists trueish)

  protected def getBool(name: String, req: RequestHeader) =
    (getInt(name, req) exists trueish) || (get(name, req) exists trueish)

  protected def getBoolOpt(name: String)(implicit ctx: UserContext) =
    (getInt(name) map (trueish)) orElse (get(name) map trueish)

  protected def getBoolOpt(name: String, req: RequestHeader) =
    (getInt(name, req) map (trueish)) orElse (get(name, req) map trueish)

  protected def getMobile(implicit ctx: UserContext) =
    IsMobile(getBool("mobile"))
}
