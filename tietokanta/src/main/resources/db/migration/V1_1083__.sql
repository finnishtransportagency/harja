INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('api', 'analytiikka-hae-paallystysurakat'),
       ('api', 'analytiikka-hae-paallystyskohteet'),
       ('api', 'analytiikka-hae-paallystyskohteiden-aikataulu'),
       ('api', 'analytiikka-hae-paallystysilmoitukset');

-- Lisätään ylläpitokohteelle luotu-kolumni, jotta voidaan rajata analytiikalle lähetettäviä kohteita.
ALTER TABLE yllapitokohde
    ADD COLUMN luotu TIMESTAMP;

ALTER TABLE yllapitokohdeosa
    ADD COLUMN luotu TIMESTAMP;

ALTER TABLE yllapitokohteen_aikataulu
    ADD COLUMN luotu TIMESTAMP;

-- Aseta luotu-päivämäärä viimeisimpään muokkaukseen. Ei ole todenmukainen, mutta palvelee tarkoitusta,
-- joka on sallia ylläpitokohteiden siirto analytiikkaan osissa.
UPDATE yllapitokohde
SET luotu = muokattu
WHERE muokattu IS NOT NULL;

UPDATE yllapitokohdeosa
SET luotu = muokattu
WHERE muokattu IS NOT NULL;

UPDATE yllapitokohteen_aikataulu
SET luotu = muokattu
WHERE muokattu IS NOT NULL;

-- Jos kohdetta ei ole muokattu, asetetaan se sen vuoden ensimmäiselle päivälle klo 12.
UPDATE yllapitokohde
SET luotu = MAKE_TIMESTAMP(vuodet[1], 1, 12, 0, 0, 0)
WHERE muokattu IS NULL
  AND vuodet[1] IS NOT NULL;

UPDATE yllapitokohdeosa
SET luotu = (SELECT luotu FROM yllapitokohde WHERE yllapitokohde.id = yllapitokohdeosa.yllapitokohde)
WHERE muokattu IS NULL;

UPDATE yllapitokohteen_aikataulu aika
SET luotu = (SELECT luotu FROM yllapitokohde WHERE yllapitokohde.id = aika.yllapitokohde)
WHERE muokattu IS NULL;

-- Uusille riveille default-arvo.
ALTER TABLE yllapitokohde ALTER COLUMN luotu SET DEFAULT current_timestamp;
ALTER TABLE yllapitokohdeosa ALTER COLUMN luotu SET DEFAULT current_timestamp;
ALTER TABLE yllapitokohteen_aikataulu ALTER COLUMN luotu SET DEFAULT current_timestamp;

