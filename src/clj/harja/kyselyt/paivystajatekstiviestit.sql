-- name: kirjaa-uusi-paivystajatekstiviesti<!
INSERT INTO paivystajatekstiviesti (viestinumero, ilmoitus, yhteyshenkilo) VALUES
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
-- single?: true
SELECT
  ilmoitus,
  yhteyshenkilo
WHERE puhelinnumero = :puhelinnumero AND
      viestinumero = :viestinumero
LIMIT 1;