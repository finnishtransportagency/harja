-- name: paivita-materiaalin-alkuperainen-maara<!
UPDATE vv_materiaali
SET maara = :maara
WHERE id = :id;

-- name: urakan-tiedot-sahkopostin-lahetysta-varten
SELECT sampoid,
       nimi
FROM urakka
WHERE id = :id;