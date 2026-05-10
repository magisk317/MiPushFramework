# protocol

Parallel protocol/runtime module retained for frozen wire-source work. The packaged runtime path
currently uses `pinned`; this module must still behave like generated or frozen code.

Examples:
- `org.apache.thrift.*`
- `com.google.protobuf.micro.*`
- `com.xiaomi.xmpush.thrift.*`
- `com.xiaomi.push.protobuf.*`
- `com.xiaomi.push.thrift.*`

Rules:
- No business-level refactors.
- Keep wire shape, field ordering, and compatibility semantics stable.
- If schema/codegen can be restored later, this module should become generated rather than hand-edited.
