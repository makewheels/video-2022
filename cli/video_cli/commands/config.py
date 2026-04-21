"""Local CLI configuration commands."""
import click
from ..config import (
    CONFIG_FILE,
    clear_config,
    clear_token,
    get_base_url,
    get_token,
    set_base_url,
    set_token,
)
from ..output import print_json, print_success


def _mask_token(token: str | None) -> str | None:
    if not token:
        return None
    if len(token) <= 8:
        return "*" * len(token)
    return f"{token[:4]}...{token[-4:]}"


@click.group()
def config():
    """Manage local CLI configuration."""
    pass


@config.command("show")
@click.option("--show-token", is_flag=True, help="Show the full saved token")
def show_config(show_token):
    """Show the current local CLI configuration."""
    token = get_token()
    data = {
        "configFile": str(CONFIG_FILE),
        "baseUrl": get_base_url(),
        "hasToken": bool(token),
    }
    if token:
        data["token"] = token if show_token else _mask_token(token)
    print_json(data)


@config.command("set-base-url")
@click.argument("url")
def set_base_url_command(url):
    """Save a default API base URL."""
    set_base_url(url)
    print_success("Base URL saved", {"baseUrl": url})


@config.command("set-token")
@click.argument("token")
def set_token_command(token):
    """Save a default auth token."""
    set_token(token)
    print_success("Token saved", {"token": _mask_token(token)})


@config.command("clear-token")
def clear_token_command():
    """Remove only the saved auth token."""
    clear_token()
    print_success("Token cleared")


@config.command("clear")
def clear_all_config():
    """Remove the local CLI config file."""
    clear_config()
    print_success("Config cleared")
