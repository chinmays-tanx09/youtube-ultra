# Module tests

Plain-JVM tests for "download with my account" (`Account`, `PlayerProto`, `Resolve`, stream selection in `Formats`).
`stubs/` mimics the few public methods of the app's extension that the module calls; `fixtures/` holds two saved
player responses (protobuf) with **fake** URLs, ids and tokens: one signed-in/playable, one members-only/locked.

```sh
export ANDROID_HOME=$HOME/Android/Sdk
bash scripts/fetch-deps.sh          # or at least .deps/libs
bash module/test/run.sh
```
