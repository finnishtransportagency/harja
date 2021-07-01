-- name: hae-urakan-lupaustiedot
SELECT id,
       pisteet,
       "urakka-id"
  FROM lupaus_sitoutuminen
 WHERE "urakka-id" = :urakkaid;

-- name: lisaa-urakan-luvatut-pisteet<!
INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
 VALUES (:urakkaid, :pisteet, :kayttaja);

-- name: paivita-urakan-luvatut-pisteet<!
UPDATE lupaus_sitoutuminen
   SET pisteet = :pisteet, muokattu = NOW(), muokkaaja = :kayttaja
 WHERE id = :id;

-- name: hae-lupauksen-urakkatieto
SELECT "urakka-id"
  FROM lupaus_sitoutuminen
 WHERE id = :id;
