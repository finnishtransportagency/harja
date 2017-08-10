-- name: paivita-materiaalin-alkuperainen-maara<!
UPDATE vv_materiaali
SET maara = :maara
WHERE id = (SELECT id
            FROM vv_materiaali
            WHERE nimi = :nimi
            ORDER BY luotu
            LIMIT 1);