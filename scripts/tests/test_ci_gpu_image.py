"""Safety and diff-range tests for skipping expensive unrelated prototype image builds."""

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.ci_gpu_image import required


class GpuImageChangesTest(unittest.TestCase):
    def decision(self, event, paths):
        with patch("scripts.ci_gpu_image.subprocess.run") as run:
            run.return_value.stdout = "\0".join(paths).encode() + b"\0"
            result = required(event, "base", "head")
            return result, run.call_args.args[0]

    def test_unrelated_business_and_documentation_do_not_build(self):
        result, command = self.decision("pull_request", ["server/video/Service.java", "docs/design/cloud-transcode.md"])
        self.assertFalse(result)
        self.assertEqual(command, ["git", "diff", "--name-only", "--no-renames", "-z", "base...head"])

    def test_push_compares_actual_before_and_after(self):
        result, command = self.decision("push", [])
        self.assertFalse(result)
        self.assertEqual(command[-2:], ["base", "head"])

    def test_runtime_dependencies_dockerfile_and_workflow_require_build(self):
        for path in ["transcode-worker/Dockerfile", "transcode-worker/uv.lock", "transcode-worker/tests/test_media.py",
                     "transcode-worker/video_transcode_worker/media.py", ".github/workflows/ci.yml",
                     "scripts/ci_gpu_image.py", "scripts/tests/test_ci_gpu_image.py"]:
            with self.subTest(path=path):
                self.assertTrue(self.decision("pull_request", [path])[0])

    def test_manual_first_push_and_unknown_base_require_build_without_diff(self):
        with patch("scripts.ci_gpu_image.subprocess.run") as run:
            for event, base, head in [("workflow_dispatch", "b", "h"), ("push", "0" * 40, "h"),
                                      ("push", None, "h"), ("push", "b", None)]:
                self.assertTrue(required(event, base, head))
            run.assert_not_called()

    def test_comparison_failure_cannot_skip_image_validation(self):
        for failure in [OSError("fixture git unavailable"), subprocess.CalledProcessError(1, ["git"])]:
            with patch("scripts.ci_gpu_image.subprocess.run", side_effect=failure):
                self.assertTrue(required("pull_request", "base", "head"))


class GpuImageRealGitTest(unittest.TestCase):
    def test_pr_uses_merge_base_and_renames_out_of_worker_still_require_build(self):
        with tempfile.TemporaryDirectory(prefix="video-ci-diff-") as directory:
            root = Path(directory)

            def git(*args):
                output = subprocess.check_output(["git", "-C", directory, *args], stderr=subprocess.DEVNULL)
                return output.decode().strip()

            git("init", "-q")
            git("config", "user.name", "Test Fixture")
            git("config", "user.email", "fixture@example.invalid")
            (root / "transcode-worker").mkdir()
            (root / "transcode-worker/Dockerfile").write_text("FROM fixture\n")
            git("add", ".")
            git("commit", "-qm", "initial fixture")
            initial = git("rev-parse", "HEAD")
            git("checkout", "-qb", "base")
            (root / "transcode-worker/Dockerfile").write_text("FROM changed-base\n")
            git("commit", "-qam", "base changes worker")
            base = git("rev-parse", "HEAD")
            git("checkout", "-qb", "feature", initial)
            (root / "README.md").write_text("unrelated feature\n")
            git("add", ".")
            git("commit", "-qm", "only documentation")
            head = git("rev-parse", "HEAD")
            real_run = subprocess.run
            with patch("scripts.ci_gpu_image.subprocess.run",
                       side_effect=lambda *args, **kwargs: real_run(*args, cwd=directory, **kwargs)):
                self.assertFalse(required("pull_request", base, head))
            git("mv", "transcode-worker/Dockerfile", "old-Dockerfile")
            git("commit", "-qm", "move worker file out")
            renamed = git("rev-parse", "HEAD")
            with patch("scripts.ci_gpu_image.subprocess.run",
                       side_effect=lambda *args, **kwargs: real_run(*args, cwd=directory, **kwargs)):
                self.assertTrue(required("push", head, renamed))


if __name__ == "__main__":
    unittest.main()
