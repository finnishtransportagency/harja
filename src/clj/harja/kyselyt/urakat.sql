-- name: listaa-urakat-hallintayksikolle
-- Palauttaa listan annetun hallintayksikön (id) urakoista. Sisältää perustiedot ja geometriat.
-- PENDING: joinataan mukaan ylläpidon urakat eri taulusta?
SELECT id, nimi, alue::POLYGON
  FROM urakka
 WHERE hallintayksikko_id = :hallintayksikko
 
