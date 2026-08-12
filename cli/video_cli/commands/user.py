"""User profile commands."""
import click
from ..client import get, post, APIError
from ..output import print_json, print_error, print_success


@click.group()
def user():
    """User profile management."""
    pass


@user.command()
@click.pass_context
def profile(ctx):
    """Get my profile (nickname, bio, avatar, subscriber/video counts)."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/user/getMyProfile", base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@user.command("update-profile")
@click.option("--nickname", default=None, help="New nickname (max 30 chars)")
@click.option("--bio", default=None, help="New bio (max 200 chars)")
@click.pass_context
def update_profile(ctx, nickname, bio):
    """Update my profile. At least one of --nickname/--bio is required."""
    if nickname is None and bio is None:
        print_error("Nothing to update: provide --nickname and/or --bio")
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        data = {}
        if nickname is not None:
            data["nickname"] = nickname
        if bio is not None:
            data["bio"] = bio
        result = post("/user/updateProfile", data, base_url=base_url, token=token)
        print_success("Profile updated", result)
    except APIError as e:
        print_error(e.message, e.code)
