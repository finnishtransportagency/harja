UPDATE laatupoikkeama
  SET kasittelyaika = muokattu
WHERE kasittelyaika IS NULL AND paatos IS NOT NULL;

UPDATE laatupoikkeama
  SET paatos = 'ei_sanktiota'::laatupoikkeaman_paatostyyppi,
      perustelu = 'Tämä laatupoikkeama on tallennettu virheellisesti ilman päätöstä. Päätökseksi on asetettu koneellisesti ei sanktiota.'
WHERE paatos IS NULL AND kasittelyaika IS NOT NULL AND perustelu IS NULL;

UPDATE laatupoikkeama
  SET paatos = 'ei_sanktiota'::laatupoikkeaman_paatostyyppi
WHERE paatos IS NULL AND kasittelyaika IS NOT NULL;

ALTER TABLE laatupoikkeama
    ADD CONSTRAINT kaikki_sanktiotiedot CHECK
((paatos IS NULL AND perustelu IS NULL AND kasittelyaika IS NULL) OR
 (paatos IS NOT NULL AND kasittelyaika IS NOT NULL));