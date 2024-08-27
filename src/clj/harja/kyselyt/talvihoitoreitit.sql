-- name: lisaa-talvihoitoreitti<!
INSERT INTO talvihoitoreitti (nimi, urakka_id, luotu, luoja)
VALUES (:nimi, :urakka_id, NOW(), :kayttaja_id);

-- name: lisaa-kalusto-talvihoitoreitille<!
INSERT INTO talvihoitoreitti_kalusto (talvihoitoreitti_id, kalustotyyppi, maara)
VALUES (:talvihoitoreitti_id, :kalustotyyppi, :maara);

-- name: lisaa-reitti-talvihoitoreitille<!
INSERT INTO talvihoitoreitti_reitti (talvihoitoreitti_id, tie, alkuosa,
                                     loppuosa, alkuetaisyys, loppuetaisyys, pituus, hoitoluokka, reitti)
VALUES (:talvihoitoreitti_id, :tie, :alkuosa, :loppuosa, :alkuetaisyys, :loppuetaisyys, :pituus, :hoitoluokka,
        (select * from tierekisteriosoitteelle_viiva(:tie::INT, :alkuosa::INT, :alkuetaisyys::INT, :loppuosa::INT, :loppuetaisyys::INT)));
