# Release 1.26.2 - Log files in a folder called logs

## Changed

- When the server runs as a jar (for example as a service), the log files (`niwe-erp.log`, `niwe-erp-error.log`, `niwe-access.log`) are written to a folder named **logs** next to the jar instead of the jar folder itself. The folder is created on the first start. The backups go to **backups** next to the jar (1.26.1), so the deployment folder looks like this: `niwe-erp.jar`, `logs/`, `backups/`.
- `app.logging.dir` (or the environment variable `APP_LOGGING_DIR`) still wins when you set it. Started from an IDE the logs stay in `logs/` where the server is started.
- Settings > Server logs reads the new folder. Log files written by earlier versions stay beside the jar: move them into `logs` if you want them listed.

## Technical

- `PosApplication` default `app.logging.dir` = `<jar folder>/logs`. The service account needs write permission on the jar's folder.

## Upgrade

Rebuild the backend and restart the service. Then open Settings > Server logs and check that the files appear.

## Checked / not checked

Two-line change, not compiled or run here.
