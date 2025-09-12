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

-- Yhteen pysyvään muutokseen voidaan tallentaa useita tehtävä- ja määrämuutoksia useille hoitovuosille
ALTER TABLE mhu_muutos_tehtava_ja_maaraluettelo
    ADD CONSTRAINT uniikki_muutos_tehtava_ja_maara UNIQUE (muutos, versio, tehtava, hoitokauden_alkuvuosi);

-- Yhteen pysyvään muutokseen voidaan tallentaa useita kustannusvaikutuksia useille hoitovuosille, joissa vaihtelevat kustannuslajit ja toimenpideinstanssit
ALTER TABLE mhu_muutos_kustannusvaikutus ADD CONSTRAINT uniikki_muutos_kustannusvaikutus UNIQUE (muutos, kustannuslaji, toimenpideinstanssi, hoitokauden_alkuvuosi);
DROP INDEX IF EXISTS mhu_muutos_kustannusvaikutus_idx;
CREATE INDEX mhu_muutos_kustannusvaikutus_mvt_idx ON mhu_muutos_kustannusvaikutus (muutos, versio, toimenpideinstanssi);
