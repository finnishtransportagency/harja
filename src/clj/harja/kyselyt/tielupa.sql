-- name: aseta-tieluvalle-urakka
SELECT aseta_tieluvalle_urakka(:id);

-- name: hae-id-ulkoisella-tunnisteella
SELECT id
FROM tielupa
WHERE "ulkoinen-tunniste" = :ulkoinen_tunniste;

-- name: liita-liite-tieluvalle<!
INSERT INTO tielupa_liite (tielupa, liite)
VALUES (:tielupa, :liite);