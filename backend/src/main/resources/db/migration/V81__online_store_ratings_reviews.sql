-- v1.46.0: ratings (the stars) and reviews (the words) are separate. A rating counts as soon as it is given; only the words wait for staff.
alter table online_review add column rating_counted boolean not null default true;
-- a review with no words has nothing to check, so it is not waiting any more
update online_review set status = 'APPROVED' where status = 'PENDING' and (review_text is null or review_text = '');
-- a review staff had hidden keeps being hidden, and its stars stay out of the average as before
update online_review set rating_counted = false where status = 'HIDDEN';
