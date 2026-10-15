"""Signed delivery decision only; no background work, fee writes or HTTP installation."""

from dataclasses import dataclass

from video_serverless.ports.file_access import FileRedirectKeys, FileRedirectResult, FileRedirectUrls
from video_serverless.ports.playback_signatures import AccessClaim, AccessSignatures


@dataclass(frozen=True)
class FileRedirects:
    keys: FileRedirectKeys
    urls: FileRedirectUrls
    signatures: AccessSignatures

    def get(self, claim: AccessClaim, signature: str | None) -> FileRedirectResult:
        if not self.signatures.timestamp_valid(claim.timestamp):
            return FileRedirectResult(error_code=35, detail="文件访问签名已过期")
        if not self.signatures.verify(claim, signature):
            return FileRedirectResult(error_code=35)
        key = self.keys.key_for_file(claim.file_id)
        if key is None:
            return FileRedirectResult(error_code=31)
        return FileRedirectResult(url=self.urls.sign(key, 3 * 60 * 60))
