-- name: hae-hoitoluokka-pisteelle
-- hakee hoitoluokan pisteelle
SELECT *
FROM hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, :tietolaji, CAST(:treshold AS INTEGER));

-- name: hae-hoitoluokka-tr-pisteelle
-- hakee hoitoluokan pisteelle
SELECT DISTINCT hoitoluokka
FROM hoitoluokka
WHERE tie = :tie
      AND tietolajitunniste = :tietolajitunniste::hoitoluokan_tietolajitunniste
      AND ((aosa <= :aosa AND aet <= :aet
            AND :aosa <= losa AND :aet <= let) OR
           (aosa <= :losa AND aet <= :let
            AND :losa <= losa AND :let <= let));

-- name: vie-hoitoluokkatauluun!
-- vie entryn hoitoluokkatauluun
INSERT INTO hoitoluokka (tie, aosa, aet, losa, let, hoitoluokka, geometria, tietolajitunniste, paivitetty)
VALUES
  (:tie, :aosa, :aet, :losa, :let, :hoitoluokka, ST_GeomFromText(:geometria) :: GEOMETRY,
             :tietolajitunniste :: hoitoluokan_tietolajitunniste, current_timestamp);

-- name: tuhoa-hoitoluokkadata!
-- poistaa tietyn tietolajitunnisteen hoitoluokkatiedot
DELETE FROM hoitoluokka
WHERE tietolajitunniste = :tietolaji :: hoitoluokan_tietolajitunniste;

-- name: tarkista-hoitoluokkadata
SELECT count(*) as lkm
FROM hoitoluokka WHERE tietolajitunniste = :hoitoluokkatyyppi ::hoitoluokan_tietolajitunniste;
