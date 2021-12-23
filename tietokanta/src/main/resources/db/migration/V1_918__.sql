-- Lisätään ilmoitus tauluun yksi indeksi, joka hieman pienentää hakujen kokoa.
-- Itse ilmoitus taulu on suuri (koska siellä on geometrioita sisällä), niin se syö helposti tietokannan muistin
-- Indeksin lisääminen vie levytilaa, mutta vähentää muistin käyttöä ja täten nopeuttaa kannan toimintaa
create index ilmoitus_urakka_ilmoitusid_index
    on ilmoitus (urakka, ilmoitusid);

-- Samat selitykset kuin yllä eli kyseessä opn iso taulu ja indeksin käyttö nopeuttaa hakuja, vaikka kovalevyä
-- tarvitaankin hieman lisää
create index ilmoitustoimenpide_kuittaustyyppi_ilmoitusid_index
    on ilmoitustoimenpide (kuittaustyyppi, ilmoitus);

-- Ilmoitustoimenpide tauluun päivitetään update lausella useammassa kohdassa lähetys id:n perusteella ja sillä
-- ei ollut indexiä. Säästämme aikaa valtavasti tällä lisäyksellä
create index ilmoitustoimenpide_lahetysid_index on ilmoitustoimenpide (lahetysid);
