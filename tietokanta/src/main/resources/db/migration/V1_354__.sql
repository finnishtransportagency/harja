UPDATE laatupoikkeama
SET paatos = 'ei_sanktiota'::laatupoikkeaman_paatostyyppi
WHERE paatos IS NULL AND
      (kasittelyaika IS NOT NULL OR
       perustelu IS NOT NULL OR
       kasittelytapa IS NOT NULL);

UPDATE laatupoikkeama
SET kasittelyaika = muokattu
WHERE kasittelyaika IS NULL
      AND paatos IS NOT NULL;

UPDATE laatupoikkeama
SET perustelu = 'Tämä laatupoikkeama on tallennettu virheellisesti ilman perustelua. Tämä on koneellisesti asetettu perusteluteksti.'
WHERE perustelu IS NULL
      AND paatos IS NOT NULL;

UPDATE laatupoikkeama
SET kasittelytapa = 'muu'::laatupoikkeaman_kasittelytapa,
  muu_kasittelytapa = 'Puuttuvat tiedot täydennetty koneellisesti'
WHERE kasittelytapa IS NULL
      AND paatos IS NOT NULL;

ALTER TABLE laatupoikkeama
  ADD CONSTRAINT kaikki_sanktiotiedot CHECK
((paatos IS NULL AND kasittelyaika IS NULL AND perustelu IS NULL AND kasittelytapa IS NULL) OR
 (paatos IS NOT NULL AND kasittelyaika IS NOT NULL AND perustelu IS NOT NULL AND kasittelytapa IS NOT NULL));