-- v1.75.0: one clearing account and one wallet/bank account per payment provider (MTN, Airtel, card).
alter table online_order add column clearing_channel varchar(20);
alter table online_payout drop constraint online_payout_channel_ck;
alter table online_payout add constraint online_payout_channel_ck check (channel in ('MOBILE_MONEY', 'MTN', 'AIRTEL', 'CARD'));
