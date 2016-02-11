-- name: kirjaa-uusi-paivystajatekstiviesti<!
INSERT INTO paivystajatekstiviesti (viestinumero, ilmoitus, yhteyshenkilo) VALUES
  ((SELECT hae_seuraava_vapaa_viestinumero(:yhteyshenkiloid :: INTEGER)),
   (SELECT id
    FROM ilmoitus
    WHERE ilmoitusid = :ilmoitusid),
   :yhteyshenkiloid);

-- name: hae-ilmoitus-id
SELECT ilmoitus
FROM paivystajatekstiviesti
WHERE yhteyshenkilo = :yhteyshenkilo AND
      viestinumero = :viestinumero;