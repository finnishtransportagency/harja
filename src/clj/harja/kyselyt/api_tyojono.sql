-- name: hae-tapahtuman-sisalto
-- single?: true
SELECT sisalto
FROM api_tyojono
WHERE id = :id;

-- name: lisaa-tyojonoon<!
INSERT INTO api_tyojono ("tapahtuman-nimi", sisalto)
VALUES (:tapahtuman_nimi, :sisalto);

-- name: poista-tyojonosta!
DELETE FROM api_tyojono
WHERE id = :id;