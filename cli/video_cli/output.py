"""Output formatting for video-cli."""
import json
import sys
import click


def print_json(data, indent=2):
    click.echo(json.dumps(data, indent=indent, ensure_ascii=False, default=str))


def print_table(headers: list[str], rows: list[list]):
    try:
        from tabulate import tabulate
        click.echo(tabulate(rows, headers=headers, tablefmt="simple"))
    except ImportError:
        print_json([dict(zip(headers, row)) for row in rows])


def print_error(message: str, code: int = 1):
    error_data = {"error": True, "message": message, "code": code}
    click.echo(json.dumps(error_data, ensure_ascii=False), err=True)
    exit_code = code if isinstance(code, int) and 0 < code < 256 else 1
    sys.exit(exit_code)


def print_success(message: str, data=None):
    result = {"success": True, "message": message}
    if data is not None:
        result["data"] = data
    print_json(result)
