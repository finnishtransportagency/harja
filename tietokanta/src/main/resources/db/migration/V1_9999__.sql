-- Poista duplikaatti rahavaraus rivit kannasta 
-- Näille olemassa jo rivit : Rahavaraus B, C ja K
DELETE FROM rahavaraus
      WHERE nimi IN ('Äkilliset hoitotyöt', 'Vahinkojen korvaukset', 'Kannustinjärjestelmä');

-- Lisää luoja kolumni toteutuneisiin kustannuksiin 
ALTER TABLE toteutuneet_kustannukset ADD COLUMN luoja INTEGER REFERENCES kayttaja (id);
