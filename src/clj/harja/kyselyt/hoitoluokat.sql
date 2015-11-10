-- name: hae-hoitoluokka-pisteelle
-- hakee hoitoluokan pisteelle
SELECT * FROM hoitoluokka_pisteelle(ST_MakePoint(:x, :y)::geometry, :tietolaji, CAST(:treshold AS INTEGER));

-- name: vie-hoitoluokkatauluun!
-- vie entryn hoitoluokkatauluun
INSERT INTO hoitoluokka (ajorata, aosa, tie, piirinro, let, losa, aet, osa, hoitoluokka, geometria, tietolajitunniste) VALUES
       (:ajorata, :aosa, :tie, :piirinro, :let, :losa, :aet, :osa, :hoitoluokka, ST_GeomFromText(:geometria)::geometry,
       :tietolajitunniste::hoitoluokan_tietolajitunniste)

-- name: tuhoa-hoitoluokkadata!
-- poistaa tietyn tietolajitunnisteen hoitoluokkatiedot
DELETE FROM hoitoluokka WHERE tietolajitunniste = :tietolaji::hoitoluokan_tietolajitunniste