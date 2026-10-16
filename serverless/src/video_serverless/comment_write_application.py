"""Protected comment mutations with the original methods, direct errors and request lifecycle."""

from dataclasses import dataclass

from video_serverless.business.comment_writes import CommentVideoMissing, CommentWrites
from video_serverless.comment_application import _row
from video_serverless.comment_json import decode_comment
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError, _parameter

COMMENT_WRITE_METHODS = {"/comment/add": "POST", "/comment/delete": "GET", "/comment/like": "GET"}


@dataclass(frozen=True)
class CommentWriteApplication:
    writes: CommentWrites
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        expected = COMMENT_WRITE_METHODS.get(request.path)
        if expected is None:
            return CycleResult(Response.json({"message": "Comment write route not found"}, 404), "not_started")
        if request.method != expected and not (expected == "GET" and request.method == "HEAD"):
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            try:
                if metadata.path == "/comment/add":
                    fields = decode_comment(body, metadata.first_header("Content-Type"))
                    result = self.writes.add(identity.user, *fields)
                elif metadata.path == "/comment/delete":
                    result = self.writes.delete(identity.user_id, _parameter(metadata, "commentId"))
                else:
                    result = self.writes.like(identity.user_id, _parameter(metadata, "commentId"))
            except CommentVideoMissing as error:
                raise UserRequestError(22, "视频不存在", str(error)) from None
            failed = result.error_message is not None
            data = None if result.data is None else _row(result.data)
            response = Response.json({"code": 1 if failed else 0,
                                      "message": result.error_message if failed else "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
