"""API management commands."""
import click
from ..client import get, APIError
from ..output import print_json, print_table, print_error


@click.group()
def api():
    """API management and diagnostics."""
    pass


@api.command("rate-limit-status")
@click.pass_context
def rate_limit_status(ctx):
    """Check current API rate limit status."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/api/v1/rateLimit/status", base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and isinstance(result, dict):
            rows = [[k, v] for k, v in result.items()]
            print_table(["Field", "Value"], rows)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
