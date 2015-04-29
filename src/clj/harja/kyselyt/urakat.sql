-- name: listaa-urakat-hallintayksikolle
-- Palauttaa listan annetun hallintayksikön (id) urakoista. Sisältää perustiedot ja geometriat.
-- PENDING: joinataan mukaan ylläpidon urakat eri taulusta?
SELECT u.id, u.nimi, u.sampoid, u.alue,
       u.alkupvm, u.loppupvm, u.tyyppi, u.sopimustyyppi, 
       hal.id as hallintayksikko_id, hal.nimi as hallintayksikko_nimi, hal.lyhenne as hallintayksikko_lyhenne, 
       urk.id as urakoitsija_id, urk.nimi as urakoitsija_nimi, urk.ytunnus as urakoitsija_ytunnus,
       (SELECT array_agg(concat(id, '=', sampoid)) FROM sopimus s WHERE urakka = u.id)  as sopimukset,
       ST_Simplify(au.alue, 50) as alueurakan_alue
  FROM urakka u
       LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
       LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
       LEFT JOIN hanke h ON u.hanke=h.id
       LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
 WHERE hallintayksikko = :hallintayksikko

-- name: hae-urakoita
-- Hakee urakoita tekstihaulla.
SELECT u.id, u.nimi, u.sampoid, u.alue::POLYGON,
       u.alkupvm, u.loppupvm, u.tyyppi, u.sopimustyyppi,
       hal.id as hallintayksikko_id, hal.nimi as hallintayksikko_nimi, hal.lyhenne as hallintayksikko_lyhenne, 
       urk.id as urakoitsija_id, urk.nimi as urakoitsija_nimi, urk.ytunnus as urakoitsija_ytunnus,
       (SELECT array_agg(concat(id, '=', sampoid)) FROM sopimus s WHERE urakka = u.id)  as sopimukset,
       ST_Simplify(au.alue, 50) as alueurakan_alue
  FROM urakka u
       LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
       LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
       LEFT JOIN hanke h ON u.hanke=h.id
       LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro       
 WHERE u.nimi ILIKE :teksti
    OR hal.nimi ILIKE :teksti
    OR urk.nimi ILIKE :teksti

-- name: hae-organisaation-urakat
-- Hakee organisaation "omat" urakat, joko urakat joissa annettu hallintayksikko on tilaaja
-- tai urakat joissa annettu urakoitsija on urakoitsijana.
SELECT u.id, u.nimi, u.sampoid, u.alue::POLYGON,
       u.alkupvm, u.loppupvm, u.tyyppi, u.sopimustyyppi, 
       hal.id as hallintayksikko_id, hal.nimi as hallintayksikko_nimi, hal.lyhenne as hallintayksikko_lyhenne, 
       urk.id as urakoitsija_id, urk.nimi as urakoitsija_nimi, urk.ytunnus as urakoitsija_ytunnus,
       (SELECT array_agg(concat(id, '=', sampoid)) FROM sopimus s WHERE urakka = u.id)  as sopimukset,
       ST_Simplify(au.alue, 50) as alueurakan_alue
  FROM urakka u
       LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
       LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
       LEFT JOIN hanke h ON u.hanke=h.id
       LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro       
 WHERE urk.id = :organisaatio
    OR hal.id = :organisaatio
 

-- name: tallenna-urakan-sopimustyyppi!
-- Tallentaa urakalle sopimustyypin
UPDATE urakka
   SET sopimustyyppi = :sopimustyyppi::sopimustyyppi
 WHERE id = :urakka

-- name: hae-urakan-sopimustyyppi
-- Hakee urakan sopimustyypin
SELECT sopimustyyppi
  FROM urakka
 WHERE id = :urakka