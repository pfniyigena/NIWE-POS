# NIWE ERP, version 1.22.3

Settings hygiene: no database password or signing secret in git. Versions 1.0.0 to 1.22.2 stay the base. No migration, no code change, no screen changes.

## Why

The clean-install rehearsal for 1.22.2 found that `application.properties` and `application-niwe.properties` carried a real database password (and port 5434) in the repository, and every installation had to remember to edit a tracked file, which then shows up as a change in git.

## What changed

1. **Environment variables.** The settings that differ per installation are now read from the environment, with harmless defaults:

   | Variable | Setting | Default |
   |---|---|---|
   | `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/pos_app` (`...:5433/...` in the `niwe` profile) |
   | `DB_USER` | `spring.datasource.username` | `postgres` |
   | `DB_PASSWORD` | `spring.datasource.password` | empty |
   | `JWT_SECRET` | `app.jwt.secret` | the old placeholder text; **always set your own** |

2. **A private settings file.** Anything else (or all of it) can go in `backend/config/application.properties`. Spring Boot reads it automatically when the backend is started from the `backend` folder, and git ignores that folder.
3. **Release packages are not tracked.** `.gitignore` now ignores `niwe-*.tgz`, `_*.tar`, `_*.tgz`, `/_to_delete/`, `/Claude outputs/` and `backend/config/`. The `niwe-pos-v1.22.1.tgz` and `niwe-pos-v1.22.2.tgz` files that were committed are removed from git (the files stay on disk).

## What you need to do

- On a machine that already runs with a hand-edited `application.properties`, nothing breaks: a literal value in a file you keep locally still wins. To stop carrying the edits, move them to environment variables or to `backend/config/application.properties`.
- **The old password is still in git history** (it was committed before 1.22.3). If that password protects anything real, change it. Rewriting history is possible but not done here.
- Always set `JWT_SECRET` to a long random value on a real installation.

## Tests

The cleaned `application.properties` was used to start the backend with `DB_URL`, `DB_USER`, `DB_PASSWORD` and `JWT_SECRET` set in the environment: it connected, started, and refused a wrong password.
