"""Explicit notification HTTP with the legacy optional identity; no notification generation."""

from dataclasses import dataclass

from video_serverless.business.notifications import Notifications
from video_serverless.comment_application import _row
from video_serverless.comment_json import _java_value
from video_serverless.http import Response
from video_serverless.playback_json import _spring_object
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import _integer
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError

NOTIFICATION_READ_METHODS = dict.fromkeys(("/notification/getMyNotifications", "/notification/getUnreadCount"), "GET")
NOTIFICATION_WRITE_METHODS = dict.fromkeys(("/notification/markAsRead", "/notification/markAllAsRead"), "POST")


@dataclass(frozen=True)
class NotificationApplication:
    notifications: Notifications
    cycle: RequestCycle
    reads_enabled: bool = False
    writes_enabled: bool = False

    @property
    def methods(self):
        return {**(NOTIFICATION_READ_METHODS if self.reads_enabled else {}),
                **(NOTIFICATION_WRITE_METHODS if self.writes_enabled else {})}

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        expected = self.methods.get(request.path)
        if expected is None:
            return CycleResult(Response.json({"message": "Notification route not found"}, 404), "not_started")
        if request.method != expected and not (expected == "GET" and request.method == "HEAD"):
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            data = None
            if metadata.path == "/notification/getMyNotifications":
                result = self.notifications.page(identity.user_id, _integer(metadata, "page", 0),
                                                 _integer(metadata, "pageSize", 20))
                data = {"list": [_row(row) for row in result.content], "total": result.total,
                        "totalPages": result.total_pages, "currentPage": result.current_page,
                        "pageSize": result.page_size}
            elif metadata.path == "/notification/getUnreadCount":
                data = self.notifications.unread(identity.user_id)
            elif metadata.path == "/notification/markAsRead":
                fields = dict(_spring_object(body, metadata.first_header("Content-Type")))
                value = fields.get("notificationId")
                self.notifications.mark_read(identity.user_id, None if value is None else _java_value(value))
            else:
                self.notifications.mark_all_read(identity.user_id)
            response = Response.json({"code": 0, "message": "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle)
