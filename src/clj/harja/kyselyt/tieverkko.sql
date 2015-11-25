-- name: vie-tieverkkotauluun!
-- vie entryn tieverkkotauluun
INSERT INTO tieverkko (osoite3, tie, ajorata, osa, tiepiiri, tr_pituus, geometria) VALUES
  (:osoite3, :tie, :ajorata, :osa, :tiepiiri, :tr_pituus, ST_GeomFromText(:the_geom) :: GEOMETRY);

-- name: hae-tr-osoite-valille
-- hakee tierekisteriosoitteen kahden pisteen välille
SELECT *
FROM tierekisteriosoite_pisteille(ST_MakePoint(:x1, :y1) :: GEOMETRY,
                                  ST_MakePoint(:x2, :y2) :: GEOMETRY, CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: hae-tr-osoite
-- hakee tierekisteriosoitteen yhdelle pisteelle
SELECT *
FROM tierekisteriosoite_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: tuhoa-tieverkkodata!
-- poistaa kaikki tieverkon tiedot taulusta. ajetaan transaktiossa
DELETE FROM tieverkko;

-- name: paivita-paloiteltu-tieverkko!
-- päivittää materialisoidun näkymän
REFRESH MATERIALIZED VIEW tieverkko_paloina WITH DATA;

-- name: tierekisteriosoite-viivaksi
-- hakee geometrian annetulle tierekisteriosoitteelle
SELECT *
FROM tierekisteriosoitteelle_viiva(CAST(:tie AS INTEGER), CAST(:aosa AS INTEGER), CAST(:aet AS INTEGER),
                                   CAST(:losa AS INTEGER), CAST(:loppuet AS INTEGER));

-- name: tierekisteriosoite-pisteeksi
-- hakee pisteen annetulle tierekisteriosoitteelle jossa ei ole loppuosaa
SELECT *
FROM tierekisteriosoitteelle_piste(CAST(:tie AS INTEGER), CAST(:aosa AS INTEGER), CAST(:aet AS INTEGER));
