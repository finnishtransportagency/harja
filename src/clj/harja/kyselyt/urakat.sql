-- name: listaa-urakat-hallintayksikolle
-- Palauttaa listan annetun hallintayksikön (id) urakoista. Sisältää perustiedot ja geometriat.
-- PENDING: joinataan mukaan ylläpidon urakat eri taulusta?
SELECT u.id, u.nimi, u.sampoid, u.alue::POLYGON,
       u.alkupvm, u.loppupvm, u.tyyppi,
       hal.id as hallintayksikko_id, hal.nimi as hallintayksikko_nimi, hal.lyhenne as hallintayksikko_lyhenne, 
       urk.id as urakoitsija_id, urk.nimi as urakoitsija_nimi, urk.ytunnus as urakoitsija_ytunnus,
       ST_Simplify(au.alue, 50) as alueurakan_alue
  FROM urakka u
       LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
       LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
       LEFT JOIN hanke h ON u.hanke=h.id
       LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro       
 WHERE (hallintayksikko = :hallintayksikko OR au.elynumero = hal.elynumero)
