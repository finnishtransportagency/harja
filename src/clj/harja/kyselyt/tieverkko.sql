-- name: vie-tieverkkotauluun!
-- vie entryn tieverkkotauluun
INSERT INTO tieverkko (osoite3, tie, ajorata, osa, tiepiiri, tr_pituus, geometria) VALUES
  (:osoite3, :tie, :ajorata, :osa, :tiepiiri, :tr_pituus, ST_GeomFromText(:the_geom) :: GEOMETRY);

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
FROM yrita_tierekisteriosoite_pisteille(
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

-- name: hae-tr-osoite
-- hakee tierekisteriosoitteen yhdelle pisteelle
SELECT *
FROM tierekisteriosoite_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: hae-tr-osoite*
-- Hakee TR osoitteen pisteelle tai nil jos ei löydy
SELECT *
FROM yrita_tierekisteriosoite_pisteelle(
         ST_MakePoint(:x, :y) :: GEOMETRY,
         CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: tuhoa-tieverkkodata!
-- poistaa kaikki tieverkon tiedot taulusta. ajetaan transaktiossa
DELETE FROM tieverkko;

-- name: paivita-paloiteltu-tieverkko
-- päivittää tieverkkotaulut
SELECT paivita_tr_taulut();

-- name: tierekisteriosoite-viivaksi
-- single?: true
-- hakee geometrian annetulle tierekisteriosoitteelle
SELECT * FROM tr_osoitteelle_viiva3(
   CAST(:tie AS INTEGER),
   CAST(:aosa AS INTEGER), CAST(:aet AS INTEGER),
   CAST(:losa AS INTEGER), CAST(:loppuet AS INTEGER));


-- name: tierekisteriosoite-pisteeksi
-- hakee pisteen annetulle tierekisteriosoitteelle jossa ei ole loppuosaa
SELECT *
FROM tierekisteriosoitteelle_piste(CAST(:tie AS INTEGER), CAST(:aosa AS INTEGER), CAST(:aet AS INTEGER));

-- name: hae-osien-pituudet
-- Hakee osien pituudet annetulla välillä (inclusive)
SELECT
  osa,
  pituus
FROM tr_osien_pituudet
WHERE tie = :tie AND
      ((:aosa::integer IS NULL AND :losa::integer IS NULL)
       OR
       (osa BETWEEN :aosa AND :losa));
