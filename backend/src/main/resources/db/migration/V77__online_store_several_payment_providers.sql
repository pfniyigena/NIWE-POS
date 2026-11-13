-- Staff may switch on several payment providers (MTN_MOMO,AIRTEL_MONEY): the column holds the codes separated by commas.
alter table online_store_settings alter column payment_provider type character varying(200);
