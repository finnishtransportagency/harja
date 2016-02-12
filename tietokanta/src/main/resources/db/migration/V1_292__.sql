CREATE TABLE paivystajatekstiviesti (
  id            SERIAL PRIMARY KEY,
  viestinumero  INTEGER,
  ilmoitus      INTEGER REFERENCES ilmoitus (id),
  yhteyshenkilo INTEGER REFERENCES yhteyshenkilo (id)
);

CREATE OR REPLACE FUNCTION hae_seuraava_vapaa_viestinumero(yhteyshenkilo_id INTEGER)
  RETURNS INTEGER AS $$
BEGIN
  LOCK TABLE paivystajatekstiviesti IN ACCESS EXCLUSIVE MODE;
  RETURN (SELECT coalesce((SELECT (SELECT max(p.viestinumero)
                                   FROM paivystajatekstiviesti p
                                     INNER JOIN ilmoitus i ON p.ilmoitus = i.id AND i.suljettu IS NOT TRUE
                                   WHERE yhteyshenkilo = yhteyshenkilo_id)), 0) + 1 AS viestinumero);
END;
$$ LANGUAGE plpgsql;