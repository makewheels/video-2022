var common = {
  //判断是否是pc端
  isPC: function() {
    let userAgent = navigator.userAgent;
    let mobileAgents = ["Android", "iPhone", "SymbianOS", "Windows Phone", "iPad", "iPod"];
    for (let i = 0; i < mobileAgents.length; i++) {
      if (userAgent.indexOf(mobileAgents[i]) > 0) {
        return false;
      }
    }
    return true;
  },

  //获取url路径中的参数
  getUrlVariable: function(key) {
    const query = window.location.search.substring(1);
    const vars = query.split("&");
    for (let i = 0; i < vars.length; i++) {
      const pair = vars[i].split("=");
      if (pair[0] === key) {
        return pair[1];
      }
    }
    return null;
  }
};
