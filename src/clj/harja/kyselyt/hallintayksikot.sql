-- name: listaa-hallintayksikot-kulkumuodolle
-- Hakee hallintayksiköiden perustiedot ja geometriat kulkumuodon mukaan
SELECT id, nimi, alue
  FROM organisaatio
 WHERE tyyppi = 'hallintayksikko'::organisaatiotyyppi AND
       liikennemuoto = :liikennemuoto::liikennemuoto


