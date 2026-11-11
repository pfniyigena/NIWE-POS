# Release 1.26.1 - Backups folder next to the jar

## Fixed

- **"Unexpected error: Failed to create backup directory ./backups"** when the server runs as a service or from a folder where the service account cannot write. The backups folder used to be `./backups` in the working folder of the process. When the server is started as a jar it is now a folder named **backups** next to the jar (created if missing), the same place as the log files. Started from an IDE it is still `./backups`.
- To use another folder, set `app.backup.directory` in `application.properties`, as an environment variable (`APP_BACKUP_DIRECTORY`) or on the command line; that always wins.

## Technical

- `PosApplication` adds `app.backup.directory` to the lowest-priority defaults when running from a jar; `application.properties` no longer sets it; `BackupService` reads `${app.backup.directory:./backups}`.
- Backups already made in an old `./backups` folder are not moved: copy them into the new folder if you want them in the list.
- The service account needs write permission on the jar's folder.

## Upgrade

Rebuild the backend and restart the service. Nothing else is needed.

## Checked / not checked

The change is three lines and was not compiled or run here: after the restart press **Backup now** and check that a `.sql` file appears next to the jar.
