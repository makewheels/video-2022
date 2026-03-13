"""Video management commands."""
import os
import click
from ..client import get, post, APIError
from ..output import print_json, print_table, print_error, print_success


@click.group()
def video():
    """Video upload, management, and status."""
    pass


@video.command("list")
@click.option("--skip", default=0, help="Number of items to skip")
@click.option("--limit", default=20, help="Number of items to return")
@click.option("--keyword", default=None, help="Search keyword")
@click.pass_context
def list_videos(ctx, skip, limit, keyword):
    """List my videos with pagination."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        params = {"skip": skip, "limit": limit}
        if keyword:
            params["keyword"] = keyword
        result = get("/video/getMyVideoList", params, base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and result:
            videos = result.get("list", []) if isinstance(result, dict) else result
            if isinstance(videos, list):
                rows = [[v.get("id", ""), v.get("title", ""), v.get("status", ""), v.get("watchCount", 0)] for v in videos]
                print_table(["ID", "Title", "Status", "Views"], rows)
            else:
                print_json(result)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@video.command()
@click.option("--id", "video_id", required=True, help="Video ID")
@click.pass_context
def detail(ctx, video_id):
    """Get video details."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/video/getVideoDetail", {"videoId": video_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@video.command()
@click.option("--id", "video_id", required=True, help="Video ID")
@click.pass_context
def status(ctx, video_id):
    """Get video processing status (lightweight)."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/video/getVideoStatus", {"videoId": video_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@video.command()
@click.option("--id", "video_id", required=True, help="Video ID")
@click.option("--title", default=None, help="New title")
@click.option("--description", default=None, help="New description")
@click.option("--visibility", default=None, type=click.Choice(["PUBLIC", "PRIVATE"]), help="Visibility")
@click.pass_context
def update(ctx, video_id, title, description, visibility):
    """Update video metadata."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {"id": video_id}
        if title:
            data["title"] = title
        if description:
            data["description"] = description
        if visibility:
            data["visibility"] = visibility
        result = post("/video/updateInfo", data, base_url=base_url, token=token)
        print_success("Video updated", result)
    except APIError as e:
        print_error(e.message, e.code)


@video.command()
@click.option("--id", "video_id", required=True, help="Video ID")
@click.pass_context
def delete(ctx, video_id):
    """Delete a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/video/delete", {"videoId": video_id}, base_url=base_url, token=token)
        print_success("Video deleted", result)
    except APIError as e:
        print_error(e.message, e.code)


@video.command()
@click.option("--file", "filename", required=True, help="Video filename (e.g., test.mp4)")
@click.option("--type", "video_type", default="UPLOAD", help="Video type (default: UPLOAD)")
@click.pass_context
def create(ctx, filename, video_type):
    """Pre-create a video for upload."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        size = 0
        if os.path.exists(filename):
            size = os.path.getsize(filename)
        data = {
            "videoType": video_type,
            "rawFilename": os.path.basename(filename),
            "size": size,
            "ttl": "PERMANENT",
        }
        result = post("/video/create", data, base_url=base_url, token=token)
        print_success("Video created", result)
    except APIError as e:
        print_error(e.message, e.code)


@video.command("download-url")
@click.option("--id", "video_id", required=True, help="Video ID")
@click.pass_context
def download_url(ctx, video_id):
    """Get raw file download URL."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/video/getRawFileDownloadUrl", {"videoId": video_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
