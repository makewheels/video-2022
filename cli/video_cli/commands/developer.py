"""Developer app management commands."""
import click
from ..client import get, post, APIError
from ..output import print_json, print_table, print_error, print_success


@click.group()
def developer():
    """Developer app management and JWT tokens."""
    pass


@developer.command("create-app")
@click.argument("name")
@click.pass_context
def create_app(ctx, name):
    """Create a new developer app."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = post("/developer/app/create", {"appName": name},
                       base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and result:
            rows = [[
                result.get("appId", ""),
                result.get("appName", ""),
                result.get("appSecret", ""),
                result.get("status", ""),
            ]]
            print_table(["App ID", "Name", "Secret", "Status"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@developer.command("list-apps")
@click.pass_context
def list_apps(ctx):
    """List all developer apps."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/developer/app/list", base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and result:
            apps = result if isinstance(result, list) else []
            rows = [[
                a.get("appId", ""),
                a.get("appName", ""),
                a.get("status", ""),
                a.get("webhookUrl", ""),
            ] for a in apps]
            print_table(["App ID", "Name", "Status", "Webhook URL"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@developer.command("create-token")
@click.argument("app_id")
@click.argument("app_secret")
@click.pass_context
def create_token(ctx, app_id, app_secret):
    """Get a JWT token using app credentials."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = post("/developer/token/create",
                       {"appId": app_id, "appSecret": app_secret},
                       base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
