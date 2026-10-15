"""Legacy login ordering; failures propagate, and no transaction is implied."""

from dataclasses import dataclass, replace
from datetime import datetime
from typing import Callable

from video_serverless.ports.login import LoginResult, LoginStore
from video_serverless.ports.users import UserSnapshot


@dataclass(frozen=True)
class Login:
    store: LoginStore
    user_id: Callable[[], str]
    token: Callable[[], str]
    clock: Callable[[], datetime]
    production: bool

    def submit(self, phone: str, code: str) -> LoginResult:
        stored = self.store.code_for_phone(phone)
        if stored is None:
            return LoginResult(error_code=12)
        if stored != code and (self.production or code != "111"):
            return LoginResult(error_code=11)
        # Preserve delete -> user save -> wallet ordering, including failure boundaries.
        self.store.remove_codes(phone)
        user = self.store.user_for_phone(phone)
        if user is None:
            created = self.clock()
            user = UserSnapshot(id=self.user_id(), phone=phone, create_time=created,
                                update_time=created, subscriber_count=0, video_count=0)
        user = replace(user, token=self.token())
        self.store.save_user(user)
        self.store.ensure_wallet(user.id, self.clock())
        return LoginResult(user=user)
