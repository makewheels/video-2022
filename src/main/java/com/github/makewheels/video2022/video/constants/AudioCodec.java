package com.github.makewheels.video2022.video.constants;

public class AudioCodec {
    public static final String AAC = "aac";
    public static final String OPUS = "opus";

    public static boolean isAac(String codec) {
        return AAC.equals(codec);
    }
}
