"""Video like/dislike commands."""
import click
from ..client import get, APIError
from ..output import print_json, print_error, print_success


@click.group("like")
def like_group():
    """Video like and dislike management."""
    pass


@like_group.command("like")
@click.option("--video-id", required=True, help="Video ID")
@click.pass_context
def like_video(ctx, video_id):
    """Like a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/videoLike/like", {"videoId": video_id}, base_url=base_url, token=token)
        print_success("Video liked", result)
    except APIError as e:
        print_error(e.message, e.code)


@like_group.command()
@click.option("--video-id", required=True, help="Video ID")
@click.pass_context
def dislike(ctx, video_id):
    """Dislike a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/videoLike/dislike", {"videoId": video_id}, base_url=base_url, token=token)
        print_success("Video disliked", result)
    except APIError as e:
        print_error(e.message, e.code)


@like_group.command("status")
@click.option("--video-id", required=True, help="Video ID")
@click.pass_context
def like_status(ctx, video_id):
    """Get like/dislike status for a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/videoLike/getStatus", {"videoId": video_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
