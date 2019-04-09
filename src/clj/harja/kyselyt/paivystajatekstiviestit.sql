-- name: kirjaa-uusi-paivystajatekstiviesti<!
INSERT INTO paivystajatekstiviesti (viestinumero, ilmoitus, yhteyshenkilo, puhelinnumero) VALUES
  ((SELECT hae_seuraava_vapaa_viestinumero(:puhelinnumero :: VARCHAR(16))),
   (SELECT id
    FROM ilmoitus
    WHERE ilmoitusid = :ilmoitusid),
   :yhteyshenkiloid,
   :puhelinnumero);

-- name: poista-ilmoituksen-viestit!
DELETE FROM paivystajatekstiviesti
WHERE ilmoitus = :ilmoitus;

-- name: hae-puhelin-ja-viestinumerolla
SELECT
  ptv.ilmoitus,
  i.ilmoitusid,
  ptv.yhteyshenkilo
FROM paivystajatekstiviesti ptv
  JOIN ilmoitus i ON ptv.ilmoitus = i.id
WHERE ptv.puhelinnumero = :puhelinnumero AND
      ptv.viestinumero = :viestinumero AND
      NOT exists(SELECT itp.id
                 FROM ilmoitustoimenpide itp
                 WHERE
                   itp.ilmoitus = i.id AND
                   itp.kuittaustyyppi = 'lopetus');
