# Release 1.51.1 - Online Store: answer a contact message inside the ERP

- **Reply by e-mail now opens a writing box in the Messages screen** (the old button only tried to open a mail program, which does nothing on a computer without one). Write the answer and press "Send the answer": the server sends it from the shop's e-mail (Settings > Email), in the customer's language, with the customer's message quoted under it, and the message becomes Answered. The answer you sent stays under the message (who, when). "Write another answer" is available for a follow-up.
- A customer who gave only a phone number has no e-mail box: the screen says to use the phone or the WhatsApp button. If e-mail is not set up, or sending fails, a clear message is shown and nothing is marked as answered.
- Database: migration V86 (`reply`, `replied_at` on `online_contact_message`). API: `POST /api/online-store/messages/{id}/reply` {text}. French texts.

Tested in the cloud with a mail server that needs STARTTLS and a login: the mail arrived with the right recipient, subject ("Re: <subject>") and body with the quoted message; phone-only message refused with the clear text; empty answer refused; the screen shows the box, sends, and moves the message to Answered with the sent text. Not tested: a real mail host, French screens.
