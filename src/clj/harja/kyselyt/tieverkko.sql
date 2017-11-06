-- name: vie-tien-osan-ajorata!
INSERT INTO tr_osan_ajorata (tie,osa,ajorata,geom)
VALUES (:tie, :osa, :ajorata, ST_GeomFromText(:geom));

-- name: hae-tr-osoite-valille
-- hakee tierekisteriosoitteen kahden pisteen välille
SELECT *
FROM tierekisteriosoite_pisteille(
         ST_MakePoint(:x1, :y1) :: GEOMETRY,
         ST_MakePoint(:x2, :y2) :: GEOMETRY,
         :threshold :: INTEGER) AS tr_osoite;

-- name: hae-tr-osoite-valille*
-- hakee tierekisteriosoitteen kahden pisteen välille tai NULL jos ei löydy
SELECT *
FROM yrita_tierekisteriosoite_pisteille2(
         ST_MakePoint(:x1, :y1) :: GEOMETRY,
         ST_MakePoint(:x2, :y2) :: GEOMETRY,
         :threshold :: INTEGER) AS tr_osoite;

-- name: hae-tieviivat-pisteille
-- Hakee tieverkolle projisoidut viivat annetuille pisteille.
-- Pisteet on string WKT geometrycollection pointeja. Jokaisen
-- kahden pisteen välille lasketaan osoite. Palauttaa
-- alkupisteen, loppupisteen ja viivan geometrian. Jos viivaa
-- ei löydy, palauttaa NULL geometriana.
SELECT *
FROM
      tieviivat_pisteille(ST_GeomFromText(:pisteet), :threshold :: INTEGER)
    AS vali(alku GEOMETRY, loppu GEOMETRY, geometria GEOMETRY);

-- name: hae-tieviivat-pisteille-aika
-- Hakee tieverkolle projisoidut viivat annetuille pisteille.
-- Huomio pisteiden välisen ajan järkevän geometrisoinnin päättelyssä.
SELECT *
  FROM tieviivat_pisteille_aika(:pisteet::piste_aika[])
       AS vali(alku GEOMETRY, loppu GEOMETRY, geometria GEOMETRY);

-- name: hae-tr-osoite
-- hakee tierekisteriosoitteen yhdelle pisteelle
SELECT *
FROM tierekisteriosoite_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: hae-tr-osoite*
-- Hakee TR osoitteen pisteelle tai nil jos ei löydy
SELECT *
FROM yrita_tierekisteriosoite_pisteelle2(
         ST_MakePoint(:x, :y) :: GEOMETRY,
         CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: tuhoa-tien-osien-ajoradat!
-- poistaa kaikki tien osien ajoratatiedot
DELETE FROM tr_osan_ajorata;

-- name: paivita-paloiteltu-tieverkko
-- päivittää tieverkkotaulut
SELECT paivita_tr_taulut();

-- name: tierekisteriosoite-viivaksi
-- single?: true
-- hakee geometrian annetulle tierekisteriosoitteelle
SELECT * FROM tierekisteriosoitteelle_viiva(
   CAST(:tie AS INTEGER),
   CAST(:aosa AS INTEGER), CAST(:aet AS INTEGER),
   CAST(:losa AS INTEGER), CAST(:loppuet AS INTEGER));


-- name: tierekisteriosoite-pisteeksi
-- single?: true
-- hakee pisteen annetulle tierekisteriosoitteelle jossa ei ole loppuosaa
SELECT *
FROM tierekisteriosoitteelle_piste(CAST(:tie AS INTEGER), CAST(:aosa AS INTEGER), CAST(:aet AS INTEGER));

-- name: hae-osien-pituudet
-- Hakee osien pituudet (metreinä) annetulla välillä (inclusive).
SELECT
  osa,
  pituus
FROM tr_osien_pituudet
WHERE tie = :tie AND
      ((:aosa::integer IS NULL AND :losa::integer IS NULL)
       OR
       (osa BETWEEN :aosa AND :losa));

-- name: hae-tieosan-ajoradat
SELECT ajorata
FROM tr_osan_ajorata
WHERE tie = :tie AND osa = :osa
ORDER BY ajorata ASC;

-- name: tuhoa-ajoratojen-pituudet!
DELETE FROM tr_ajoratojen_pituudet;

-- name: luo-ajoradan-pituus!
INSERT INTO tr_ajoratojen_pituudet (tie, osa, ajorata, pituus) VALUES (:tie, :osa, :ajorata, :pituus);

-- name: hae-ajoratojen-pituudet
SELECT
  osa,
  ajorata,
  pituus
FROM tr_ajoratojen_pituudet
WHERE tie = :tie AND
      ((:aosa::integer IS NULL AND :losa::integer IS NULL)
       OR
       (osa BETWEEN :aosa AND :losa));