-- name: hae-hoitoluokka-pisteelle
-- hakee hoitoluokan pisteelle
SELECT * FROM hoitoluokka_pisteelle(ST_MakePoint(:x, :y)::geometry, :hoitoluokka, CAST(:treshold AS INTEGER));
