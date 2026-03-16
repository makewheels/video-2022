"""Notification commands."""
import click
from ..client import get, post, APIError
from ..output import print_json, print_table, print_error, print_success


@click.group()
def notification():
    """Notification management."""
    pass


@notification.command("list")
@click.option("--page", default=0, help="Page number (0-based)")
@click.option("--page-size", default=20, help="Number of items per page")
@click.pass_context
def list_notifications(ctx, page, page_size):
    """List my notifications."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/notification/getMyNotifications",
                      {"page": page, "pageSize": page_size},
                      base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and result:
            items = result.get("list", []) if isinstance(result, dict) else result
            if isinstance(items, list):
                rows = [
                    [
                        n.get("id", ""),
                        n.get("type", ""),
                        (n.get("content", "") or "")[:40],
                        "✓" if n.get("read") else "✗",
                        n.get("createTime", ""),
                    ]
                    for n in items
                ]
                print_table(["ID", "Type", "Content", "Read", "Time"], rows)
            else:
                print_json(result)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@notification.command("read")
@click.argument("notification_id")
@click.pass_context
def read_notification(ctx, notification_id):
    """Mark a notification as read."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = post("/notification/markAsRead",
                       {"notificationId": notification_id},
                       base_url=base_url, token=token)
        print_success("通知已标记为已读", result)
    except APIError as e:
        print_error(e.message, e.code)


@notification.command("read-all")
@click.pass_context
def read_all(ctx):
    """Mark all notifications as read."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = post("/notification/markAllAsRead", {},
                       base_url=base_url, token=token)
        print_success("全部通知已标记为已读", result)
    except APIError as e:
        print_error(e.message, e.code)


@notification.command("unread-count")
@click.pass_context
def unread_count(ctx):
    """Get unread notification count."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/notification/getUnreadCount",
                      base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
