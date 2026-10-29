# NIWE ERP, version 1.15.0

Work Orders: printable documents, estimate approval, board and calendar, planned maintenance, notes, photos and files, history, timer and customer e-mail. Versions 1.0.0 to 1.14.0 stay the base. One migration (V54).

## What changed

1. **Printable documents.** On a job: *Print job card* (for the technician: what to do, checklist boxes, parts, notes, space for a signature; no prices), *Print estimate* (customer jobs: what the job will cost, with a place for the customer to approve or the name of who approved) and, once a customer job is completed, *Print completion note* (work done, steps, charges, total, "Received by"). They open as A4 PDFs in a new tab. The technician the job is assigned to can print the job card; the estimate and completion note need Operations access (view or manage).
2. **Estimate and customer approval.** The estimate is worked out from the job: the fixed price, or the estimated hours times the hourly rate, plus parts that are not included in a fixed price and the outside costs that are charged. *Send estimate* records the date and the total the customer was shown and can e-mail the PDF to the customer (with an optional message). The customer's answer is recorded with *Customer approved* (a name is required, plus how it was agreed) or *Customer declined*; sending again starts a new round.
   - Per job, a manager switches *The customer must approve the estimate before work starts* on or off. *Work orders > Settings* can switch it on for every new customer job.
   - When it is on, **Start work** is refused until the estimate is approved (the button is disabled and the server refuses too). Internal jobs never need approval. It only controls starting: a job already started stays started.
   - If the fixed price, hourly rate or the parts change while the job is still Open, the approval is cleared and the estimate has to be sent again. After work has started, a change only shows a warning that the price differs from what the customer saw.
3. **Board.** *Work orders > Board*: columns Open, In progress, On hold and Completed (latest 15). Drag a card into In progress or On hold, or use the Start/Resume/Hold buttons on the card. A job is completed on its own page, where the checklist, parts and note are checked. Filter by person, type or text. Cards show priority, due date (late in red), approval state, "Planned" and checklist progress.
4. **Calendar.** *Work orders > Calendar*: one week per person (Monday to Sunday). Jobs show on their scheduled day (or due day if not scheduled). Drag a job to another day or to another person's row to re-schedule or re-assign it; the gap between its scheduled and due dates is kept. Jobs with no date are listed under the table. On a job, *Schedule > Move or reassign* does the same with date pickers.
5. **Planned maintenance.** *Work orders > Planned maintenance*: schedules for jobs that repeat (service the generator every 3 months, a monthly visit for a contract customer). Each has a title, internal or customer job, priority, optional asset, service package (copies the checklist and parts), assignee, fixed price and hourly rate for customer jobs, how often (days, weeks or months), the next due date, how many days early to create the job, and how many days after its scheduled day it is due.
   - A check runs every morning at 05:20 and creates the job for each active schedule that is due (or due within its "days early"). It moves the schedule on to the next date.
   - A schedule that fell far behind makes **one** job, not a pile of back-dated ones.
   - *Do not create a new job while the last one is still open* (on by default): the schedule skips that round and moves on.
   - *Create the jobs that are due* runs the check now; *Run now* on a schedule makes one job straight away (refused while the last job is still open and that option is on). Schedules can be paused, edited and deleted; jobs already made are kept.
6. **Notes, photos and files.** Notes (up to 2000 characters) by the technician or a manager; the author or a manager can delete one. Photos (JPG, PNG, GIF, WebP), PDF or text files up to 10 MB each, 40 per job, with an optional caption; thumbnails show on the job and open in a new tab. The file type is checked from the file itself, not only its name. Files are stored in the database, so they are in your backups (and grow the database).
7. **History.** *History > Show* lists what happened to the job from the audit log: created, estimate sent/approved/declined, started, held, completed, notes and files added, moved, e-mailed.
8. **Timer.** On an open or in-progress job with Time Tracking on, the assignee can *Start timer* and *Stop timer* (uses the normal Time Tracking timer on the job's project, so the time lands in the timesheet). Putting a job on hold or completing it stops the person's own running timer on that job and saves the time.
9. **Customer e-mail.** *E-mail completion note* on a completed customer job sends the PDF to the customer; the time it was last sent is shown. *Work orders > Settings > E-mail the customer when a job is completed* does it automatically. If e-mail is not set up, or the customer has no e-mail address, completing a job still works and the reason is recorded; sending an estimate or completion note by hand says why nothing was sent.
10. **Audit log.** New entries: `WORK_ORDER_ESTIMATE_SENT`, `_ESTIMATE_APPROVED`, `_ESTIMATE_REJECTED`, `_ESTIMATE_RESET`, `_APPROVAL_REQUIREMENT`, `_NOTE_ADDED`, `_NOTE_DELETED`, `_FILE_ADDED`, `_FILE_DELETED`, `_EMAILED`, `_EMAIL_SKIPPED`, `_EMAIL_FAILED`, `_RESCHEDULED`, `_SETTINGS_UPDATED`, `_TIMER_STOPPED`, `_TIMER_NOT_SAVED`, and `MAINTENANCE_SCHEDULE_CREATED`, `_UPDATED`, `_PAUSED`, `_RESUMED`, `_RAN`, `_SKIPPED`, `_FAILED`, `_DELETED`.

## Who can do what

Managers (administrator, or Operations manage) do everything above. The assignee works their own job: start/hold/complete (subject to the approval gate), checklist, parts, notes, photos, timer, job card. Operations view can read documents, estimates, history and schedules but not change them. Others get nothing from the new endpoints.

## Good to know

- The new screens are under `/admin/work-orders/...` and are linked in the Work Orders menu (Board, Calendar, Planned maintenance); *Settings* is a button row on those pages.
- Existing jobs are untouched: approval is off for them. The "require approval" default is off until you switch it on.
- Planned maintenance needs the Work Orders module (and Time Tracking, as before); the morning check does nothing while the module is off.
- The estimate PDF and completion note use the business name and details from *Settings > Business details* like other documents.

## Upgrading

Unpack, run `python3 tools/apply-v1150.py` from the repository folder (version numbers and README), then `python3 tools/wire-v150.py frontend/src/app` (adds the four routes and three menu links; safe to run twice), then build and start as usual; Flyway adds V54.
