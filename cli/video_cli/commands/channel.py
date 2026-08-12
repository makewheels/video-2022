"""Channel and subscription commands."""
import click
from ..client import get, APIError
from ..output import print_json, print_table, print_error, print_success


@click.group()
def channel():
    """Channel info and subscription management."""
    pass


@channel.command()
@click.option("--user-id", "channel_user_id", required=True, help="Channel owner user ID")
@click.pass_context
def subscribe(ctx, channel_user_id):
    """Subscribe to a channel."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/subscription/subscribe", {"channelUserId": channel_user_id}, base_url=base_url, token=token)
        print_success("Channel subscribed", result)
    except APIError as e:
        print_error(e.message, e.code)


@channel.command()
@click.option("--user-id", "channel_user_id", required=True, help="Channel owner user ID")
@click.pass_context
def unsubscribe(ctx, channel_user_id):
    """Unsubscribe from a channel."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/subscription/unsubscribe", {"channelUserId": channel_user_id}, base_url=base_url, token=token)
        print_success("Channel unsubscribed", result)
    except APIError as e:
        print_error(e.message, e.code)


@channel.command("subscriptions")
@click.option("--skip", default=0, help="Number of items to skip")
@click.option("--limit", default=20, help="Number of items to return")
@click.pass_context
def my_subscriptions(ctx, skip, limit):
    """List my subscribed channel user IDs."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/subscription/getMySubscriptions", {"skip": skip, "limit": limit}, base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and isinstance(result, list):
            rows = [[user_id] for user_id in result]
            print_table(["Channel User ID"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@channel.command("get")
@click.option("--user-id", "channel_user_id", required=True, help="Channel owner user ID")
@click.pass_context
def get_channel(ctx, channel_user_id):
    """Get channel info (nickname, avatar, subscriber/video counts)."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/user/getChannel", {"userId": channel_user_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
