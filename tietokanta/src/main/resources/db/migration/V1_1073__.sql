ALTER TABLE pot2_alusta RENAME COLUMN massamaara TO massamenekki;

ALTER TABLE pot2_alusta ALTER COLUMN massamenekki TYPE NUMERIC(10, 2);
