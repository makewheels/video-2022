# Legacy request-byte encodings

92 synthetic byte-level requests through real old MVC and isolated Mongo are
recorded in `cases.json`. Base64 is the exact request body, not a reconstructed
Unicode string. Four paths cover profile, heartbeat, start and String/Fastjson exit.

The matrix includes UTF-8, UTF-16/32 little/big endian, with/without BOM, declared
and absent charset, ISO-8859-1, and malformed UTF-8. Complete HTTP responses,
selected persisted text and journal counts are fixed. Generated start IDs are
normalized. Default Java execution checks committed data; regeneration requires
explicit `-Dcontract.output.dir=...` and a task-owned loopback Mongo database.

Python uses the same bytes through actual ASGI/Mongo. Stream JSON detection and
String-converter replacement behavior intentionally differ. These cases do not
certify every charset, malformed surrogate sequence or real FC envelope/proxy.
