-- Opening balances brought in by import: an invoice (customer owes us) or a bill (we owe a supplier) that is not a new sale or purchase.
alter table invoice add column opening_balance boolean not null default false;
alter table bill add column opening_balance boolean not null default false;
