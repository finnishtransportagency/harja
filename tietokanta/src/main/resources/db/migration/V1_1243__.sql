-- Lisää uudet arvot 'harja-api-ui' ja 'harja-api-korjaus' lahde enumiin
-- Tämä viittaa niihin toteumiin, joita on lisätty urakoitsijajärjestelmistä suoraan käsin
-- tai koneellisesti raportoitua on jälkikäteen muokattu käsin. UI tarkoittaa tässä (lahde) tapauksessa käsin tehtyä muutosta, joko
-- suoraan urakoitsijajärjestelmässä tai Harja-käyttöliittymässä.
ALTER TYPE lahde ADD VALUE 'harja-api-ui'; -- Tehty suoraan käsin urakoitsijajärjestelmässä
ALTER TYPE lahde ADD VALUE 'harja-api-korjaus'; -- Koneellisesti raportoitua on jälkikäteen muokattu
