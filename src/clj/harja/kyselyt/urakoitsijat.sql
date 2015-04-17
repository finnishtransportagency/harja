-- name: listaa-urakoitsijat
-- Hakee kaikkien väylämuotojen ja urakkatyyppien urakoitsijat
SELECT y.id, y.ytunnus, y.nimi,
       u.tyyppi as urakkatyyppi
  FROM urakka u
       LEFT JOIN organisaatio y ON u.urakoitsija = y.id
 WHERE y.tyyppi = 'urakoitsija'::organisaatiotyyppi

-- name: hae-urakkatyypin-urakoitsijat
-- Palauttaa annetun urakkatyypin urakoitsijoiden id:t
SELECT y.id 
  FROM urakka u 
  		LEFT JOIN organisaatio y ON u.urakoitsija = y.id 
 WHERE u.tyyppi = :tyyppi::urakkatyyppi;

 -- name: hae-yllapidon-urakoitsijat
-- Palauttaa kaikkien ylläpitourakkatyyppien urakoitsijoiden id:t
SELECT y.id 
  FROM urakka u 
  		LEFT JOIN organisaatio y ON u.urakoitsija = y.id 
 WHERE u.tyyppi != 'hoito'::urakkatyyppi;
