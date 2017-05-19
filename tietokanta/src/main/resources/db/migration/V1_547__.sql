ALTER TABLE turvallisuuspoikkeama
  ALTER COLUMN paikan_kuvaus TYPE VARCHAR(200)
  USING substr(paikan_kuvaus, 1, 200)
