ALTER TABLE geometriapaivitys
    ADD COLUMN seuraava_paivitys TIMESTAMP,
    ADD COLUMN edellinen_paivitysyritys TIMESTAMP,
    ADD COLUMN paikallinen BOOLEAN DEFAULT FALSE,
    ADD COLUMN kaytossa BOOLEAN DEFAULT TRUE;

COMMENT ON COLUMN geometriapaivitys.paikallinen IS 'Kun et halua ladata aineistoa palvelimelta vaan käyttää manuaalisesti tiedostosijaintiin vietyä shapefileä, aseta tähän arvo TRUE.';
COMMENT ON COLUMN geometriapaivitys.kaytossa IS 'Kun haluat ettei aineistoa päivitetä aikaleimoista riippumatta palvelimelta eikä paikallisesti, aseta tähän kenttään FALSE.';

ALTER TABLE paallystyspalvelusopimus
    ADD COLUMN paivitetty TIMESTAMP;
ALTER TABLE hoitoluokka
    ADD COLUMN paivitetty TIMESTAMP;
ALTER TABLE valaistusurakka
    ADD COLUMN paivitetty TIMESTAMP;
ALTER TABLE tr_osoitteet
    ADD COLUMN paivitetty TIMESTAMP;
ALTER TABLE tr_ajoratojen_pituudet
    ADD COLUMN paivitetty TIMESTAMP;
