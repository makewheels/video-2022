package com.github.makewheels.video2022.watch.playback;

import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.watch.playback.dto.ExitPlaybackDTO;
import com.github.makewheels.video2022.watch.playback.dto.HeartbeatPlaybackDTO;
import com.github.makewheels.video2022.watch.playback.dto.StartPlaybackDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class PlaybackService {

    @Autowired
    private PlaybackSessionRepository playbackSessionRepository;

    public PlaybackSession startSession(StartPlaybackDTO dto) {
        PlaybackSession session = new PlaybackSession();
        session.setWatchId(dto.getWatchId());
        session.setVideoId(dto.getVideoId());
        session.setClientId(dto.getClientId());
        session.setSessionId(dto.getSessionId());

        String userId = UserHolder.getUserId();
        session.setUserId(userId);

        Date now = new Date();
        session.setStartTime(now);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        session.setTotalPlayDurationMs(0L);
        session.setMaxProgressMs(0L);
        session.setCurrentProgressMs(0L);
        session.setHeartbeatCount(0);
        session.setExitType("PLAYING");
        session.setResolution("auto");

        playbackSessionRepository.save(session);
        log.info("播放会话开始: sessionId={}, videoId={}", session.getId(), dto.getVideoId());
        return session;
    }

    public void heartbeat(HeartbeatPlaybackDTO dto) {
        PlaybackSession session = playbackSessionRepository.getById(dto.getPlaybackSessionId());
        if (session == null) return;

        session.setCurrentProgressMs(dto.getCurrentTimeMs());
        if (dto.getCurrentTimeMs() != null && session.getMaxProgressMs() != null
                && dto.getCurrentTimeMs() > session.getMaxProgressMs()) {
            session.setMaxProgressMs(dto.getCurrentTimeMs());
        }
        if (dto.getTotalPlayDurationMs() != null) {
            session.setTotalPlayDurationMs(dto.getTotalPlayDurationMs());
        }
        if (dto.getResolution() != null) {
            session.setResolution(dto.getResolution());
        }
        session.setHeartbeatCount(session.getHeartbeatCount() + 1);
        session.setUpdateTime(new Date());

        playbackSessionRepository.save(session);
    }

    public void exit(ExitPlaybackDTO dto) {
        PlaybackSession session = playbackSessionRepository.getById(dto.getPlaybackSessionId());
        if (session == null) return;

        session.setEndTime(new Date());
        session.setCurrentProgressMs(dto.getCurrentTimeMs());
        if (dto.getCurrentTimeMs() != null && session.getMaxProgressMs() != null
                && dto.getCurrentTimeMs() > session.getMaxProgressMs()) {
            session.setMaxProgressMs(dto.getCurrentTimeMs());
        }
        if (dto.getTotalPlayDurationMs() != null) {
            session.setTotalPlayDurationMs(dto.getTotalPlayDurationMs());
        }
        session.setExitType(dto.getExitType());
        if (dto.getResolution() != null) {
            session.setResolution(dto.getResolution());
        }
        session.setUpdateTime(new Date());

        playbackSessionRepository.save(session);
        log.info("播放会话结束: sessionId={}, duration={}ms, exitType={}",
                session.getId(), session.getTotalPlayDurationMs(), session.getExitType());
    }
}
