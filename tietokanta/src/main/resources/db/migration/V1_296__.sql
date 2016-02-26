-- Pudota suljettu-boolean ilmoitukselta, vanhentunut
ALTER TABLE ilmoitus DROP COLUMN suljettu;

CREATE OR REPLACE FUNCTION hae_seuraava_vapaa_viestinumero(yhteyshenkilo_id INTEGER)
  RETURNS INTEGER AS $$
BEGIN
  LOCK TABLE paivystajatekstiviesti IN ACCESS EXCLUSIVE MODE;
  RETURN (SELECT coalesce((SELECT (SELECT max(p.viestinumero)
                                   FROM paivystajatekstiviesti p
                                     INNER JOIN ilmoitus i ON p.ilmoitus = i.id
                                     INNER JOIN ilmoitustoimenpide itp ON itp.ilmoitus = i.id
                                   WHERE yhteyshenkilo = yhteyshenkilo_id)), 0) + 1 AS viestinumero);
END;
$$ LANGUAGE plpgsql;