-- Muutoksia urakanvastuuhenkilo-tauluun

-- Lisää Urakanvastuuhenkilo-tauluun etu- ja sukunimisarakkeet erikseen
ALTER TABLE urakanvastuuhenkilo
  ADD COLUMN etunimi VARCHAR(255),
  ADD COLUMN sukunimi VARCHAR(255);

-- Siirrä nykyiset nimitiedot sukunimisarakkeeseen
UPDATE urakanvastuuhenkilo SET sukunimi = nimi;

-- Poista nimisarake, jos nimitietojen siirto sujui hyvin
ALTER TABLE urakanvastuuhenkilo
  DROP COLUMN nimi;

-- Lisää uniikki indeksi: urakkaan kuuluvalla roolilla on vain yksi ensisijainen vastuuhenkilö
CREATE UNIQUE INDEX uniikki_ensisijainen_urakanvastuuhenkilo_roolissa
  ON urakanvastuuhenkilo (urakka, rooli, ensisijainen);
COMMENT ON INDEX uniikki_ensisijainen_urakanvastuuhenkilo_roolissa IS 'Urakkaan kuuluvalla roolilla saa olla vain yksi ensisijainen vastuuhenkilo.';
