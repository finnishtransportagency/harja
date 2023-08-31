-- Muutetaan uniikit indeksit käyttäämän luoja-sarakkeen sijaan urakka-saraketta
-- Partitioiduissa tauluissa muutos on jo tehty migraatiossa V1_1029.
CREATE UNIQUE INDEX tarkastus_ulkoinen_id_urakka_poistettu_tyyppi_uindex ON tarkastus (ulkoinen_id, urakka, poistettu, tyyppi);
CREATE UNIQUE INDEX tarkastus_ennen_2015_ulkoinen_id_urakka_poistettu_tyyppi_uindex ON tarkastus_ennen_2015 (ulkoinen_id, urakka, poistettu, tyyppi);

-- Poistetaan päivitysoikeuksia näihin vanhimipiin tauluihin
REVOKE INSERT, UPDATE, DELETE ON tarkastus_ennen_2015 FROM harja;

REVOKE INSERT, UPDATE, DELETE ON tarkastus_2015_q1 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2015_q2 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2015_q3 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2015_q4 FROM harja;

REVOKE INSERT, UPDATE, DELETE ON tarkastus_2016_q1 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2016_q2 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2016_q3 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2016_q4 FROM harja;

REVOKE INSERT, UPDATE, DELETE ON tarkastus_2017_q1 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2017_q2 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2017_q3 FROM harja;
REVOKE INSERT, UPDATE, DELETE ON tarkastus_2017_q4 FROM harja;
