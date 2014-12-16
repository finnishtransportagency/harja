-- name: listaa-hallintayksikot-kulkumuodolle
-- Hakee hallintayksiköiden perustiedot ja geometriat kulkumuodon mukaan
SELECT id, nimi, alue
  FROM hallintayksikko
 WHERE liikennemuoto = :liikennemuoto::liikennemuoto


