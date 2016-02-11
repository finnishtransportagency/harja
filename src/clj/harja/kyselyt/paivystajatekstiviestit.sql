-- name: hae-seuraa-vapaa-viestinumero
SELECT hae_seuraava_vapaa_viestinumero(:yhteyshenkilo_id :: INTEGER) AS viestinumero;

-- name: kirjaa-uusi-paivystajatekstiviesti!
INSERT INTO paivystajaviesti (viestinumero, ilmoitus, yhteyshenkilo) VALUES
  (:viestinumero,
   (SELECT id
    FROM ilmoitus
    WHERE ilmoitusid = :ilmoitusid),
   :yhteyshenkilo);