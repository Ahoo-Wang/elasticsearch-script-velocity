# elasticsearch-script-velocity

Elasticsearch script plugin that adds [Velocity](https://velocity.apache.org/) as a search-template scripting language (`lang: "velocity"`), providing conditionals/loops that mustache lacks.

## Layout

- `src/main/java/me/ahoo/elasticsearch/script/velocity/` — 3 classes: `VelocityPlugin` (ScriptPlugin entry), `VelocityScriptEngine` (compile via Velocity StringResourceLoader), `VelocityExecutableScript` (render at search time)
- `src/main/plugin-metadata/plugin-security.policy` — permissions granted to the plugin under the ES security manager
- `src/test/resources/product_search.vm` — reference search template used by tests
- `src/jmh/` — JMH benchmark (reuses the test class)
- `.github/workflows/` — `integration-test.yml` (`gradle clean check`), `codecov.yml`, `release.yml` (auto assets)

## Build & Test

```bash
./gradlew test                                   # unit tests (JUnit 5)
./gradlew test --tests "me.ahoo.elasticsearch.script.velocity.VelocityScriptEngineTest"
./gradlew clean check                            # full gate incl. jarHell + license headers (what CI runs)
./gradlew bundlePlugin                           # build/distributions/elasticsearch-script-velocity-<version>.zip
./gradlew jmh -PjmhIncludes=VelocityScriptEngineBenchmark   # optional: -PjmhThreads=N -PjmhMode=thrpt
```

Java 17 toolchain. Build uses Elasticsearch `build-tools` (`elasticsearch.esplugin` Gradle plugin) declared in `settings.gradle.kts` buildscript.

## Version & Release Convention

- Project `version = elasticVersion-pluginVersion`, both from `gradle.properties` (e.g. `8.19.19-1.0.1`)
- Release tag MUST be `v{elasticVersion}-{pluginVersion}` (e.g. `v8.19.19-1.0.1`) — the Release workflow fails on mismatch, then runs `check bundlePlugin` and uploads the zip to the release automatically (`release: published` trigger). Do not upload assets manually.

## Architecture Rules & Gotchas

- `org.elasticsearch:elasticsearch` is `compileOnly` (provided by the ES node at runtime). Velocity is the only bundled runtime dependency — keep it that way (jarHell gates duplicate classes).
- **Security-sensitive**: the engine configures Velocity's `SecureUberspector` so templates cannot reach `Class`/`ClassLoader`/`Runtime`/`System` via reflection chains. Do not remove or downgrade it; read the Security section in `README.md`/`README.zh-CN.md` before changing engine or security-policy files. Execution runs inside `AccessController.doPrivileged`, so template-side introspection runs with the plugin's granted permissions.
- `StringResourceLoader`'s repository is a JVM-global static singleton. `ensureResource` compares script body to pick up stored-script updates (the loader does not cache parsed templates by default) — keep that check.
- `VelocityContext(Map)` holds the passed map **by reference**; the defensive `new HashMap<>(params)` copy in `VelocityExecutableScript` is intentional (templates can `#set` and must not mutate caller params). Do not "simplify" it away.
- `ScriptException`'s scriptStack must stay empty (mustache-plugin convention); the Java stack belongs on the cause.
- Main sources need the Apache-2.0 license header (esplugin `licenseHeaders` check); test sources don't require it.

## Conventions

- Conventional commits (`fix:`, `test:`, `ci:`, `chore(deps):` ...); work via short-lived branch → PR → squash merge (Renovate handles dependency updates — don't bump deps manually).
- `gradle/libs.versions.toml` is the version catalog; keep it free of unused entries.
- READMEs are bilingual (`README.md` en / `README.zh-CN.md` zh) — update both together.
