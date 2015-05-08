-- name: hae-lampotilat
-- Hakee urakan lämpötilat urakan id:llä
SELECT id, urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila from lampotilat WHERE urakka = :id;

-- name: uusi-lampotila<!
INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila) VALUES (:urakka, :alku, :loppu, :keskilampo, :pitkalampo);

-- name: paivita-lampotila<!
UPDATE lampotilat SET
  urakka = :urakka, alkupvm = :alku, loppupvm = :loppu, keskilampotila = :keskilampo, pitka_keskilampotila = :pitkalampo
WHERE id = :id;