-- Luodaan tehtavaryhmaotsikko taululle kolumni, jossa määritellään, että mille urakkatyypille otsikko kuuluu
-- Oletuksena kaikille otsikoille urakkatyyppi on "teiden-hoito", mutta tarvittaessa voidaan luoda myös muita urakkatyyppejä, esim. "vesivayla-hoito"
ALTER TABLE tehtavaryhmaotsikko
    ADD COLUMN urakkatyyppi urakkatyyppi DEFAULT 'teiden-hoito' NOT NULL;

-- Lisää tehtäville kolumnit, joilla voidaan määritellä, onko tehtävä Vesiväylä-hoito-urakoissa relevantti ja onko tehtävän tavoitehintavaikutus aina vai ehdollinen
ALTER TABLE tehtava
    ADD COLUMN "vhu-tehtava?" BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN tavoitehintavaikutus_aina BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN tavoitehintavaikutus_ehdollinen NUMERIC DEFAULT NULL;
