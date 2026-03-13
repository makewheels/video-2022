"""Comment commands."""
import click
from ..client import get, post, APIError
from ..output import print_json, print_table, print_error, print_success


@click.group()
def comment():
    """Video comments management."""
    pass


@comment.command()
@click.option("--video-id", required=True, help="Video ID")
@click.option("--content", required=True, help="Comment content")
@click.option("--parent-id", default=None, help="Parent comment ID (for replies)")
@click.pass_context
def add(ctx, video_id, content, parent_id):
    """Add a comment to a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {"videoId": video_id, "content": content}
        if parent_id:
            data["parentId"] = parent_id
        result = post("/comment/add", data, base_url=base_url, token=token)
        print_success("Comment added", result)
    except APIError as e:
        print_error(e.message, e.code)


@comment.command("list")
@click.option("--video-id", required=True, help="Video ID")
@click.option("--skip", default=0, help="Number of items to skip")
@click.option("--limit", default=20, help="Number of items to return")
@click.option("--sort", default="latest", type=click.Choice(["latest", "oldest"]), help="Sort order")
@click.pass_context
def list_comments(ctx, video_id, skip, limit, sort):
    """List comments for a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        params = {"videoId": video_id, "skip": skip, "limit": limit, "sort": sort}
        result = get("/comment/getByVideoId", params, base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and isinstance(result, list):
            rows = [[c.get("id", ""), c.get("content", "")[:50], c.get("likeCount", 0)] for c in result]
            print_table(["ID", "Content", "Likes"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@comment.command()
@click.option("--id", "comment_id", required=True, help="Comment ID")
@click.pass_context
def delete(ctx, comment_id):
    """Delete a comment."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/comment/delete", {"commentId": comment_id}, base_url=base_url, token=token)
        print_success("Comment deleted", result)
    except APIError as e:
        print_error(e.message, e.code)


@comment.command("like")
@click.option("--id", "comment_id", required=True, help="Comment ID")
@click.pass_context
def like_comment(ctx, comment_id):
    """Like a comment."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/comment/like", {"commentId": comment_id}, base_url=base_url, token=token)
        print_success("Comment liked", result)
    except APIError as e:
        print_error(e.message, e.code)


@comment.command()
@click.option("--video-id", required=True, help="Video ID")
@click.pass_context
def count(ctx, video_id):
    """Get comment count for a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/comment/getCount", {"videoId": video_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@comment.command()
@click.option("--parent-id", required=True, help="Parent comment ID")
@click.option("--skip", default=0, help="Number of items to skip")
@click.option("--limit", default=20, help="Number of items to return")
@click.pass_context
def replies(ctx, parent_id, skip, limit):
    """Get replies to a comment."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/comment/getReplies", {"parentId": parent_id, "skip": skip, "limit": limit}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
