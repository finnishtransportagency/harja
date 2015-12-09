-- Lisää laatupoikkeamille alku- ja loppusijainnit
ALTER TABLE laatupoikkeama ADD COLUMN alkusijainti GEOMETRY;
ALTER TABLE laatupoikkeama ADD COLUMN loppusijainti GEOMETRY;

UPDATE laatupoikkeama
SET alkusijainti = sijainti;

ALTER TABLE laatupoikkeama DROP COLUMN sijainti;

-- Lisää tarkastuksille alku- ja loppusijainnit
ALTER TABLE tarkastus ADD COLUMN alkusijainti GEOMETRY;
ALTER TABLE tarkastus ADD COLUMN loppusijainti GEOMETRY;

UPDATE tarkastus
SET alkusijainti = sijainti;

ALTER TABLE tarkastus DROP COLUMN sijainti;

-- Poista tarkastuksilta mittaaja
ALTER TABLE tarkastus DROP COLUMN mittaaja;