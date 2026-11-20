-- v1.59.2: delivery / pick-up codes are always texted (SMS) as well as e-mailed. Until an SMS company is connected the message waits in the text log as QUEUED.
alter table online_text_message drop constraint online_text_message_status_ck;
alter table online_text_message add constraint online_text_message_status_ck check (status in ('SENT', 'FAILED', 'SKIPPED', 'QUEUED'));
