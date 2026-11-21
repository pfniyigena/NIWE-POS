-- v1.67.0: the shop does not sell goods with fewer than this many days left before they expire (0 = only the expired ones are held back).
alter table online_store_settings add column min_days_left integer not null default 0;
