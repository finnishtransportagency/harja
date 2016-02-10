-- name: hae-seuraa-vapaa-viestinumero
SELECT max(coalesce(
               (SELECT (SELECT pv.viestinumero
                        FROM paivystajaviesti pv
                          INNER JOIN ilmoitus i ON pv.ilmoitus = i.id AND i.suljettu IS NOT TRUE
                        WHERE yhteyshenkilo = :yhteyshenkilo_id)), 0)) + 1 AS viestinumero;

-- name: kirjaa-uusi-paivystajaviesti!
INSERT INTO paivystajaviesti (viestinumero, ilmoitus, yhteyshenkilo) VALUES
  (:viestinumero,
   (SELECT id
    FROM ilmoitus
    WHERE ilmoitusid = :ilmoitusid),
   :yhteyshenkilo);