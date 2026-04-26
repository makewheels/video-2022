"""Statistics commands."""
import click
from ..client import get, APIError
from ..output import print_json, print_table, print_error


def _print_aggregate_table(result):
    if isinstance(result, list):
        rows = [[item.get("date", ""), item.get("traffic", 0)] for item in result]
        print_table(["Date", "Traffic"], rows)
        return

    if not isinstance(result, dict):
        print_json(result)
        return

    x_axis = result.get("xAxis", {}).get("data", [])
    y_axis = result.get("yAxis", {}).get("data", [])
    series = result.get("series", {}).get("data", [])
    rows = []
    for index, date in enumerate(x_axis):
        human_traffic = y_axis[index] if index < len(y_axis) else ""
        bytes_traffic = series[index] if index < len(series) else ""
        rows.append([date, human_traffic, bytes_traffic])

    if rows:
        print_table(["Date", "Traffic", "Bytes"], rows)
    else:
        print_json(result)


@click.group()
def stats():
    """Traffic and usage statistics."""
    pass


@stats.command()
@click.option("--video-id", required=True, help="Video ID")
@click.pass_context
def traffic(ctx, video_id):
    """Get traffic consumption for a video."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/statistics/getTrafficConsume", {"videoId": video_id}, base_url=base_url, token=token)
        print_json(result)
    except APIError as e:
        print_error(e.message, e.code)


@stats.command()
@click.option("--start", "start_time", required=True, help="Start time (epoch ms)")
@click.option("--end", "end_time", required=True, help="End time (epoch ms)")
@click.pass_context
def aggregate(ctx, start_time, end_time):
    """Get aggregated traffic data by day."""
    base_url = ctx.obj.get("base_url")
    token = ctx.obj.get("token")
    try:
        result = get("/statistics/aggregateTrafficData", {"startTime": start_time, "endTime": end_time}, base_url=base_url, token=token)
        if ctx.obj.get("output_format") == "table":
            _print_aggregate_table(result)
        else:
            print_json(result)
    except APIError as e:
        print_error(e.message, e.code)
