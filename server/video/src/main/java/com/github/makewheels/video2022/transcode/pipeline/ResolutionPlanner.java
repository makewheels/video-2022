package com.github.makewheels.video2022.transcode.pipeline;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 档位与处理方决策（设计 §1 / §2）。
 * <p>
 * 按显示短边 D 选档：D &lt; 720 单档 720p 标签原尺寸；720 ≤ D &lt; 1080 单档 720p；D ≥ 1080 两档。
 * 不放大、不裁剪。重编码决策按源媒体信息与档位逐项判断。
 */
@Service
public class ResolutionPlanner {

    /** 码率超过该值需要压缩（单位 bps） */
    @Value("${transcode.max-direct-bitrate-bps:13000000}")
    private long maxDirectBitrateBps;

    /** 档位 → 目标短边 */
    public static int targetShortSide(String resolution) {
        switch (resolution) {
            case "1080p": return 1080;
            case "720p": return 720;
            default: throw new IllegalArgumentException("未知档位 " + resolution);
        }
    }

    /**
     * 选档：返回逻辑档位列表，按短边升序。
     */
    public List<String> planResolutions(int displayShortSide) {
        List<String> targets = new ArrayList<>();
        if (displayShortSide >= 1080) {
            targets.add("720p");
            targets.add("1080p");
        } else {
            // 720 ≤ D < 1080 单档 720p；D < 720 单档 720p 标签原尺寸，不放大
            targets.add("720p");
        }
        return targets;
    }

    /**
     * 计算输出显示尺寸：短边缩放到 target（不放大），保持比例，宽高取偶。
     * 不缩放时仅做必要的偶数对齐（最多各 1 像素）。
     */
    public int[] computeOutputSize(int displayWidth, int displayHeight, String resolution) {
        int d = Math.min(displayWidth, displayHeight);
        int target = targetShortSide(resolution);
        int w = displayWidth;
        int h = displayHeight;
        if (d > target) {
            if (displayWidth >= displayHeight) {
                // 短边是 height
                h = target;
                w = Math.round(displayWidth * (float) target / displayHeight);
            } else {
                w = target;
                h = Math.round(displayHeight * (float) target / displayWidth);
            }
        }
        // 偶数对齐（编码器要求），最多 ±1 像素
        w -= w % 2;
        h -= h % 2;
        return new int[]{w, h};
    }

    /**
     * 决定单档处理方：需要重编码（或 HDR/高位深/422 采样）走 GPU，否则 CPU remux。
     */
    public String decideProvider(JSONObject media, String resolution) {
        if (needReencode(media, resolution)) {
            return TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU;
        }
        return TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU;
    }

    /**
     * 是否需要重编码。
     */
    public boolean needReencode(JSONObject media, String resolution) {
        String videoCodec = media.getString("videoCodec");
        String audioCodec = media.getString("audioCodec");
        boolean hasAudio = media.getBooleanValue("hasAudio");
        long bitrateBps = media.getLongValue("bitrateBps");
        String pixFmt = media.getString("pixFmt");
        String dynamicRange = media.getString("dynamicRange");

        if (videoCodec == null || !"h264".equals(videoCodec)) return true;
        if (hasAudio && (audioCodec == null || !"aac".equals(audioCodec))) return true;
        if (bitrateBps > maxDirectBitrateBps) return true;
        // 4:2:0 之外采样（422/444）TS 封装兼容性差，统一重编码
        if (pixFmt == null || !(pixFmt.startsWith("yuv420p") || pixFmt.startsWith("nv12"))) return true;
        // 非 SDR（HDR10/HLG/PQ/DV/UNKNOWN）需要 HEVC Main10，不允许 remux
        if (dynamicRange == null || !"SDR".equals(dynamicRange)) return true;

        // 源显示短边大于目标短边才缩放；相等或更小则原尺寸 remux
        int displayShortSide = Math.min(
                media.getIntValue("displayWidth"), media.getIntValue("displayHeight"));
        if (displayShortSide > targetShortSide(resolution)) return true;
        return false;
    }

    /**
     * 构建 mediaPolicy（协议 v1）。
     * SDR 输出 H.264 + TS；非 SDR 输出 HEVC Main10 + fMP4。帧率一律 preserve。
     */
    public JSONObject buildMediaPolicy(JSONObject media, String resolution) {
        int[] size = computeOutputSize(
                media.getIntValue("displayWidth"), media.getIntValue("displayHeight"), resolution);
        String dynamicRange = media.getString("dynamicRange");
        boolean hdr = dynamicRange == null || !"SDR".equals(dynamicRange);

        JSONObject policy = new JSONObject();
        policy.put("outputWidth", size[0]);
        policy.put("outputHeight", size[1]);
        policy.put("dynamicRange", hdr ? dynamicRange : "SDR");
        policy.put("videoCodec", hdr ? "hevc" : "h264");
        policy.put("videoEncoder", hdr ? "hevc_nvenc" : "h264_nvenc");
        policy.put("frameRateMode", "preserve");
        policy.put("segmentType", hdr ? "fmp4" : "ts");
        policy.put("segmentSeconds", 6);

        // 目标码率以现有 MPS 基准为起点（720p 4 Mbps / 1080p 12 Mbps），
        // VBR：maxrate = 1.5×，bufsize = 2×maxrate，实测后可调
        long target = resolution.equals("1080p") ? 12_000_000L : 4_000_000L;
        policy.put("targetBitrateBps", target);
        policy.put("maxBitrateBps", target / 2 * 3);
        policy.put("bufferBits", target * 3);

        // 不放大时不需要重编码的档位走 remux
        policy.put("reencode", needReencode(media, resolution));

        // 色彩元数据透传（函数侧按此打 tag；SDR 值可为空）
        policy.put("colorPrimaries", media.getString("colorPrimaries"));
        policy.put("colorTransfer", media.getString("colorTransfer"));
        policy.put("colorSpace", media.getString("colorSpace"));
        policy.put("colorRange", media.getString("colorRange"));
        // HDR 静态元数据（master-display / maxCLL 等 side data），函数侧透传
        policy.put("hdrSideData", media.getJSONObject("hdrSideData"));

        // 音频：remux 时 copy（原样保留多音轨）；重编码时转 AAC 并保留全部音轨/声道/语言
        JSONObject audioPolicy = new JSONObject();
        audioPolicy.put("mode", policy.getBoolean("reencode") ? "encode" : "copy");
        audioPolicy.put("codec", "aac");
        audioPolicy.put("preserveTracks", true);
        policy.put("audioPolicy", audioPolicy);

        return policy;
    }

}
