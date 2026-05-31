"""Local CLI configuration commands."""
import click
from ..config import (
    CONFIG_FILE,
    clear_config,
    clear_token,
    get_base_url,
    get_current_profile,
    get_token,
    list_profiles,
    set_base_url,
    set_token,
    use_profile,
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
    """Manage local CLI configuration (supports multiple profiles)."""
    pass


@config.command("show")
@click.option("--show-token", is_flag=True, help="Show the full saved token")
def show_config(show_token):
    """Show the current local CLI configuration."""
    token = get_token()
    data = {
        "configFile": str(CONFIG_FILE),
        "currentProfile": get_current_profile(),
        "baseUrl": get_base_url(),
        "hasToken": bool(token),
    }
    if token:
        data["token"] = token if show_token else _mask_token(token)
    profiles = {}
    for name, prof in list_profiles().items():
        profiles[name] = {
            "baseUrl": prof.get("base_url"),
            "token": (prof.get("token") if show_token else _mask_token(prof.get("token"))),
        }
    if profiles:
        data["profiles"] = profiles
    print_json(data)


@config.command("list")
def list_config():
    """List all configured profiles."""
    current = get_current_profile()
    rows = {}
    for name, prof in list_profiles().items():
        rows[name] = {
            "current": name == current,
            "baseUrl": prof.get("base_url"),
            "hasToken": bool(prof.get("token")),
        }
    print_json({"currentProfile": current, "profiles": rows})


@config.command("use")
@click.argument("name")
@click.option("--base-url", "base_url", default=None, help="Set/override this profile's base URL")
def use_config(name, base_url):
    """Switch the active profile (creating it if needed).

    Known names 'local' and 'prod' get their default URL automatically.
    """
    use_profile(name, base_url=base_url)
    print_success("Active profile set", {"profile": name, "baseUrl": get_base_url(name)})


@config.command("set-base-url")
@click.argument("url")
@click.option("--profile", "profile", default=None, help="Target profile (default: current)")
def set_base_url_command(url, profile):
    """Save a default API base URL (for the current or given profile)."""
    if profile:
        set_base_url(url, profile=profile)
    else:
        set_base_url(url)
    print_success("Base URL saved", {"baseUrl": url, "profile": profile or get_current_profile()})


@config.command("set-token")
@click.argument("token")
@click.option("--profile", "profile", default=None, help="Target profile (default: current)")
def set_token_command(token, profile):
    """Save a default auth token (for the current or given profile)."""
    if profile:
        set_token(token, profile=profile)
    else:
        set_token(token)
    print_success("Token saved", {"token": _mask_token(token), "profile": profile or get_current_profile()})


@config.command("clear-token")
@click.option("--profile", "profile", default=None, help="Target profile (default: current)")
def clear_token_command(profile):
    """Remove only the saved auth token (for the current or given profile)."""
    if profile:
        clear_token(profile=profile)
    else:
        clear_token()
    print_success("Token cleared")


@config.command("clear")
def clear_all_config():
    """Remove the local CLI config file (all profiles)."""
    clear_config()
    print_success("Config cleared")
