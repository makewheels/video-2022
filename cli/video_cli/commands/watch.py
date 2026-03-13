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
@click.pass_context
def start(ctx, watch_id, client_id, session_id):
    """Start a playback session."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {"watchId": watch_id}
        if client_id:
            data["clientId"] = client_id
        if session_id:
            data["sessionId"] = session_id
        result = post("/playback/start", data, base_url=base_url, token=token)
        print_success("Playback started", result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command()
@click.option("--watch-id", required=True, help="Watch ID")
@click.option("--position", default=0, type=int, help="Current playback position (ms)")
@click.pass_context
def heartbeat(ctx, watch_id, position):
    """Send playback heartbeat."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {"watchId": watch_id, "position": position}
        result = post("/playback/heartbeat", data, base_url=base_url, token=token)
        print_success("Heartbeat sent", result)
    except APIError as e:
        print_error(e.message, e.code)


@watch.command()
@click.option("--watch-id", required=True, help="Watch ID")
@click.pass_context
def progress(ctx, watch_id):
    """Get playback progress."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/progress/getProgress", {"watchId": watch_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
