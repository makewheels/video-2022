"""Playlist management commands."""
import click
from ..client import get, post, APIError
from ..output import print_json, print_table, print_error, print_success


@click.group()
def playlist():
    """Playlist management."""
    pass


@playlist.command()
@click.option("--title", required=True, help="Playlist title")
@click.option("--description", default="", help="Playlist description")
@click.option("--visibility", default="PUBLIC", type=click.Choice(["PUBLIC", "PRIVATE"]), help="Visibility")
@click.pass_context
def create(ctx, title, description, visibility):
    """Create a new playlist."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {"title": title, "description": description, "visibility": visibility}
        result = post("/playlist/createPlaylist", data, base_url=base_url, token=token)
        print_success("Playlist created", result)
    except APIError as e:
        print_error(e.message, e.code)


@playlist.command("list")
@click.option("--skip", default=0, help="Number of items to skip")
@click.option("--limit", default=20, help="Number of items to return")
@click.pass_context
def list_playlists(ctx, skip, limit):
    """List my playlists."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/playlist/getMyPlaylistByPage", {"skip": skip, "limit": limit}, base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and isinstance(result, list):
            rows = [[p.get("id", ""), p.get("title", ""), p.get("videoCount", 0)] for p in result]
            print_table(["ID", "Title", "Videos"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@playlist.command()
@click.option("--id", "playlist_id", required=True, help="Playlist ID")
@click.option("--show-videos/--no-videos", default=True, help="Include video list")
@click.pass_context
def detail(ctx, playlist_id, show_videos):
    """Get playlist details."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/playlist/getPlaylistById", {"playlistId": playlist_id, "showVideoList": show_videos}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@playlist.command("add-item")
@click.option("--playlist-id", required=True, help="Playlist ID")
@click.option("--video-id", required=True, help="Video ID to add")
@click.pass_context
def add_item(ctx, playlist_id, video_id):
    """Add a video to a playlist."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = post("/playlist/addPlaylistItem", {"playlistId": playlist_id, "videoId": video_id}, base_url=base_url, token=token)
        print_success("Video added to playlist", result)
    except APIError as e:
        print_error(e.message, e.code)


@playlist.command("delete-item")
@click.option("--playlist-id", required=True, help="Playlist ID")
@click.option("--video-id", required=True, help="Video ID to remove")
@click.pass_context
def delete_item(ctx, playlist_id, video_id):
    """Remove a video from a playlist."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = post("/playlist/deletePlaylistItem", {"playlistId": playlist_id, "videoId": video_id}, base_url=base_url, token=token)
        print_success("Video removed from playlist", result)
    except APIError as e:
        print_error(e.message, e.code)


@playlist.command()
@click.option("--id", "playlist_id", required=True, help="Playlist ID")
@click.pass_context
def delete(ctx, playlist_id):
    """Delete a playlist."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/playlist/deletePlaylist", {"playlistId": playlist_id}, base_url=base_url, token=token)
        print_success("Playlist deleted", result)
    except APIError as e:
        print_error(e.message, e.code)


@playlist.command()
@click.option("--id", "playlist_id", required=True, help="Playlist ID")
@click.pass_context
def recover(ctx, playlist_id):
    """Recover a deleted playlist."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/playlist/recoverPlaylist", {"playlistId": playlist_id}, base_url=base_url, token=token)
        print_success("Playlist recovered", result)
    except APIError as e:
        print_error(e.message, e.code)


@playlist.command()
@click.option("--title", required=True, help="New title")
@click.option("--id", "playlist_id", required=True, help="Playlist ID")
@click.option("--description", default=None, help="New description")
@click.pass_context
def update(ctx, title, playlist_id, description):
    """Update playlist metadata."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {"playlistId": playlist_id, "title": title}
        if description is not None:
            data["description"] = description
        result = post("/playlist/updatePlaylist", data, base_url=base_url, token=token)
        print_success("Playlist updated", result)
    except APIError as e:
        print_error(e.message, e.code)
