# elasticsearch-script-velocity

Elasticsearch script plugin that adds [Velocity](https://velocity.apache.org/) as a search-template scripting language (`lang: "velocity"`), providing conditionals/loops that mustache lacks.

## Layout

- `src/main/java/me/ahoo/elasticsearch/script/velocity/` — `VelocityPlugin` (ScriptPlugin entry + node settings), `VelocityScriptEngine` (Velocity runtime config, parses each script into its own `Template`), `VelocityExecutableScript` (render at search time, size-limited output), `RenderScope` (per-render thread-local state), `LoopLimitingUberspector` (`SecureUberspector` + `#foreach` budget), `EncodingReferenceInsertionHandler` + `ReferenceEncoder` (output encoding per `content_type`)
- `src/test/resources/product_search.vm` — reference search template used by tests
- `src/jmh/` — JMH benchmark (reuses the test class)
- `e2e/run_e2e.py` — end-to-end tests: installs the plugin zip into the official Elasticsearch Docker image (version from `gradle.properties`) and exercises it over REST. The `product-search` template it stores is read from `README.md`'s "Store the template" example, and `README.zh-CN.md` must carry the identical template, so README edits are tested too.
- `.github/workflows/` — `integration-test.yml` (job `Test`: `./gradlew clean check jmhClasses bundlePlugin` → `E2E`: `e2e/run_e2e.py` on that bundle, and `Codecov`: uploads its JaCoCo report), `release.yml` (tag check → build → e2e → upload assets), `renovate.yml`

## Build & Test

```bash
./gradlew test                                   # unit tests (JUnit 5)
./gradlew test --tests "me.ahoo.elasticsearch.script.velocity.VelocityScriptEngineTest"
./gradlew clean check                            # full gate incl. jarHell + license headers (what CI runs)
./gradlew bundlePlugin                           # build/distributions/elasticsearch-script-velocity-<version>.zip
./gradlew jmh -PjmhIncludes=VelocityScriptEngineBenchmark   # optional: -PjmhThreads=N -PjmhMode=thrpt
python3 e2e/run_e2e.py                           # after bundlePlugin; needs Docker (--keep leaves the node running)
```

Java 21 toolchain (Elasticsearch 9.x requires JVM 21+). Build uses Elasticsearch `build-tools` (`elasticsearch.esplugin` Gradle plugin) declared in `settings.gradle.kts` buildscript.

## Version & Release Convention

- Project `version = elasticVersion-pluginVersion`, both from `gradle.properties` (e.g. `8.19.19-1.0.1`)
- Release tag MUST be `v{elasticVersion}-{pluginVersion}` (e.g. `v8.19.19-1.0.1`) — the Release workflow fails on mismatch, then runs `check bundlePlugin` and the e2e tests, and uploads the zip to the release automatically (`release: published` trigger). Do not upload assets manually.

## Architecture Rules & Gotchas

- `org.elasticsearch:elasticsearch` is `compileOnly` (provided by the ES node at runtime). Velocity (with its transitive `commons-lang3` and `slf4j-api`) is the only bundled runtime dependency — keep it that way (jarHell gates duplicate classes).
- **Security-sensitive**: the engine's uberspector (`LoopLimitingUberspector`) extends Velocity's `SecureUberspector` so templates cannot reach `Class`/`ClassLoader`/`Runtime`/`System` via reflection chains. Do not remove or downgrade it; read the Security section in `README.md`/`README.zh-CN.md` before changing engine files.
- No Java SecurityManager: since ES 8.18 (and on all of 9.x) entitlements replace it, so the plugin ships no `plugin-security.policy` (the installer only warns about one) and uses no `AccessController.doPrivileged`. Velocity needs no entitlements (no file/network/process access); add an `entitlement-policy.yaml` only if that changes.
- Scripts are parsed straight from source into their own `Template` (`RuntimeInstance.parse`); never go back to a shared resource repository (the old JVM-global `StringResourceLoader` repo raced on concurrent compiles of one name and let `#parse`/`#include` read other scripts). The only resource loader is a string loader with a private, empty repository — don't add a file loader.
- `velocimacro.inline.local_scope=true` keeps `#macro` definitions per template. Velocity's default is one engine-wide namespace where the first definition wins (templates sharing a macro name, or an updated stored script, render a stale body) — keep it.
- Output encoding mirrors mustache's `content_type` handling (JSON default, `text/plain` raw). `EncodingReferenceInsertionHandler` encodes only what is written to the final output writer, so string-literal interpolation isn't double-encoded. It is registered **engine-wide**, not via an `EventCartridge` attached to the context: a context cartridge without a method-exception handler makes Velocity swallow template method exceptions.
- Render limits (`velocity.max_output_size_bytes`, `velocity.max_loop_iterations`) must fail the render, never truncate (Velocity's own `directive.foreach.max_loops` silently truncates — don't use it).
- Catch `StackOverflowError` from parse/render and convert it to a `ScriptException` **without** the Error in the cause chain: ES halts the node when it finds an `Error` anywhere in a cause chain.
- `VelocityContext(Map)` holds the passed map **by reference**; the defensive deep copy of the params' Map/List structure, made per `execute()`, is intentional (templates can `#set`, nested maps included, and must not mutate caller params or leak into the next run). Do not "simplify" it away.
- `ScriptException`'s scriptStack must stay empty (mustache-plugin convention); the Java stack belongs on the cause.
- Main sources need the Apache-2.0 license header (esplugin `licenseHeaders` check); test sources don't require it.

## Conventions

- Conventional commits (`fix:`, `test:`, `ci:`, `chore(deps):` ...); work via short-lived branch → PR → squash merge (Renovate handles dependency updates — don't bump deps manually).
- `gradle/libs.versions.toml` is the version catalog; keep it free of unused entries.
- READMEs are bilingual (`README.md` en / `README.zh-CN.md` zh) — update both together.
