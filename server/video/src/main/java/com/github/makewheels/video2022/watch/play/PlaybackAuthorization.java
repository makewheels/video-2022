package com.github.makewheels.video2022.watch.play;

import cn.hutool.core.util.IdUtil;
import com.github.makewheels.video2022.file.FileAccessSignatureService;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.Visibility;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 限定 video/client/session 的短期播放授权，HLS 客户端不需要转发用户登录凭证。 */
@Service
public class PlaybackAuthorization {
    @Resource private FileAccessSignatureService signatures;

    public void authorize(Video video, Context context) {
        if (video == null) throw new VideoException(ErrorCode.VIDEO_NOT_EXIST);
        if (!Visibility.PRIVATE.equals(video.getVisibility())) return;
        if (video.getUploaderId() != null && video.getUploaderId().equals(UserHolder.getUserId())) return;
        if (hasPlaybackSignature(context)) return;
        throw new VideoException(ErrorCode.FILE_ACCESS_SIGNATURE_INVALID, "该视频为私密视频");
    }

    private boolean hasPlaybackSignature(Context context) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return false;
        var request = attrs.getRequest();
        String timestamp = request.getParameter("playbackTime");
        if (timestamp == null || !signatures.isTimestampValid(timestamp)) return false;
        return signatures.isSignatureValid(context.getVideoId(), context.getClientId(), context.getSessionId(),
                "", "playlist", timestamp, request.getParameter("playbackNonce"), request.getParameter("playbackSign"));
    }

    public String signUrl(String url, Context context) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = IdUtil.nanoId();
        String signature = signatures.generateSignature(context.getVideoId(), context.getClientId(),
                context.getSessionId(), "", "playlist", timestamp, nonce);
        return url + "&playbackTime=" + timestamp + "&playbackNonce=" + nonce + "&playbackSign=" + signature;
    }
}
