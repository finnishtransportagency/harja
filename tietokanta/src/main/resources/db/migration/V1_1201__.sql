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

