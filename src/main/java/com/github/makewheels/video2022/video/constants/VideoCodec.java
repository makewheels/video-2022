package com.github.makewheels.video2022.video.constants;

public class VideoCodec {
    public static final String H264 = "h264";
    public static final String HEVC = "hevc";
    public static final String VP9 = "vp9";

    public static boolean isH264(String codec) {
        return H264.equals(codec);
    }
}
