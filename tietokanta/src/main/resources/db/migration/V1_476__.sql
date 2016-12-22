<<<<<<< HEAD
ALTER TABLE tietyomaa
  ADD COLUMN nopeusrajoitus INT;
=======
-- Indeksoi ilmoituksen urakka
CREATE INDEX ilmoitus_urakka_idx ON ilmoitus (urakka);
>>>>>>> develop
