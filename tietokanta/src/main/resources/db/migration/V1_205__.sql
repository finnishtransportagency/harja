-- Kuvaus: Tarkastukselle uusi uniikki indeksi

CREATE UNIQUE INDEX uniikki_tarkastus ON tarkastus (ulkoinen_id, luoja, tyyppi);