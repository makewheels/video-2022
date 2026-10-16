# Legacy playback HTTP snapshots

`PlaybackJsonHttpContractTest` records 108 synthetic requests through real Spring
MVC and isolated Mongo. Start/heartbeat use Spring DTO conversion; exit uses a
String converter followed by Fastjson. Samples intentionally retain the different
syntax, scalar coercion, method/media mapping and interceptor order.

`cases.json` includes complete HTTP responses, normalized session documents and
request/exception journal counts. Generated start IDs become `generated`; dates
become `present`. Separate lifecycle tests verify date/default/update semantics.
Default Java execution rechecks committed snapshots; an explicit absolute
`-Dcontract.output.dir=...` enables regeneration for review.

Python `test_playback_http.py` compares actual ASGI and Mongo against these rows.
Only synthetic inputs and task-owned loopback databases may be used. These rows
do not certify all Fastjson extensions, arbitrary object-to-string key ordering,
UTF-16/32, production data types, mobile players or FC networking.
