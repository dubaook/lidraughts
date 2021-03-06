import { AnalyseApi, AnalyseOpts } from './interfaces';
import { start } from './main';

export default function(cfg: AnalyseOpts) {
  const li = window.lidraughts, data = cfg.data,
    socketParams: any = { userTv: data.userTv && data.userTv.id };
  let analyse: AnalyseApi;
  if (socketParams.userTv && data.userTv && data.userTv.gameId)
    socketParams.gameId = data.userTv.gameId;

  li.socket = li.StrongSocket(
    data.url.socket,
    data.player.version, {
      options: {
        name: 'analyse'
      },
      params: socketParams,
      receive: function(t, d) {
        analyse.socketReceive(t, d);
      },
      events: {
      }
    });
  cfg.$side = $('.analyse__side').clone();
  cfg.$underboard = $('.analyse__underboard').clone();
  cfg.trans = li.trans(cfg.i18n);
  cfg.initialPly = 'url';
  cfg.socketSend = li.socket.send;
  analyse = start(cfg);
};
