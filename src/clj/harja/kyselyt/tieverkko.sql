-- name: vie-tierekisteri!
-- vie entryn tieverkkotauluun
INSERT INTO tieverkko (osoite3, tie, ajorata, osa, tiepiiri, tr_pituus, hoitoluokka, geometria, update_hash) VALUES
       (:osoite3, :tie, :ajorata, :osa, :tiepiiri, :tr_pituus, :hoitoluokka, ST_GeomFromText(:the_geom)::geometry, :update_hash)

-- name: hae-tr-osoite-pisteelle
-- hakee tierekisteriosoitteen pisteelle
SELECT osoite3, tie, ajorata, osa, tiepiiri, tr_pituus, hoitoluokka
  FROM tieverkko
  WHERE ST_Length(ST_ShortestLine(geometria, ST_MakePoint(:x, :y))) <= :treshold
