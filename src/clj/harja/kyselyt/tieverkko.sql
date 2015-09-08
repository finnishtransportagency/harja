-- name: vie-tieverkkotauluun!
-- vie entryn tieverkkotauluun
INSERT INTO tieverkko (osoite3, tie, ajorata, osa, tiepiiri, tr_pituus, hoitoluokka, geometria) VALUES
       (:osoite3, :tie, :ajorata, :osa, :tiepiiri, :tr_pituus, :hoitoluokka, ST_GeomFromText(:the_geom)::geometry)

-- name: hae-tr-osoite-pisteelle
-- hakee tierekisteriosoitteen pisteelle
SELECT osoite3, tie, ajorata, osa, tiepiiri, tr_pituus, hoitoluokka, geometria
  FROM tieverkko, LATERAL ST_MakePoint(:x, :y) pt
  WHERE ST_DWithin(geometria, pt, :treshold)
  ORDER BY ST_Length(ST_ShortestLine(geometria, pt)) ASC
  LIMIT 1

-- name: tuhoa-tieverkkodata!
-- poistaa kaikki tieverkon tiedot taulusta. ajetaan transaktiossa
DELETE FROM tieverkko;
