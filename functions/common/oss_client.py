"""OSS 访问封装：按任务下载原片、上传产物。凭证来自环境变量（Infisical 注入）。"""
import os

import oss2


class OssClient:
    def __init__(self, bucket_name: str, endpoint: str):
        access_key_id = os.environ.get("OSS_ACCESS_KEY_ID", "")
        access_key_secret = os.environ.get("OSS_SECRET_KEY", "")
        if not access_key_id or not access_key_secret:
            raise RuntimeError("OSS 凭证未配置（OSS_ACCESS_KEY_ID / OSS_SECRET_KEY）")
        auth = oss2.Auth(access_key_id, access_key_secret)
        # endpoint 可为内网地址，自动补协议
        if not endpoint.startswith("http"):
            endpoint = "https://" + endpoint
        self.bucket = oss2.Bucket(auth, endpoint, bucket_name)

    def download(self, key: str, local_path: str):
        oss2.resumable_download(self.bucket, key, local_path)

    def upload_file(self, local_path: str, key: str):
        self.bucket.put_object_from_file(key, local_path)

    def upload_text(self, text: str, key: str):
        self.bucket.put_object(key, text.encode("utf-8"))

    def exists(self, key: str) -> bool:
        return self.bucket.object_exists(key)

    def head(self, key: str):
        return self.bucket.head_object(key)
