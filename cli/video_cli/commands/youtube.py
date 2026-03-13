"""YouTube integration commands."""
import click
from ..client import get, post, APIError
from ..output import print_json, print_error, print_success

YOUTUBE_BASE_URL = "https://youtube.videoplus.top:5030"


@click.group()
def youtube():
    """YouTube video integration."""
    pass


@youtube.command()
@click.option("--youtube-id", required=True, help="YouTube video ID")
@click.pass_context
def info(ctx, youtube_id):
    """Get YouTube video information."""
    try:
        result = get("/youtube/getVideoInfo", {"youtubeVideoId": youtube_id}, base_url=YOUTUBE_BASE_URL)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@youtube.command()
@click.option("--youtube-id", required=True, help="YouTube video ID")
@click.pass_context
def transfer(ctx, youtube_id):
    """Transfer a YouTube video to the platform."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = post("/youtube/transferVideo", {"youtubeVideoId": youtube_id}, base_url=base_url, token=token)
        print_success("YouTube video transfer started", result)
    except APIError as e:
        print_error(e.message, e.code)


@youtube.command()
@click.option("--youtube-id", required=True, help="YouTube video ID")
@click.pass_context
def extension(ctx, youtube_id):
    """Get YouTube video file extension."""
    try:
        result = get("/youtube/getFileExtension", {"youtubeVideoId": youtube_id}, base_url=YOUTUBE_BASE_URL)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
