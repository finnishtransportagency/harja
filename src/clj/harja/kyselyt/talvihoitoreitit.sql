-- name: lisaa-talvihoitoreitti<!
INSERT INTO talvihoitoreitti (nimi, urakka_id, ulkoinen_id, luotu, luoja)
VALUES (:nimi, :urakka_id, :ulkoinen_id, NOW(), :kayttaja_id);

-- name: lisaa-sijainti-talvihoitoreitille<!
INSERT INTO talvihoitoreitti_sijainti (talvihoitoreitti_id, tie, alkuosa,
                                     loppuosa, alkuetaisyys, loppuetaisyys, pituus_m, hoitoluokka, reitti)
VALUES (:talvihoitoreitti_id, :tie, :alkuosa, :loppuosa, :alkuetaisyys, :loppuetaisyys, :pituus, :hoitoluokka,
        (SELECT *
           FROM tierekisteriosoitteelle_viiva(:tie::INT, :alkuosa::INT, :alkuetaisyys::INT, :loppuosa::INT,
                                              :loppuetaisyys::INT)));

-- name: lisaa-kalusto-sijainnille<!
INSERT INTO talvihoitoreitti_sijainti_kalusto (talvihoitoreitti_sijainti_id, kalustotyyppi, maara)
VALUES (:sijainti_id, :kalustotyyppi, :maara);


-- name: hae-urakan-talvihoitoreitit
SELECT tr.id,
       tr.nimi,
       tr.urakka_id,
       tr.ulkoinen_id,
       tr.muokattu,
       tr.muokkaaja,
       tr.luotu,
       tr.luoja
  FROM talvihoitoreitti tr
 WHERE tr.urakka_id = :urakka_id
 GROUP BY tr.id;


-- name: hae-sijainti-talvihoitoreitille
SELECT trr.id,
       trr.tie,
       trr.alkuosa,
       trr.loppuosa,
       trr.alkuetaisyys,
       trr.loppuetaisyys,
       (trr.pituus_m::FLOAT / 1000) AS pituus,         -- Muutetaan metrit kilometreiksi
       trr.hoitoluokka,
       array_agg(row(trsk.kalustotyyppi, trsk.maara)) as kalustot,
       trr.reitti::geometry,
       ((SELECT laske_tr_osoitteen_pituus(trr.tie, trr.alkuosa, trr.alkuetaisyys, trr.loppuosa,
                                          trr.loppuetaisyys))::FLOAT / 1000)
                                  AS laskettu_pituus -- Lasketaan pituus geometriasta, eikä luoteta sokeasti urakoitsijan raportoimaan pituuteen
  FROM talvihoitoreitti_sijainti trr
       LEFT JOIN talvihoitoreitti_sijainti_kalusto trsk ON trr.id = trsk.talvihoitoreitti_sijainti_id
 WHERE trr.talvihoitoreitti_id = :talvihoitoreitti_id
GROUP BY trr.id;

-- name: hae-talvihoitoreitti-ulkoisella-idlla
SELECT tr.id,
       tr.nimi,
       tr.urakka_id,
       tr.ulkoinen_id,
       tr.muokattu,
       tr.muokkaaja,
       tr.luotu,
       tr.luoja
  FROM talvihoitoreitti tr
 WHERE tr.ulkoinen_id = :ulkoinen_id
   AND tr.urakka_id = :urakka_id;

-- name: poista-talvihoitoreitin-sijainnit!
DELETE
  FROM talvihoitoreitti_sijainti
 WHERE talvihoitoreitti_id = :talvihoitoreitti_id;

-- name: paivita-talvihoitoreitti<!
UPDATE talvihoitoreitti
   SET nimi      = :nimi,
       muokattu  = NOW(),
       muokkaaja = :kayttaja_id
 WHERE id = :talvihoitoreitti_id;

-- name: poista-talvihoitoreitti!
DELETE
  FROM talvihoitoreitti
 WHERE ulkoinen_id = :ulkoinen_id
   AND urakka_id = :urakka_id;
