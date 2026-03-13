import json
import pytest
from video_cli.output import print_json, print_success, print_error, print_table


class TestOutput:
    def test_print_json(self, capsys):
        print_json({"key": "value"})
        captured = capsys.readouterr()
        data = json.loads(captured.out)
        assert data["key"] == "value"

    def test_print_json_chinese(self, capsys):
        print_json({"title": "测试视频"})
        captured = capsys.readouterr()
        assert "测试视频" in captured.out

    def test_print_json_indent(self, capsys):
        print_json({"a": 1}, indent=4)
        captured = capsys.readouterr()
        assert "    " in captured.out

    def test_print_success_with_data(self, capsys):
        print_success("操作成功", {"id": "123"})
        captured = capsys.readouterr()
        data = json.loads(captured.out)
        assert data["success"] is True
        assert data["message"] == "操作成功"
        assert data["data"]["id"] == "123"

    def test_print_success_without_data(self, capsys):
        print_success("done")
        captured = capsys.readouterr()
        data = json.loads(captured.out)
        assert data["success"] is True
        assert data["message"] == "done"
        assert "data" not in data

    def test_print_error(self):
        with pytest.raises(SystemExit) as exc:
            print_error("出错了", code=2)
        assert exc.value.code == 2

    def test_print_error_default_code(self):
        with pytest.raises(SystemExit) as exc:
            print_error("error")
        assert exc.value.code == 1

    def test_print_table(self, capsys):
        print_table(["Name", "Age"], [["Alice", 30], ["Bob", 25]])
        captured = capsys.readouterr()
        assert "Alice" in captured.out
        assert "Bob" in captured.out
