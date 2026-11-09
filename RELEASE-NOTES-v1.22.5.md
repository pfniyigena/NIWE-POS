# Release 1.22.5 - Print the list of modules as a PDF

## What changed

**Settings > Modules** has a new **Print PDF** button (administrators only, like the page itself). It opens an A4 PDF with:

- the business name, the date printed, and the license: who it is issued to, license number, issue and expiry date, and the installation id (or a line saying no license is installed);
- a summary count: how many modules are core, active, in your license but not activated, and not available;
- a table of **every module**: name, what it does, which modules it works together with, and its status (Core, Active, In your license, Locked, Requested since a date, License expired).

## Technical

- New `GET /api/modules/list-pdf` (admin only) in `ModuleController`; the document is built by the new `ModuleListPdf` with the same `PdfKit` as the other printed papers.
- Frontend: the existing `app-paper-button` on `admin/modules.component.ts`.
- No migration, no change to licensing.

## Upgrade

Replace the jar and the frontend build. Nothing to configure.

## Checked / not checked

Reviewed line by line against the signatures it uses. It was **not compiled or run** in the release environment (Maven Central and the project libraries were not reachable), so please run `mvn -q clean package`, open Settings > Modules and press **Print PDF** once before rolling out.
