-- name: paivita-materiaalin-alkuperainen-maara<!
UPDATE vv_materiaali
SET maara = :maara
WHERE id = :id;

-- name: urakan-tiedot-sahkopostin-lahetysta-varten
SELECT sampoid,
       nimi
FROM urakka
WHERE id = :id;

-- name: materiaalin-halytysraja
SELECT halytysraja
FROM vv_materiaali
WHERE nimi = :nimi AND
      "urakka-id" = :urakka-id AND
      halytysraja IS NOT NULL
LIMIT 1;

