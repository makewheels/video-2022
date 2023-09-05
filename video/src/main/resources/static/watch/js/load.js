import axios from 'axios';
import common from './common';

function loadVideo() {
  const watchId = common.getUrlVariable("v");
  if (watchId == null) {
    const lastSlashIndex = window.location.href.lastIndexOf('/');
    watchId = window.location.href.substring(lastSlashIndex + 1);
  }
  console.log("watchId=" + watchId);

  axios.get('/watchController/getWatchInfo?watchId=' + watchId
      + '&clientId=' + localStorage.clientId
      + '&sessionId=' + sessionStorage.sessionId)
      .then(function (res) {
          // 开始播放
          startPlayVideo(res.data.data);

          // 加载视频信息
          loadVideoInfo(videoId);
          // 加载播放列表
          loadPlaylist(videoId);
          // 增加观看记录
          addWatchHistory();
          // 给播放器加监听
          addPlayerEventListeners();
          // 设置发心跳定时器
          setInterval(sendHeartbeat, 2000, 'TIMER', null);
      });
}

export default loadVideo;
