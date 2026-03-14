from setuptools import setup, find_packages

setup(
    name="video-cli",
    version="1.0.0",
    description="CLI tool for video-2022 platform — Agent-friendly video management",
    packages=find_packages(),
    python_requires=">=3.10",
    install_requires=[
        "click>=8.0",
        "requests>=2.28",
        "tabulate>=0.9",
    ],
    extras_require={
        "test": ["pytest>=7.0", "pytest-xdist>=3.0", "responses>=0.23"],
    },
    entry_points={
        "console_scripts": [
            "video-cli=video_cli.main:cli",
        ],
    },
)
