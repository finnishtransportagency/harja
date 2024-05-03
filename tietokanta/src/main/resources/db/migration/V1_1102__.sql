-- Lisätään toteuma_tehtavalle indeksi, joka nopeuttaa ilannekuvan tehtavahakua dramaattisesti
create index idx_toteuma_tehtava_toteuma_toimenpidekoodi_poistettu on toteuma_tehtava (toteuma, toimenpidekoodi, poistettu);
