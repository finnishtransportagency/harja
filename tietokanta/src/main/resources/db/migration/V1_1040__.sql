-- Muutetaan uniikit indeksit käyttäämän luoja-sarakkeen sijaan urakka-saraketta
ALTER TABLE tarkastus
    DROP CONSTRAINT tarkastus_ulkoinen_id_luoja_uniikki;
CREATE UNIQUE INDEX tarkastus_ulkoinen_id_urakka_uniikki ON tarkastus (ulkoinen_id, urakka);

ALTER TABLE tarkastus_ennen_2015
    DROP CONSTRAINT tarkastus_ennen_2015_ulkoinen_id_luoja_tyyppi_idx;
CREATE UNIQUE INDEX tarkastus_ennen_2015_ulkoinen_id_urakka_tyyppi_uniikki ON tarkastus_ennen_2015 (ulkoinen_id, urakka, tyyppi);

