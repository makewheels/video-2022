# Legacy OPTIONS and CORS

Sixteen real Spring MVC/isolated Mongo samples in `cases.json` cover plain OPTIONS
and preflights on GET, POST, unrestricted heartbeat and protected profile paths.
Headers, body/status and request/exception/client/session counts are recorded.
The same Java test separately verifies client GET CORS and HEAD mapping/logging.

Java normally checks committed samples; explicit `-Dcontract.output.dir=...`
exports fixtures. Python compares actual ASGI/Mongo against them. Vary may be
represented as separate header lines or a comma-separated equivalent value.
This is not a production FC/browser proxy or all malformed-Origin certification.
