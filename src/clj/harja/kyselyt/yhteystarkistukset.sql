-- name: kirjaa-yhteystarkistus<!
INSERT
INTO
  yhteystarkistus (nimi, viimeisin_tarkastus)
VALUES (:nimi, NOW())
ON CONFLICT ON CONSTRAINT uniikki_nimi
  DO
  UPDATE SET viimeisin_tarkastus = NOW();