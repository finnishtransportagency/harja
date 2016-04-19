-- Hakee seuraavan vapaan viestinumeron päivystäjälle lähetettävälle tekstiviestille
DROP FUNCTION IF EXISTS hae_seuraava_vapaa_viestinumero(yhteyshenkilo_id INTEGER);

CREATE OR REPLACE FUNCTION hae_seuraava_vapaa_viestinumero(haettava_puhelinnnumero VARCHAR(16))
  RETURNS INTEGER AS $$
BEGIN
  LOCK TABLE paivystajatekstiviesti IN ACCESS EXCLUSIVE MODE;
  RETURN (SELECT coalesce((SELECT (SELECT max(p.viestinumero)
                                   FROM paivystajatekstiviesti p
                                     INNER JOIN ilmoitus i ON p.ilmoitus = i.id
                                   WHERE p.puhelinnumero = haettava_puhelinnnumero AND
                                         NOT exists(SELECT itp.id
                                                    FROM ilmoitustoimenpide itp
                                                    WHERE
                                                      itp.ilmoitus = i.id AND
                                                      itp.kuittaustyyppi = 'lopetus'))), 0)
                 + 1 AS viestinumero);
END;
$$ LANGUAGE plpgsql;
