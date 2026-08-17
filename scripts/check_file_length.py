"""文件长度门禁：Python 源文件不超过 500 行。

用法：python scripts/check_file_length.py（在仓库根目录或任意位置均可）
"""

import sys
from pathlib import Path

MAX_LINES = 500
SCAN_DIRS = ["ai-agent", "cli", "test", "scripts"]

# 存量基线，只减不增：列出当前已超限文件及其行数上限
BASELINE = {
    "ai-agent/video_agent/tools.py": 885,
    "ai-agent/video_agent/schema.py": 745,
}


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    violations: list[str] = []
    for scan_dir in SCAN_DIRS:
        base = root / scan_dir
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.py")):
            if ".venv" in path.parts:
                continue
            rel = path.relative_to(root).as_posix()
            limit = BASELINE.get(rel, MAX_LINES)
            lines = len(path.read_text(encoding="utf-8").splitlines())
            if lines > limit:
                violations.append(f"{rel}: {lines} 行 > {limit} 行")
    if violations:
        print("文件长度超限：")
        for item in violations:
            print(f"  {item}")
        return 1
    print(f"文件长度检查通过（上限 {MAX_LINES} 行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
