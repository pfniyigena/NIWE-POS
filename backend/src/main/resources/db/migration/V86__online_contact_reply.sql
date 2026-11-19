-- v1.51.1: staff answer a contact message by e-mail from the Messages screen; the answer is kept with the message.
alter table online_contact_message add column reply varchar(2000);
alter table online_contact_message add column replied_at timestamp(6) with time zone;
