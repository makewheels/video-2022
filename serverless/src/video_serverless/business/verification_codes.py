"""Legacy code issuance; no refresh, SMS delivery or application-level expiry."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.ports.verification_codes import VerificationCodes


@dataclass(frozen=True)
class CodeRequests:
    codes: VerificationCodes
    generate_code: Callable[[], str]
    clock: Callable[[], datetime]

    def request(self, phone: str) -> None:
        if self.codes.exists_for_phone(phone):
            return
        code = self.generate_code()
        self.codes.save(phone, code, self.clock())
