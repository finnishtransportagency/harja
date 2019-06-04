-- name: aseta-tieluvalle-urakka
SELECT aseta_tieluvalle_urakka(:id);

-- name: hae-id-ulkoisella-tunnisteella
SELECT id
FROM tielupa
WHERE "ulkoinen-tunniste" = :ulkoinen_tunniste;

-- name: liita-liite-tieluvalle<!
INSERT INTO tielupa_liite (tielupa, liite)
VALUES (:tielupa, :liite);

-- name: hae-tielupien-liitteet
SELECT
  *
FROM liite l JOIN tielupa_liite t ON l.id = t.liite
WHERE t.tielupa IN (:tielupa);

-- name: hae-urakan-tieluvat
SELECT *
FROM tielupa
WHERE urakat @> ARRAY[:urakkaid] ::INT[];
