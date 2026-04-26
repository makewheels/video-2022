"""Watch/playback commands."""
import click
from ..client import get, post, APIError
from ..output import print_json, print_error, print_success


@click.group()
def watch():
    """Video playback and watch session management."""
    pass


@watch.command()
@click.option("--watch-id", required=True, help="Watch ID")
@click.pass_context
def info(ctx, watch_id):
    """Get watch session information."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/watchController/getWatchInfo", {"watchId": watch_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command()
@click.option("--watch-id", required=True, help="Watch ID")
@click.option("--client-id", default=None, help="Client ID")
@click.option("--session-id", default=None, help="Session ID")
@click.option("--video-id", default=None, help="Video ID")
@click.pass_context
def start(ctx, watch_id, client_id, session_id, video_id):
    """Start a playback session."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {"watchId": watch_id}
        if video_id:
            data["videoId"] = video_id
        if client_id:
            data["clientId"] = client_id
        if session_id:
            data["sessionId"] = session_id
        result = post("/playback/start", data, base_url=base_url, token=token)
        print_success("Playback started", result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command()
@click.option("--playback-session-id", required=True, help="Playback session ID returned by 'watch start'")
@click.option(
    "--current-time-ms",
    "--position",
    "current_time_ms",
    default=0,
    show_default=True,
    type=int,
    help="Current playback position in milliseconds",
)
@click.option("--is-playing/--paused", "is_playing", default=True, help="Whether playback is currently active")
@click.option("--resolution", default=None, help="Current playback resolution")
@click.option("--total-play-duration-ms", default=None, type=int, help="Total active playback duration in milliseconds")
@click.pass_context
def heartbeat(ctx, playback_session_id, current_time_ms, is_playing, resolution, total_play_duration_ms):
    """Send playback heartbeat."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {
            "playbackSessionId": playback_session_id,
            "currentTimeMs": current_time_ms,
            "isPlaying": is_playing,
        }
        if resolution:
            data["resolution"] = resolution
        if total_play_duration_ms is not None:
            data["totalPlayDurationMs"] = total_play_duration_ms
        result = post("/playback/heartbeat", data, base_url=base_url, token=token)
        print_success("Playback heartbeat sent", result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command()
@click.option("--playback-session-id", required=True, help="Playback session ID returned by 'watch start'")
@click.option(
    "--current-time-ms",
    "--position",
    "current_time_ms",
    default=0,
    show_default=True,
    type=int,
    help="Final playback position in milliseconds",
)
@click.option("--total-play-duration-ms", default=None, type=int, help="Total active playback duration in milliseconds")
@click.option("--exit-type", default="CLOSE_TAB", show_default=True, help="Exit type, e.g. CLOSE_TAB or NAVIGATE_AWAY")
@click.option("--resolution", default=None, help="Final playback resolution")
@click.pass_context
def exit(ctx, playback_session_id, current_time_ms, total_play_duration_ms, exit_type, resolution):
    """Record playback session exit."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {
            "playbackSessionId": playback_session_id,
            "currentTimeMs": current_time_ms,
            "exitType": exit_type,
        }
        if total_play_duration_ms is not None:
            data["totalPlayDurationMs"] = total_play_duration_ms
        if resolution:
            data["resolution"] = resolution
        result = post("/playback/exit", data, base_url=base_url, token=token)
        print_success("Playback exited", result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command()
@click.option("--video-id", required=True, help="Video ID")
@click.option("--client-id", required=True, help="Client ID")
@click.pass_context
def progress(ctx, video_id, client_id):
    """Get saved playback progress for a video/client pair."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/progress/getProgress", {"videoId": video_id, "clientId": client_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command("history")
@click.option("--page", default=0, type=int, help="Page number (0-based)")
@click.option("--page-size", default=20, type=int, help="Items per page")
@click.pass_context
def history(ctx, page, page_size):
    """List watch history."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/watchHistory/getMyHistory", {"page": page, "pageSize": page_size}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command("clear-history")
@click.pass_context
def clear_history(ctx):
    """Clear all watch history."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/watchHistory/clear", base_url=base_url, token=token)
        print_success("Watch history cleared", result)
    except APIError as e:
        print_error(e.message, e.code)
