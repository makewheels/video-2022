var heartbeat = {
    //增加监听事件
    addPlayerEventListeners: function () {
        //设置发心跳定时器
        setInterval("sendHeartbeat('TIMER', null)", 2000);

        player.on("ready", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("play", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("pause", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("canplay", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("playing", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("ended", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("liveStreamStop", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("onM3u8Retry", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("hideBar", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("showBar", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("waiting", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        // player.on("timeupdate",        function (res) {sendHeartbeat("EVENT", res.type)});
        player.on("snapshoted", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("requestFullScreen", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("cancelFullScreen", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("error", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("startSeek", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("completeSeek", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("resolutionChange", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("seiFrame", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
        player.on("rtsFallback", function (res) {
            sendHeartbeat("EVENT", res.type)
        });
    },

    //发心跳
    sendHeartbeat: function (type, playerEvent) {
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
        });
    }
}