"""Authentication commands."""
import click
from ..client import get, APIError
from ..config import set_token, get_token, clear_config
from ..output import print_json, print_error, print_success


@click.group()
def auth():
    """User authentication and account management."""
    pass


@auth.command()
@click.option("--phone", required=True, help="Phone number")
@click.option("--code", default=None, help="Verification code (if omitted, requests a new code)")
@click.pass_context
def login(ctx, phone, code):
    """Login with phone number and verification code.

    If --code is not provided, requests a verification code first.
    If --code is provided, submits the code and saves the token.
    """
    base_url = ctx.obj.get("base_url")
    try:
        if code is None:
            result = get("/user/requestVerificationCode", {"phone": phone}, base_url=base_url)
            print_success("Verification code sent", {"phone": phone})
        else:
            result = get("/user/submitVerificationCode", {"phone": phone, "code": code}, base_url=base_url)
            if result and result.get("token"):
                set_token(result["token"])
                print_success("Login successful", {"token": result["token"], "user": result})
            else:
                print_success("Login response", result)
    except APIError as e:
        print_error(e.message, e.code)


@auth.command()
@click.pass_context
def me(ctx):
    """Get current user information."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token") or get_token()
    if not token:
        print_error("Not logged in. Use 'video-cli auth login' first.")
    try:
        result = get("/user/getUserByToken", {"token": token}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@auth.command()
def logout():
    """Clear saved authentication token."""
    clear_config()
    print_success("Logged out successfully")
