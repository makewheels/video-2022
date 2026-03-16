"""video-cli: CLI tool for video-2022 platform."""
import click
from . import __version__
from .commands import auth, video, comment, like, playlist, youtube, stats, watch, search, api, developer, notification


@click.group()
@click.option("--base-url", envvar="VIDEO_CLI_BASE_URL", help="API base URL (default: http://localhost:5022)")
@click.option("--token", envvar="VIDEO_CLI_TOKEN", help="Auth token (overrides saved token)")
@click.option("--output", "output_format", type=click.Choice(["json", "table"]), default="json", help="Output format")
@click.version_option(__version__, prog_name="video-cli")
@click.pass_context
def cli(ctx, base_url, token, output_format):
    """video-cli — Agent-friendly CLI for video-2022 platform.

    Manage videos, users, comments, playlists, and more from the command line.
    All commands output JSON by default for easy integration with AI agents and scripts.
    """
    ctx.ensure_object(dict)
    ctx.obj["base_url"] = base_url
    ctx.obj["token"] = token
    ctx.obj["output_format"] = output_format


cli.add_command(auth.auth)
cli.add_command(video.video)
cli.add_command(comment.comment)
cli.add_command(like.like_group)
cli.add_command(playlist.playlist)
cli.add_command(youtube.youtube)
cli.add_command(stats.stats)
cli.add_command(watch.watch)
cli.add_command(search.search)
cli.add_command(api.api)
cli.add_command(developer.developer)
cli.add_command(notification.notification)


if __name__ == "__main__":
    cli()
