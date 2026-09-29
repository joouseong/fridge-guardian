-- Preserve existing dates and allow explicitly unregistered expiration dates.
ALTER TABLE ingredients MODIFY COLUMN expiration_date DATE NULL;
