-- Aletaan ottaa erikseen talteen käyttöliittymältä valitut vastaanottajat (joilta tarvitaan urakkatietokin)
-- sekä vapaatekstinä muut vastaanottajat jotka voivat olla mitä tahansa, ilman urakkatietoa
CREATE TYPE urakka_email AS (urakka_id INTEGER, email TEXT);
ALTER TABLE yllapitokohteen_sahkopostitiedot RENAME COLUMN vastaanottajat TO muut_vastaanottajat;
ALTER TABLE yllapitokohteen_sahkopostitiedot ADD COLUMN urakka_vastaanottajat urakka_email[];
ALTER TABLE yllapitokohteen_sahkopostitiedot ADD COLUMN luotu TIMESTAMP DEFAULT NOW();
