"""Release rejection tests use disposable Git repositories and artifact fixtures."""

import stat
import subprocess
import sys
import zipfile
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "release"))
from build_release import check_host, dependency_payload  # noqa: E402
from release_support import (  # noqa: E402
    COMPONENT,
    ENTRYPOINTS,
    PREFIX,
    PYTHON_VERSION,
    REPOSITORY,
    TOOLS,
    UV_VERSION,
    WHEEL_PLATFORM,
    ReleaseError,
    canonical_zip,
    clean_head,
    dependency_inventory,
    file_hash,
    read_zip,
    safe_name,
    sha256,
    source_inputs,
    source_payload,
    temporary_output,
    write_json,
)
from verify_release import verify  # noqa: E402


def git_command(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args]).decode().strip()


@pytest.fixture
def repository(tmp_path):
    repo = tmp_path / "repo"
    repo.mkdir()
    git_command(repo, "init", "-q")
    git_command(repo, "config", "user.email", "release@example.invalid")
    git_command(repo, "config", "user.name", "Release Test")
    files = {"serverless/pyproject.toml": "[project]\n", "serverless/uv.lock": "version = 1\n"}
    files.update({"serverless/release/" + name: "# tool\n" for name in TOOLS})
    files.update({PREFIX + name: "# " + name + "\n" for name in [
        "__init__.py", "business/prototype.py", "fc_handler.py", "web.py", "adapters/asgi.py", "adapters/fc.py",
        "benchmark.py", "startup.py",
    ]})
    files.update({".env": "SECRET=never-pack", "serverless/tests/test_secret.py": "# excluded"})
    for name, data in files.items():
        path = repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data)
    git_command(repo, "add", ".")
    git_command(repo, "commit", "-qm", "test: fixture")
    return repo


@pytest.fixture
def artifact(repository, tmp_path):
    commit = clean_head(repository)
    inputs = source_inputs(repository, commit, "handler")
    payload = source_payload(inputs)
    requirements = b"pymongo==4.17.0 --hash=sha256:" + b"0" * 64 + b"\n"
    payload.update({"release/requirements.txt": requirements,
                    "pymongo-4.17.0.dist-info/METADATA": b"Name: pymongo\nVersion: 4.17.0\n"})
    directory = tmp_path / "artifact"
    directory.mkdir()
    archive = directory / "payload.zip"
    canonical_zip(payload, archive)
    (directory / "requirements.txt").write_bytes(requirements)
    manifest = {
        "schemaVersion": 1, "component": COMPONENT, "repository": REPOSITORY, "commit": commit,
        "flavor": "handler", "entrypoint": ENTRYPOINTS["handler"],
        "target": {"os": "linux", "architecture": "x86_64", "python": PYTHON_VERSION,
                   "wheelPlatform": WHEEL_PLATFORM}, "builder": {"uv": UV_VERSION},
        "archive": {"name": "payload.zip", "sha256": file_hash(archive), "size": archive.stat().st_size},
        "inputs": {name: sha256(data) for name, data in inputs.items()},
        "requirementsSha256": sha256(requirements), "files": {name: sha256(data) for name, data in payload.items()},
        "dependencies": dependency_inventory(payload),
    }
    write_json(directory / "manifest.json", manifest)
    return repository, directory, commit, manifest


def test_valid_artifact_requires_external_identity(artifact):
    repo, directory, commit, manifest = artifact
    assert verify(repo, directory, commit, manifest["archive"]["sha256"]) == manifest
    for expected_commit, digest in [("0" * 40, manifest["archive"]["sha256"]), (commit, "0" * 64)]:
        with pytest.raises(ReleaseError):
            verify(repo, directory, expected_commit, digest)


@pytest.mark.parametrize("mutation", ["tracked", "untracked"])
def test_dirty_checkout_rejected(repository, mutation):
    path = repository / (".env" if mutation == "tracked" else "untracked.txt")
    path.write_text("changed")
    with pytest.raises(ReleaseError, match="clean Git"):
        clean_head(repository)


def test_git_objects_and_flavor_allowlist(repository):
    commit = clean_head(repository)
    handler = source_payload(source_inputs(repository, commit, "handler"))
    web = source_payload(source_inputs(repository, commit, "web"))
    assert "video_serverless/fc_handler.py" in handler and "video_serverless/web.py" not in handler
    assert "video_serverless/web.py" in web and "video_serverless/fc_handler.py" not in web
    assert handler["video_serverless/business/prototype.py"] == web["video_serverless/business/prototype.py"]
    assert all(name.startswith("video_serverless/") for name in handler)
    assert not any("benchmark" in name or "startup" in name for name in handler | web)
    (repository / PREFIX / "fc_handler.py").write_text("# working tree changed")
    assert source_payload(source_inputs(repository, commit, "handler")) == handler


@pytest.mark.parametrize("kind", ["symlink", "nonpython"])
def test_source_rejects_nonregular_or_unapproved_files(repository, kind):
    path = repository / PREFIX / "unexpected.py"
    if kind == "symlink":
        path.symlink_to("__init__.py")
    else:
        path = path.with_suffix(".env")
        path.write_text("secret")
    git_command(repository, "add", ".")
    git_command(repository, "commit", "-qm", "test: invalid source")
    with pytest.raises(ReleaseError):
        source_inputs(repository, clean_head(repository), "handler")


@pytest.mark.parametrize("name", ["../secret", "/etc/passwd", "a/../b", "a\\b", "a:b", "a//b", "./a", "", "a\x00b"])
def test_archive_path_rejection(name):
    assert not safe_name(name)


@pytest.mark.parametrize("kind", ["duplicate", "symlink", "directory", "traversal", "corrupt"])
def test_zip_rejection(tmp_path, kind):
    path = tmp_path / "bad.zip"
    if kind == "corrupt":
        path.write_bytes(b"not zip")
    else:
        with zipfile.ZipFile(path, "w") as archive:
            name = "../a" if kind == "traversal" else "a/" if kind == "directory" else "a"
            info = zipfile.ZipInfo(name)
            info.external_attr = (stat.S_IFLNK | 0o777) << 16 if kind == "symlink" else 0
            archive.writestr(info, b"payload")
            if kind == "duplicate":
                with pytest.warns(UserWarning, match="Duplicate"):
                    archive.writestr("a", b"another")
    with pytest.raises(ReleaseError):
        read_zip(path)


def test_zip_is_reproducible_and_metadata_fixed(tmp_path):
    first, second = tmp_path / "first.zip", tmp_path / "second.zip"
    canonical_zip({"b": b"second", "a": b"first"}, first)
    canonical_zip({"a": b"first", "b": b"second"}, second)
    assert first.read_bytes() == second.read_bytes()
    with zipfile.ZipFile(first) as archive:
        assert archive.namelist() == ["a", "b"]
        for info in archive.infolist():
            assert info.date_time == (1980, 1, 1, 0, 0, 0)
            assert info.external_attr >> 16 == stat.S_IFREG | 0o644


@pytest.mark.parametrize("mutation", ["requirements", "dependency", "inventory", "archive", "source"])
def test_tampering_rejected(artifact, mutation):
    repo, directory, commit, manifest = artifact
    digest = manifest["archive"]["sha256"]
    if mutation == "requirements":
        data = b"attacker==1\n"
        (directory / "requirements.txt").write_bytes(data)
        manifest["requirementsSha256"] = sha256(data)
    elif mutation == "dependency":
        manifest["dependencies"][0]["version"] = "0.0.0"
    elif mutation == "inventory":
        manifest["files"].pop("video_serverless/__init__.py")
    elif mutation == "archive":
        with (directory / "payload.zip").open("ab") as stream:
            stream.write(b"tamper")
    else:
        payload = read_zip(directory / "payload.zip")
        payload["video_serverless/__init__.py"] = b"# changed source"
        canonical_zip(payload, directory / "payload.zip")
        digest = file_hash(directory / "payload.zip")
        manifest["archive"].update(sha256=digest, size=(directory / "payload.zip").stat().st_size)
        manifest["files"] = {name: sha256(data) for name, data in payload.items()}
    write_json(directory / "manifest.json", manifest)
    with pytest.raises(ReleaseError):
        verify(repo, directory, commit, digest)


@pytest.mark.parametrize("data", [b"Name: a\n", b"Name: a\nName: b\nVersion: 1\n", b"Name: a b\nVersion: 1\n"])
def test_bad_dependency_metadata(data):
    with pytest.raises(ReleaseError):
        dependency_inventory({"a-1.dist-info/METADATA": data})


def test_duplicate_normalized_dependencies():
    with pytest.raises(ReleaseError, match="Duplicate"):
        dependency_inventory({"a-1.dist-info/METADATA": b"Name: some-package\nVersion: 1\n",
                              "b-1.dist-info/METADATA": b"Name: some_package\nVersion: 1\n"})


def test_dependency_filter_and_symlink_rejection(tmp_path):
    (tmp_path / "pkg").mkdir()
    (tmp_path / "pkg" / "module.py").write_bytes(b"runtime")
    (tmp_path / "pkg" / "module.pyc").write_bytes(b"cache")
    assert dependency_payload(tmp_path) == {"pkg/module.py": b"runtime"}
    (tmp_path / "injected.pth").write_bytes(b"injection")
    with pytest.raises(ReleaseError):
        dependency_payload(tmp_path)
    (tmp_path / "injected.pth").unlink()
    (tmp_path / "link").symlink_to("pkg/module.py")
    with pytest.raises(ReleaseError):
        dependency_payload(tmp_path)


def test_output_must_be_new_temp_directory_outside_repo(repository, tmp_path):
    temporary_output(tmp_path / "new-output", repository)
    for path in [repository / "build", tmp_path, Path.home() / "release-output"]:
        with pytest.raises(ReleaseError):
            temporary_output(path, repository)


def test_host_gate_cannot_build_on_macos_or_windows(monkeypatch):
    monkeypatch.setattr(sys, "platform", "darwin")
    with pytest.raises(ReleaseError, match="Linux"):
        check_host()


def test_invalid_manifest_cli_rejects_without_traceback(artifact):
    repo, directory, commit, manifest = artifact
    (directory / "manifest.json").write_text("[]")
    tool = Path(__file__).resolve().parents[1] / "release" / "verify_release.py"
    result = subprocess.run([sys.executable, "-B", str(tool), "--repo", str(repo), "--directory", str(directory),
                             "--expected-commit", commit, "--expected-sha256", manifest["archive"]["sha256"]],
                            capture_output=True, text=True)
    assert result.returncode == 1 and "Verification rejected:" in result.stderr
    assert "Traceback" not in result.stderr
