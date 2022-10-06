ALTER TABLE geometriapaivitys
    ADD COLUMN seuraava_paivitys TIMESTAMP,
    ADD COLUMN kayta_paikallista_tiedostoa BOOLEAN DEFAULT FALSE,
    ADD COLUMN ei_ajeta BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN geometriapaivitys.kayta_paikallista_tiedostoa IS 'Kun et halua ladata aineistoa palvelimelta vaan käyttää manuaalisesti tiedostosijaintiin vietyä shapefileä, aseta tähän arvo TRUE. Onnistuneen päivityksen jälkeen arvo päivitetään oletukseen FALSE.';
COMMENT ON COLUMN geometriapaivitys.ei_ajeta IS 'Aseta tähän kenttään TRUE, kun haluat ettei aineistoa päivitetä aikaleimoista riippumatta palvelimelta eikä paikallisesti.';