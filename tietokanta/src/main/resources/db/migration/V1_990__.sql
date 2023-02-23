-- Välitetty urakkaan columnista puuttui indeksi.
CREATE INDEX "ilmoitus_valitetty-urakkaan_index"
    ON ilmoitus ("valitetty-urakkaan");
-- Kuitattu kolumnista puuttui indeksi
CREATE INDEX ilmoitustoimenpide_kuitattu_index
    ON ilmoitustoimenpide (kuitattu);
