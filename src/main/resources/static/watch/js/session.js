var session = {
    //clientId
    handleClientId: function() {
        if (localStorage.getItem("clientId") != null) {
            onHandleIdsFinished();
            return;
        }
        axios.get('//' + document.domain + ':' + location.port + '/client/requestClientId')
            .then(function (res) {
                localStorage.clientId = res.data.data.clientId;
                onHandleIdsFinished();
            });
    },

    //sessionId
    handleSessionId: function() {
        if (sessionStorage.getItem("sessionId") != null) {
            onHandleIdsFinished();
            return;
        }
        axios.get('//' + document.domain + ':' + location.port + '/session/requestSessionId')
            .then(function (res) {
                sessionStorage.sessionId = res.data.data.sessionId;
                onHandleIdsFinished();
            });
    }
};
