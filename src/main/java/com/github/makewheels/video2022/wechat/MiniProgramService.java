package com.github.makewheels.video2022.wechat;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.file.FileType;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.Video;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.InputStream;
import java.time.Duration;

@Service
public class MiniProgramService {
    @Value("${mini-program.env}")
    private String miniProgramEnv;

    @Resource
    private FileService fileService;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private MongoTemplate mongoTemplate;

    private String accessToken;
    private long accessTokenExpireAt;

    /**
     * {"access_token":"63_3U8e8xSlI6nv-2sRghMQW2bUDy34dzyZBsTi-lX02tNxMcEU9x769TpG375VqzmCfVzb
     * rp9XBr_2n2CJ1ZoEHDDEauRKUIWaI-fNRI-1yvH0P57i8xOPnIKoK2QNSHhAJAWBQ",
     * "expires_in":7200}
     */
    private String getAccessToken() {
        //如果已经有了，并且没过期，直接返回
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpireAt) {
            return accessToken;
        }
        //否则请求微信
        String json = HttpUtil.get("https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential&appid=wx2b94f07ee281d8ce"
                + "&secret=cbb6376401f724ab412322367c3fdd87");
        JSONObject jsonObject = JSON.parseObject(json);
        accessToken = jsonObject.getString("access_token");
        accessTokenExpireAt = System.currentTimeMillis() + jsonObject.getInteger("expires_in") * 1000;
        return accessToken;
    }

    private InputStream getQrCodeInputStream(String videoId) {
        // https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/qrcode-link/qr-code/getUnlimitedQRCode.html
        JSONObject param = new JSONObject();
        param.put("scene", videoId);
        param.put("page", "pages/share/share");
        param.put("width", 300);
        if (miniProgramEnv.equals("dev")) {
            param.put("check_path", false);
//            param.put("env_version", "develop");
            param.put("env_version", "trial");
        }
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + getAccessToken();
        HttpResponse response = HttpUtil.createPost(url).body(param.toJSONString()).execute();
        return response.bodyStream();
    }

    /**
     * 创建小程序码图片，上传OSS
     */
    public Result<JSONObject> getShareQrCodeUrl(User user, String videoId) {
        Video video = videoRepository.getById(videoId);

        //创建File对象
        File file = new File();
        file.init();
        file.setStatus(FileStatus.CREATED);
        file.setType(FileType.QR_CODE);
        file.setVideoType(video.getType());
        file.setVideoId(videoId);
        file.setUserId(user.getId());
        mongoTemplate.save(file);

        String key = PathUtil.getS3VideoPrefix(user.getId(), videoId)
                + "/qrcode/" + file.getId() + ".jpg";
        mongoTemplate.save(file);

        //上传阿里云OSS
        fileService.putObject(key, getQrCodeInputStream(videoId));
        OSSObject object = fileService.getObject(key);
        file.setObjectInfo(object);
        mongoTemplate.save(file);

        //返回url
        JSONObject response = new JSONObject();
        String url = fileService.generatePresignedUrl(key, Duration.ofHours(1));
        response.put("qrCodeUrl", url);
        return Result.ok(response);
    }

    /**
     * 登录
     * {
     * "session_key": "tQY+38pdTVnAIWCBNiM1+A==",
     * "openid": "o--sB5rdWBfwTidbZzzn4FXfWpEg"
     * }
     * <p>
     * {
     * "errcode": 40163,
     * "errmsg": "code been used, rid: 6380622c-50076416-224b40b0"
     * }
     */
    public Result<JSONObject> login(User user, String jscode) {
        JSONObject json = JSON.parseObject(HttpUtil.get("https://api.weixin.qq.com/sns/jscode2session" +
                "?appid=wx2b94f07ee281d8ce" +
                "&secret=cbb6376401f724ab412322367c3fdd87" +
                "&js_code=" + jscode));
        String openid = json.getString("openid");
        String sessionKey = json.getString("session_key");
        if (openid == null) {
            throw new RuntimeException("获取不到openid, jscode = " + jscode);
        }
        return null;
    }
}
