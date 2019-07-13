package lidraughts.relay

import lidraughts.common.ApiVersion
import lidraughts.socket.Socket.{ Sri, SocketVersion }
import lidraughts.socket.{ Handler, JsSocketHandler }
import lidraughts.study.{ Study, StudySocket, SocketHandler => StudyHandler }
import lidraughts.user.User

private[relay] final class SocketHandler(
    studyHandler: StudyHandler,
    api: RelayApi
) {

  private def makeController(
    socket: StudySocket,
    relayId: Relay.Id,
    sri: Sri,
    member: StudySocket.Member,
    user: Option[User]
  ): Handler.Controller = ({
    case ("relaySync", o) =>
      logger.info(s"${user.fold("Anon")(_.username)} toggles #${relayId}")
      api.requestPlay(relayId, ~(o \ "d").asOpt[Boolean])
  }: Handler.Controller) orElse studyHandler.makeController(
    socket = socket,
    studyId = Study.Id(relayId.value),
    sri = sri,
    member = member,
    user = user
  )

  def join(
    relayId: Relay.Id,
    sri: Sri,
    user: Option[User],
    version: Option[SocketVersion],
    apiVersion: ApiVersion
  ): Fu[JsSocketHandler] = {
    val studyId = Study.Id(relayId.value)
    val socket = studyHandler.getSocket(studyId)
    studyHandler.join(studyId, sri, user, socket, member => makeController(socket, relayId, sri, member, user), version, apiVersion)
  }
}
