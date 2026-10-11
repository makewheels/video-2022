"""Local real-media validation without OSS or GPU billing."""

import argparse
import json
import tempfile
from pathlib import Path

from .media import RESOLUTIONS, probe, transcode


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("serve")
    local = sub.add_parser("check-media")
    local.add_argument("source", type=Path)
    local.add_argument("--resolution", choices=RESOLUTIONS, default="720p")
    local.add_argument("--encoder", choices=["h264_nvenc", "libx264"], default="h264_nvenc")
    args = parser.parse_args()
    if args.action == "serve":
        from .server import serve
        serve()
        return
    with tempfile.TemporaryDirectory(prefix="video-gpu-check-") as directory:
        files = transcode(args.source.resolve(), Path(directory), "check", args.resolution, args.encoder)
        print(json.dumps({"encoder": args.encoder, "segmentCount": len(files) - 1,
                          "firstSegment": probe(files[0])}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
