"""Share link commands."""
import click
from ..client import get, APIError
from ..output import print_json, print_table, print_error


@click.group()
def share():
    """Create and manage video share links."""
    pass


@share.command("create")
@click.option("--video-id", required=True, help="Video ID to create share link for")
@click.pass_context
def create_share(ctx, video_id):
    """Create a share link for a video."""
    try:
        result = get(
            "/share/create",
            {"videoId": video_id},
            base_url=ctx.obj.get("base_url"),
            token=ctx.obj.get("token"),
        )
        if ctx.obj.get("output_format") == "table":
            rows = [[result.get("shortCode"), result.get("videoId"), result.get("clickCount", 0)]]
            print_table(["Short Code", "Video ID", "Clicks"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@share.command("stats")
@click.option("--short-code", required=True, help="Share link short code")
@click.pass_context
def share_stats(ctx, short_code):
    """Get share link statistics."""
    try:
        result = get(
            "/share/stats",
            {"shortCode": short_code},
            base_url=ctx.obj.get("base_url"),
            token=ctx.obj.get("token"),
        )
        if ctx.obj.get("output_format") == "table":
            rows = [[
                result.get("shortCode"),
                result.get("videoId"),
                result.get("clickCount", 0),
                result.get("lastReferrer", ""),
                result.get("createTime", ""),
            ]]
            print_table(["Short Code", "Video ID", "Clicks", "Last Referrer", "Created"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
