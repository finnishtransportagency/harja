-- Lisätään kentät urakoitsijan selityksen vaatimiselle sekä
-- kenttä, joka kertoo, että selvitys on annettu (hakua varten)

ALTER TABLE havainto ADD COLUMN selvitys_pyydetty boolean NOT NULL DEFAULT false;
ALTER TABLE havainto ADD COLUMN selvitys_annettu boolean NOT NULL DEFAULT false;
