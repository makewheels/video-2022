"""Real local TCP, separate ASGI process and isolated Mongo; no production credentials."""

import http.client
import json
import os
import socket
import subprocess
import sys
import time

from test_user_mongo_reads import isolated_database as isolated_database

CHILD = """
import os
from datetime import datetime
from pymongo import MongoClient
import uvicorn
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.user_bootstrap import build_user_application
class NoMedia:
    def sign(self, key, validity_seconds):
        raise RuntimeError('Media signing is outside this fixture')
client = MongoClient(os.environ['MONGODB_URI'], serverSelectionTimeoutMS=3000, maxPoolSize=3)
try:
    app = build_user_application(lambda: client[os.environ['FIXTURE_DATABASE']], NoMedia(),
        profile='prod', local_clock=datetime.now, worker=1, datacenter=1)
    uvicorn.run(create_user_asgi(app), host='127.0.0.1', port=int(os.environ['FIXTURE_PORT']),
        log_level='critical', access_log=False, proxy_headers=False)
finally:
    client.close()
"""


def test_tcp_login_profile_and_peer_metadata_survive_real_server_boundary(isolated_database):
    database = isolated_database
    for name in ("user", "wallet", "verification_codes", "id_counters", "client", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    env = dict(os.environ, FIXTURE_DATABASE=database.name, FIXTURE_PORT=str(port), PYTHONDONTWRITEBYTECODE="1")
    process = subprocess.Popen([sys.executable, "-B", "-c", CHILD], env=env,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    connection = None
    try:
        deadline = time.monotonic() + 15
        while True:
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                    break
            except OSError:
                assert process.poll() is None, "Fixture ASGI process exited before listening"
                assert time.monotonic() < deadline, "Fixture ASGI startup timed out"
                time.sleep(0.05)
        connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)

        def get(path, headers=None):
            connection.request("GET", path, headers=headers or {})
            response = connection.getresponse()
            body = response.read()
            assert response.status == 200
            assert response.getheader("Content-Type") == "application/json"
            return json.loads(body)

        assert get("/user/requestVerificationCode?phone=00000000000")["code"] == 0
        code = database.verification_codes.find_one()["code"]
        logged_in = get("/user/submitVerificationCode?phone=00000000000&code=" + code)
        token = logged_in["data"]["token"]
        assert get("/user/getMyProfile", {"token": token})["data"]["token"] is None
        get("/client/requestClientId", {"User-Agent": "fixture-tcp", "X-Forwarded-For": "192.0.2.1"})
        assert database["client"].find_one()["ip"] == "127.0.0.1"
        assert database["client"].find_one()["userAgent"] == "fixture-tcp"
        assert database.requestLog.count_documents({}) == 4
        assert database.exceptionLog.count_documents({}) == 0
    finally:
        if connection is not None:
            connection.close()
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
