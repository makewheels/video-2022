"""Video search command."""
import click
from ..client import get, APIError
from ..output import print_json, print_table, print_error


@click.command()
@click.argument("keyword", default="")
@click.option("--category", default=None, help="Filter by category")
@click.option("--page", default=0, help="Page number (0-indexed)")
@click.option("--page-size", default=20, help="Results per page")
@click.pass_context
def search(ctx, keyword, category, page, page_size):
    """Search public videos by keyword.

    Examples:

        video-cli search 音乐

        video-cli search 教程 --category 教育

        video-cli search 游戏 --page 1
    """
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        params = {"page": page, "pageSize": page_size}
        if keyword:
            params["q"] = keyword
        if category:
            params["category"] = category
        result = get("/search", params, base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table" and result:
            videos = result.get("content", [])
            total = result.get("total", 0)
            current_page = result.get("currentPage", 0)
            total_pages = result.get("totalPages", 0)
            if isinstance(videos, list):
                rows = [
                    [v.get("id", ""), v.get("title", ""), v.get("category", ""), v.get("watchCount", 0)]
                    for v in videos
                ]
                print_table(["ID", "Title", "Category", "Views"], rows)
                click.echo(f"\nPage {current_page + 1}/{total_pages} (Total: {total})")
            else:
                print_json(result)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
