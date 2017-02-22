ALTER TABLE yllapito_toteuma
  ADD COLUMN poistettu boolean
  DEFAULT FALSE
  NOT NULL;

ALTER TABLE yllapito_toteuma RENAME TO yllapito_muu_toteuma;
