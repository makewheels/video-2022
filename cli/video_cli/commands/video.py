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
@click.option("--visibility", default=None, type=click.Choice(["PUBLIC", "UNLISTED", "PRIVATE"]), help="Visibility")
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
@click.option(
    "--type",
    "video_type",
    default="USER_UPLOAD",
    show_default=True,
    type=click.Choice(["USER_UPLOAD", "UPLOAD", "YOUTUBE"], case_sensitive=False),
    help="Video type",
)
@click.pass_context
def create(ctx, filename, video_type):
    """Pre-create a video for upload."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        size = 0
        if os.path.exists(filename):
            size = os.path.getsize(filename)
        normalized_video_type = video_type.upper()
        if normalized_video_type == "UPLOAD":
            normalized_video_type = "USER_UPLOAD"
        data = {
            "videoType": normalized_video_type,
            "rawFilename": os.path.basename(filename),
            "size": size,
            "ttl": "PERMANENT",
        }
        result = post("/video/create", data, base_url=base_url, token=token)
        print_success("Video created", result)
    except APIError as e:
        print_error(e.message, e.code)


@video.command()
@click.option("--file", "filepath", required=True, help="Path to the local video file to upload")
@click.option("--title", default=None, help="Title (defaults to filename)")
@click.option("--description", default=None, help="Description")
@click.option(
    "--visibility",
    default=None,
    type=click.Choice(["PUBLIC", "UNLISTED", "PRIVATE"]),
    help="Visibility (defaults to server default)",
)
@click.option(
    "--type",
    "video_type",
    default="USER_UPLOAD",
    show_default=True,
    type=click.Choice(["USER_UPLOAD", "UPLOAD", "YOUTUBE"], case_sensitive=False),
    help="Video type",
)
@click.pass_context
def upload(ctx, filepath, title, description, visibility, video_type):
    """Upload a local video file end-to-end.

    Runs the full flow: create -> getUploadCredentials -> OSS multipart
    upload -> uploadFinish -> rawFileUploadFinish, then optionally sets
    title/description/visibility.
    """
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")

    if not os.path.isfile(filepath):
        print_error(f"File not found: {filepath}")
        return
    try:
        import oss2
    except ImportError:
        print_error("oss2 is required for upload. Install with: pip install oss2")
        return

    filename = os.path.basename(filepath)
    size = os.path.getsize(filepath)
    normalized_video_type = video_type.upper()
    if normalized_video_type == "UPLOAD":
        normalized_video_type = "USER_UPLOAD"

    try:
        # 1. Pre-create video to obtain fileId / videoId
        created = post(
            "/video/create",
            {
                "videoType": normalized_video_type,
                "rawFilename": filename,
                "size": size,
                "ttl": "PERMANENT",
            },
            base_url=base_url,
            token=token,
        )
        file_id = created.get("fileId")
        video_id = created.get("videoId")

        # 2. Fetch scoped STS credentials + target OSS key
        creds = get("/file/getUploadCredentials", {"fileId": file_id}, base_url=base_url, token=token)
        endpoint = creds["endpoint"]
        if not endpoint.startswith("http"):
            endpoint = "https://" + endpoint

        # 3. Multipart upload to OSS (resumable, with progress)
        auth = oss2.StsAuth(creds["accessKeyId"], creds["secretKey"], creds["sessionToken"])
        bucket = oss2.Bucket(auth, endpoint, creds["bucket"])

        last = {"pct": -1}

        def _progress(consumed, total):
            if total:
                pct = int(consumed * 100 / total)
                if pct != last["pct"]:
                    last["pct"] = pct
                    click.echo(f"\rUploading... {pct}%", nl=False, err=True)

        oss2.resumable_upload(
            bucket,
            creds["key"],
            filepath,
            multipart_threshold=5 * 1024 * 1024,
            part_size=1024 * 1024,
            num_threads=3,
            progress_callback=_progress,
        )
        click.echo("", err=True)

        # 4. Mark raw file upload finished
        get("/file/uploadFinish", {"fileId": file_id}, base_url=base_url, token=token)

        # 5. Set metadata BEFORE triggering transcoding: the async transcode
        #    thread re-saves the Video document, so a later updateInfo would
        #    race with it and lose the title/visibility.
        if title or description or visibility:
            data = {"id": video_id}
            data["title"] = title if title else os.path.splitext(filename)[0]
            if description:
                data["description"] = description
            if visibility:
                data["visibility"] = visibility
            post("/video/updateInfo", data, base_url=base_url, token=token)

        # 6. Trigger server-side processing / transcoding
        get("/video/rawFileUploadFinish", {"videoId": video_id}, base_url=base_url, token=token)

        print_success("Video uploaded", created)
    except APIError as e:
        print_error(e.message, e.code)
    except Exception as e:  # noqa: BLE001 - surface OSS/network errors to the user
        print_error(str(e))


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
