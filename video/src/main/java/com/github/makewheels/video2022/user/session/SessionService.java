package com.github.makewheels.video2022.user.session;

import cn.hutool.json.JSONObject;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.Result;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@Service
public class SessionService {
    @Resource
    private MongoTemplate mongoTemplate;

    public Result<JSONObject> requestSessionId() {
        HttpServletRequest request = RequestUtil.getRequest();
        Session session = new Session();
        session.setCreateTime(new Date());
        session.setIp(request.getRemoteAddr());
        session.setUserAgent(request.getHeader("User-Agent"));
        mongoTemplate.save(session);
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("sessionId", session.getId());
        return Result.ok(jsonObject);
    }
}
