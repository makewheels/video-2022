"""给已编码 HEVC 补入源片 HDR10 静态 SEI，重封装时保留包时间戳及所有音轨。"""
import struct
from fractions import Fraction


def _scaled(value, scale):
    number = Fraction(str(value)) * scale
    if number.denominator != 1:
        raise ValueError("HDR 静态元数据无法无损表示")
    return int(number)


def sei_nal(side_data):
    payload = bytearray()
    mastering = side_data.get("Mastering display metadata")
    if mastering:
        values = []
        for color in ("green", "blue", "red", "white_point"):
            values.extend(_scaled(mastering[color + "_" + axis], 50000) for axis in ("x", "y"))
        body = struct.pack(">8H2I", *values, _scaled(mastering["max_luminance"], 10000),
                           _scaled(mastering["min_luminance"], 10000))
        payload.extend(bytes([137, len(body)]) + body)
    light = side_data.get("Content light level metadata")
    if light:
        body = struct.pack(">2H", int(light["max_content"]), int(light["max_average"]))
        payload.extend(bytes([144, len(body)]) + body)
    if not payload:
        return b""
    payload.append(0x80)
    escaped = bytearray()
    zeros = 0
    for value in payload:
        if zeros >= 2 and value <= 3:
            escaped.append(3)
            zeros = 0
        escaped.append(value)
        zeros = zeros + 1 if value == 0 else 0
    return b"\x4e\x01" + escaped


def preserve_static_hdr(source, output, side_data):
    import av

    sei = sei_nal(side_data)
    if not sei:
        return source
    with av.open(source) as incoming, av.open(output, "w", format="mp4") as outgoing:
        mapping = {stream.index: outgoing.add_stream_from_template(stream) for stream in incoming.streams}
        outgoing.metadata.update(incoming.metadata)
        for stream in incoming.streams:
            mapping[stream.index].metadata.update(stream.metadata)
        for packet in incoming.demux():
            if packet.size == 0:
                continue
            stream = packet.stream
            if stream.type == "video" and packet.is_keyframe:
                extra = stream.codec_context.extradata
                if stream.codec_context.name != "hevc" or not extra or extra[0] != 1 or len(extra) < 22:
                    raise ValueError("HEVC 配置不是可验证的 hvcC 格式")
                length_size = (extra[21] & 3) + 1
                replacement = av.Packet(len(sei).to_bytes(length_size, "big") + sei + bytes(packet))
                replacement.pts = packet.pts
                replacement.dts = packet.dts
                replacement.duration = packet.duration
                replacement.time_base = packet.time_base
                replacement.is_keyframe = packet.is_keyframe
                packet = replacement
            packet.stream = mapping[stream.index]
            outgoing.mux(packet)
    return output


def static_metadata_equal(before, after):
    for kind, expected in (before or {}).items():
        actual = (after or {}).get(kind)
        if actual is None:
            return False
        for field, value in expected.items():
            if field == "side_data_type":
                continue
            try:
                if Fraction(str(value)) != Fraction(str(actual.get(field))):
                    return False
            except (ValueError, ZeroDivisionError):
                if value != actual.get(field):
                    return False
    return True
