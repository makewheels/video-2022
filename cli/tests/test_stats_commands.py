import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestStatsCommands:
    def setup_method(self):
        self.runner = CliRunner()
        self.patches = [
            patch("video_cli.client.get_token", return_value="t"),
            patch("video_cli.client.get_base_url", return_value="http://localhost:5022"),
        ]
        for p in self.patches:
            p.start()

    def teardown_method(self):
        for p in self.patches:
            p.stop()

    @responses.activate
    def test_stats_traffic(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/statistics/getTrafficConsume",
            json={"code": 0, "message": "ok", "data": {"totalBytes": 1024000}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "stats", "traffic", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["totalBytes"] == 1024000

    @responses.activate
    def test_stats_aggregate(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/statistics/aggregateTrafficData",
            json={"code": 0, "message": "ok", "data": [{"date": "2024-01-01", "traffic": 500}]},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "stats", "aggregate", "--start", "1704067200000", "--end", "1704153600000"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data[0]["date"] == "2024-01-01"

    @responses.activate
    def test_stats_aggregate_table_format(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/statistics/aggregateTrafficData",
            json={"code": 0, "message": "ok", "data": [{"date": "2024-01-01", "traffic": 500}]},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "--output", "table", "stats", "aggregate", "--start", "1704067200000", "--end", "1704153600000"])
        assert result.exit_code == 0
        assert "2024-01-01" in result.output

    @responses.activate
    def test_stats_traffic_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/statistics/getTrafficConsume",
            json={"code": 1, "message": "unauthorized", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "stats", "traffic", "--video-id", "v1"])
        assert result.exit_code != 0
