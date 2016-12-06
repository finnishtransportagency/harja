-- Päivitetään kaikille varustetoteumille alkupvm oletuksena Harjan käyttöönottopäivämäärä
UPDATE varustetoteuma
SET alkanut = '2016-10-01';

ALTER TABLE varustetoteuma ALTER COLUMN alkupvm SET NOT NULL;