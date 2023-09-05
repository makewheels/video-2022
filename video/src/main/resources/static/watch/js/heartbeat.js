import axios from 'axios';

function sendHeartbeat(type, playerEvent, videoId, videoStatus, player) {
  axios.post("/heartbeat/add", {
      videoId: videoId,
      clientId: localStorage.clientId,
      sessionId: sessionStorage.sessionId,
      videoStatus: videoStatus,
      playerProvider: "ALIYUN_WEB",
      clientTime: new Date(),
      type: type,
      event: playerEvent,
      playerTime: player.getCurrentTime() * 1000,
      playerStatus: player.getStatus(),
      playerVolume: player.getVolume()
  }, {
      headers: {"token": localStorage.token}
  }).then(function (res) {
      // 处理发送心跳成功的逻辑
  });
}

function addWatchHistory(videoId, videoStatus) {
  axios.get('/watchController/addWatchLog?videoId=' + videoId
      + '&clientId=' + localStorage.clientId
      + '&sessionId=' + sessionStorage.sessionId
      + '&videoStatus=' + videoStatus)
      .then(function (res) {
          // 处理增加观看记录成功的逻辑
      });
}

function addPlayerEventListeners(player, sendHeartbeat) {
  player.on("ready", function (res) {
      sendHeartbeat("EVENT", res.type)
  });
  // 添加其他事件监听器的逻辑
}

export { sendHeartbeat, addWatchHistory, addPlayerEventListeners };
