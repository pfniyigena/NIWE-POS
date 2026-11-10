-- v1.23.0 Languages: the business-wide default language of the screens ('en' English, 'fr' French).
-- Choosing the Chad chart of accounts sets French while the language is still the untouched default (English).
alter table store_settings add column language character varying(8) not null default 'en';
alter table store_settings add constraint store_settings_language_check check (language in ('en', 'fr'));
