CREATE OR REPLACE FUNCTION hae_seuraava_vapaa_viestinumero(yhteyshenkilo_id INTEGER)
  RETURNS INTEGER AS $$
BEGIN
  LOCK TABLE paivystajatekstiviesti IN ACCESS EXCLUSIVE MODE;
  RETURN (SELECT coalesce((SELECT (SELECT max(p.viestinumero)
                                   FROM paivystajatekstiviesti p
                                     INNER JOIN ilmoitus i ON p.ilmoitus = i.id
                                   WHERE p.yhteyshenkilo = yhteyshenkilo_id AND
                                         NOT exists(SELECT itp.id
                                                    FROM ilmoitustoimenpide itp
                                                    WHERE
                                                      itp.ilmoitus = i.id AND
                                                      itp.kuittaustyyppi = 'lopetus'))), 0)
                 + 1 AS viestinumero);
END;
$$ LANGUAGE plpgsql;

ALTER TABLE paivystajatekstiviesti ADD COLUMN puhelinnumero VARCHAR(16);