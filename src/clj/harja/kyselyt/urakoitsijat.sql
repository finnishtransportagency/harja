-- name: listaa-urakoitsijat
-- Hakee kaikkien väylämuotojen ja urakkatyyppien urakoitsijat
SELECT id, ytunnus, nimi
  FROM urakoitsija

-- name: hae-urakkatyypin-urakoitsijat
-- Palauttaa annetun urakkatyypin urakoitsijoiden id:t
SELECT y.id 
  FROM urakka u 
  		LEFT JOIN urakoitsija y ON u.urakoitsija_id = y.id 
 WHERE u.tyyppi = :tyyppi::urakkatyyppi;

 -- name: hae-yllapidon-urakoitsijat
-- Palauttaa kaikkien ylläpitourakkatyyppien urakoitsijoiden id:t
SELECT y.id 
  FROM urakka u 
  		LEFT JOIN urakoitsija y ON u.urakoitsija_id = y.id 
 WHERE u.tyyppi != 'hoito'::urakkatyyppi;