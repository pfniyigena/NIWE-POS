# Release 1.22.4 - Logs next to the jar

## What changed

When the backend runs as a jar (for example as a Windows or Linux service), the three log files are now written **in the same folder as the jar**:

- `niwe-erp.log` - the normal application log
- `niwe-erp-error.log` - warnings and errors only
- `niwe-access.log` - one line per request that changed something, failed or was slow

Older rotated files (`niwe-erp.2026-10-27.0.log.gz` and similar) are kept in the same folder.

Before this release the logs went to a `logs` folder under the folder the service was *started from*, which for a service is often `C:\Windows\System32` or `/`.

## Rules

1. Started with `java -jar niwe-pos.jar` (or as a service running the jar): logs go to the jar's folder.
2. `app.logging.dir` (or the environment variable `APP_LOGGING_DIR`) set to a path: that path wins.
3. Started from the IDE or `mvn spring-boot:run` (no jar): logs go to `logs/` as before.

The service account must be able to **write** in the jar's folder. If it cannot write there, no log files appear.

## Settings > Server logs

The page now lists only files named `niwe-*.log`, so other files that share the jar folder (the jar itself, `config/`, backups) can never be listed or downloaded through it.

## Also in this release

- Unused `import` lines removed from 43 source files (no change in behaviour; commit `8997f20`).
- `pom.xml` version 1.22.4.

## Files changed

- `backend/src/main/java/com/posapp/PosApplication.java`
- `backend/src/main/java/com/posapp/logging/ServerLogService.java`
- `backend/src/main/resources/logback-spring.xml`
- `backend/pom.xml`, `README.md`

## Upgrade

No migration. Replace the jar and restart the service. Old logs in the previous `logs` folder stay where they are; move them next to the jar if you want them to show in Settings > Server logs.

## Checked / not checked

`PosApplication` was compiled in isolation only. The full project was **not** built with Maven in the release environment (Maven Central was not reachable), so please run `mvn -q clean package` on the PC and start the jar once before rolling out.
