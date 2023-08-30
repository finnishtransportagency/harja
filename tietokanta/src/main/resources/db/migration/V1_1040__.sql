-- Muutetaan uniikit indeksit käyttäämän luoja-sarakkeen sijaan urakka-saraketta
ALTER TABLE tarkastus
    DROP CONSTRAINT tarkastus_ulkoinen_id_luoja_uniikki;
CREATE UNIQUE INDEX tarkastus_ulkoinen_id_urakka_uniikki ON tarkastus (ulkoinen_id, urakka);
