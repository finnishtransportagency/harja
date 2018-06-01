-- Muutoksia silta-tauluun uuden aineiston myötä
ALTER TABLE silta
  DROP CONSTRAINT uniikki_siltatunnus, -- sallitaan virheet aineistossa, pyritään saamaan korjaukset aineistoon sitä mukaa kun käyttäjät raportoivat
  ALTER COLUMN siltaid DROP NOT NULL, -- uniikki jos siltaid on annettu, null sallittu
  ADD COLUMN trex_oid TEXT, -- uniikki jos trex_oid on annettu, null sallittu toistaiseksi
  ADD COLUMN loppupvm DATE,
  ADD COLUMN lakkautuspvm DATE,
  ADD COLUMN muutospvm DATE,
  ADD COLUMN status INTEGER,
  ADD COLUMN luoja INTEGER,
  ADD COLUMN luotu TIMESTAMP,
  ADD COLUMN muokkaaja INTEGER,
  ADD COLUMN muokattu TIMESTAMP;

CREATE UNIQUE INDEX  uniikki_trex_oid ON silta (trex_oid);



drop column