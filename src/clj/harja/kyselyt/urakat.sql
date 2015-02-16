-- name: listaa-urakat-hallintayksikolle
-- Palauttaa listan annetun hallintayksikön (id) urakoista. Sisältää perustiedot ja geometriat.
-- PENDING: joinataan mukaan ylläpidon urakat eri taulusta?
SELECT u.id, u.nimi, u.sampoid, u.alue::POLYGON,
       u.alkupvm, u.loppupvm, u.tyyppi,
       urk.id as urakoitsija_id, urk.nimi as urakoitsija_nimi, urk.ytunnus as urakoitsija_ytunnus
  FROM urakka u
       LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
 WHERE hallintayksikko = :hallintayksikko
