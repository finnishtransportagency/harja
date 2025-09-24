-- Pysyvien muutosten tietomallimuutokset

-- On joitakin tilanteita, missä toimenpiteet halutaan käyttöliittymässä järjestää ao. mukaisesti
ALTER TABLE toimenpide ADD COLUMN jarjestys INTEGER;

-- jätetään hieman tyhjiä välejä, jos tarvii myöhemmin lisätä väliin uusia
UPDATE toimenpide SET jarjestys = 10 WHERE taso = 3 AND koodi = '23104'; -- Talvihoito
UPDATE toimenpide SET jarjestys = 20 WHERE taso = 3 AND koodi = '23116'; -- Liikenneympäristön hoito
UPDATE toimenpide SET jarjestys = 30 WHERE taso = 3 AND koodi = '23124'; -- Sorateiden hoito
UPDATE toimenpide SET jarjestys = 40 WHERE taso = 3 AND koodi = '20107'; -- Päällysteiden paikkaus
UPDATE toimenpide SET jarjestys = 50 WHERE taso = 3 AND koodi = '20191'; -- MHU ylläpito
UPDATE toimenpide SET jarjestys = 60 WHERE taso = 3 AND koodi = '14301'; -- Korvausinvestointi
UPDATE toimenpide SET jarjestys = 70 WHERE taso = 3 AND koodi = '23151'; -- MHU ja HJU hoidon johto

-- kustannusvaikutukset kohdistuvat toimenpideinstanssiin, eivät yleisesti toimenpiteisiin
ALTER TABLE mhu_muutos_kustannusvaikutus
    DROP CONSTRAINT IF EXISTS mhu_muutos_kustannusvaikutus_toimenpide_fkey;
ALTER TABLE mhu_muutos_kustannusvaikutus
    RENAME COLUMN toimenpide TO toimenpideinstanssi;

ALTER TABLE mhu_muutos_kustannusvaikutus
    ADD CONSTRAINT mhu_muutos_kustannusvaikutus_toimenpideinstanssi_fkey
    FOREIGN KEY (toimenpideinstanssi)
    REFERENCES toimenpideinstanssi(id);



-- Korjaa constraintteja --

-- Yhteen pysyvään muutokseen voidaan tallentaa useita tehtävä- ja määrämuutoksia useille hoitovuosille
ALTER TABLE mhu_muutos_tehtava_ja_maaraluettelo
    ADD CONSTRAINT uniikki_muutos_tehtava_ja_maara UNIQUE (muutos, tehtava, hoitokauden_alkuvuosi);

-- Yhteen pysyvään muutokseen voidaan tallentaa useita kustannusvaikutuksia useille hoitovuosille, joissa vaihtelevat kustannuslajit ja toimenpideinstanssit
-- Muutostöillä tpi:tä ei ole, koska kuluja ei kirjata suoraan 
ALTER TABLE mhu_muutos_kustannusvaikutus DROP CONSTRAINT IF EXISTS uniikki_muutos_kustannusvaikutus;
CREATE UNIQUE INDEX uniikki_muutos_kustannusvaikutus ON mhu_muutos_kustannusvaikutus (
    muutos, kustannuslaji, hoitokauden_alkuvuosi,
    COALESCE(toimenpideinstanssi, -1)
);

DROP INDEX IF EXISTS mhu_muutos_kustannusvaikutus_idx;
CREATE INDEX mhu_muutos_kustannusvaikutus_mvt_idx ON mhu_muutos_kustannusvaikutus (muutos, versio, toimenpideinstanssi);



-- FIXME: mhu_muutos_kulu taulun constraintti on ongelmallinen, koska kulun id muuttuu jatkuvasti sen
--       päivittyessä (luodaan uusi kulu ja vanha poistetaan).
--       Tämä johtaa nyt siihen, että mhu_muutos_kulu tauluun voi tulla useita rivejä, jotka viittaavaat
--       samaan mhu_muutos riviin, mutta eri versioihin. Tahtotila olisi se, että mhu_muutos_kulu taulussa
--       olisi vain 0...n riviä per mhu_muutos rivi, jotka viittaisivat aina viimeisimpään mhu_muutos versioon.
--       Koska luodaan vain uusia mhu_muutos_kulu rivejä, eikä saada päivitettyä, ei tämä johda tietojen tallentamiseen
--       mhu_muutos_kulu_historia tauluun, vaan historia elää mhu_muutos_kulu taulussa tällä hetkellä...
ALTER TABLE mhu_muutos_kulu
    DROP CONSTRAINT IF EXISTS mhu_muutos_kulu_versio_muutos_kulu_key;
ALTER TABLE mhu_muutos_kulu
    -- Tällä hetkellä kulut luodaat aina uusiksi ja vanha kulun versio poistetaan, jolloin jokainen päivitys mhu_muutos_kulu
    -- tauluun luo uuden id:n kululle. Versionumero puolestaan muuttuu mhu_muutos taulun päivityksen yhteydessä ja
    -- uusi versionumero kuljetetaan tähän tauluun.
    ADD CONSTRAINT mhu_muutos_mk_unique UNIQUE (muutos, kulu);
