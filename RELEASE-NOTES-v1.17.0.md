# NIWE ERP, version 1.17.0

Booking: reminders and guest self-service, and working hours per person. Versions 1.0.0 to 1.16.0 stay the base. One migration (V56). One new page (*Booking > Working hours*), added by `tools/wire-v170.py`.

## What changed

1. **Reminder e-mails.** *Booking > Settings > Reminders and moving a booking*: switch on *E-mail the guest a reminder* and choose how many hours before the visit (default 24). A check every 15 minutes sends one reminder, with a calendar file attached, to each confirmed booking that has an e-mail address, once. A visit that is booked after the reminder period has already begun is not reminded (the guest was only just told about it). Needs e-mail set up under Settings. On an appointment, *Send reminder now* sends one by hand at any time, and the page shows when the reminder went.
2. **Moved notice and calendar file.** Moving an appointment now e-mails the guest "Booking moved" with the old time and the new one (before, they got a second "confirmed" mail). Confirmation, moved and reminder e-mails carry a calendar (.ics) file. Staff can download it from the appointment; guests have *Add to my calendar* on the public page.
3. **Guests move their own booking.** After booking or looking up a booking on the public page, *Move to another time* shows the free times of a day (the guest's own current time counts as free) and moves the booking, keeping the same person when they are free. The same rules as cancelling apply: only until the cancellation notice before the visit, only for pending or confirmed bookings, and at most N times (Settings, default 2). The guest's phone number and code are required, as for cancelling. A switch in Settings turns it off.
4. **Guest history.** An appointment shows how many other visits the same guest (matched by customer or phone number) has had: done, no-shows, cancelled and coming up, with a warning from two no-shows.
5. **Day sheet.** *Booking > Calendar > Print day sheet* opens a plain printable list of the day: time, guest and phone, service, person or room, status and notes, and who is away.
6. **Working hours and leave.** *Booking > Working hours*: each person who performs a service follows the business opening hours unless you give them their own hours (days they work, from and until). Own hours must fall inside the opening hours and on days the business is open. *Leave* (from and to, with a reason) removes the person from every slot on those days; adding leave tells you how many appointments are already booked in the period (they are not moved or cancelled automatically). The calendar greys out the part of a person's column when they are off or outside their hours and names the reason. Staff booking, moving and the public page all follow these hours, so nobody can be booked outside them.

## Technical

- Migration `V56__booking_v117.sql`: `booking_settings` (reminder_enabled, reminder_hours, allow_guest_reschedule, max_guest_reschedules), `appointment` (reminder_sent_at, reschedule_count), new tables `booking_staff_hours` (user, weekday 1-7, start, end) and `booking_staff_leave`.
- Backend (`com.posapp.booking`): `AppointmentService` (slot rule `inWindow`, `publicReschedule`, `publicRescheduleSlots`, `sendDueReminders`/`reminderJob` at :11/:26/:41/:56, `remindNow`, calendar files, `history`), `BookingSetupService` (schedules, leave, `staffWindows`), `BookingMailService` (`moved`, `reminder`, calendar attachments), `BookingIcs`. New endpoints: `POST /api/booking/appointments/{id}/remind`, `GET /api/booking/appointments/{id}/calendar.ics`, `POST /api/booking/reminders/run`, `GET /api/booking/schedules`, `PUT /api/booking/schedules/{userId}`, `POST /api/booking/schedules/{userId}/leave`, `DELETE /api/booking/leave/{id}`, and public `POST /api/public/booking/appointments/{code}/reschedule-slots|reschedule|calendar`.
- Tests: `t106` (API, 113 checks), `walk90` (browser, 25 checks); full regression on the frozen tree.

## Not in this version

Recurring appointments, group sessions with several seats, a waitlist, customer visit pages and no-show fees, per-service availability windows, SMS reminders.
