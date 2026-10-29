# NIWE ERP, version 1.16.0

Time Tracking: timesheet discipline and project control. Versions 1.0.0 to 1.15.0 stay the base. One migration (V55). No new menu entries.

## What changed

1. **Forgotten timers.** A timer that has been running longer than the limit in *Time tracking > Settings* (default 12 hours; 0 = never) is stopped automatically by a check that runs every 15 minutes. The time is saved as a **draft** on the day the timer started, with a note that it was stopped automatically, so the person can correct it. It never pushes a day past the daily limit: only the room left in the day is saved, and if the day is already full the timer is dropped (both are recorded in the audit log). While a timer runs, *My time* shows a warning once it has run for a long time. *Settings > Stop forgotten timers now* runs the check by hand.
2. **Copy last week.** On *My time*, *Copy last week* copies the entries of the week before the one on screen into it as drafts. Days that have not happened yet, entries already there, archived or restricted projects and locked days are skipped, and the message says what was skipped.
3. **Lock date.** *Settings > Lock time up to and including* (with a *Last Sunday* shortcut and *Remove the lock*). On or before that date nobody can add, change, move or delete time, and a manager cannot correct it or send approved time back. Submitting and approving still work, so weeks can be closed after approval.
4. **Not submitted.** *Approvals > Not submitted* lists, for a chosen Monday-to-Sunday week, the people who normally log time (they logged in the 8 weeks before, or that week) but have not handed in everything: *Nothing logged*, *Drafts not handed in* or *Sent back*, with the hours. *Remind* e-mails one person; *Send reminder to everyone listed* e-mails all. Reminders need e-mail set up and an address on the person; people without one are counted and shown. *Settings* can also send these automatically every week on a chosen day (a check at 08:10 sends it for last week, once a day at most).
5. **Project team.** On a project (not one created by Work Orders or Law), *Project team* names who may log time. With *Only the people below can log time* switched on, others no longer see the project in the pickers and the server refuses their time and timers. Administrators can always log time. It controls logging only: reports, approvals and earlier time are unchanged.
6. **Budgets.** *Settings > Warn when a project has used (%)* (default 80, 0 = off): crossing it, and going over the budget, are each recorded once in the audit log; the project list shows the bar in amber from the warning line and red over 100%, and *My time* shows a note under the project when it is close or over. A project can also *Refuse time that would go over the budget* (needs a budget): people's own entries over it are refused with the numbers; a manager's correction is exempt. Task estimates now show the percentage used.

## Technical

- Migration `V55__time_tracking_v116.sql`: `time_settings` (timer_auto_stop_hours, locked_through, reminder_enabled, reminder_day, reminder_last_sent_on, budget_warn_percent), `time_project` (restrict_to_team, block_over_budget), new table `time_project_member`.
- Backend: `TimesheetDisciplineService` (auto-stop, copy week, missing list, reminders; scheduled at :07/:22/:37/:52 and 08:10), `TimeTrackingService` (lock, team, budget rules), new endpoints `POST /api/time/my/copy-week`, `GET /api/time/missing`, `POST /api/time/reminders`, `POST /api/time/timers/auto-stop`, `PUT /api/time/projects/{id}/team`.
- Tests: `t105` (API, 110 checks), `walk89` (browser, 22 checks); full regression on the frozen tree.

## Not in this version

Timesheet PDF and Excel, decimal-hours invoice quantities, a billing run, a week grid, splitting time across midnight and holidays or leave.
