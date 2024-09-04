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
        (SELECT *
           FROM tierekisteriosoitteelle_viiva(:tie::INT, :alkuosa::INT, :alkuetaisyys::INT, :loppuosa::INT,
                                              :loppuetaisyys::INT)));

-- name: hae-urakan-talvihoitoreitit
SELECT tr.id,
       tr.nimi,
       tr.urakka_id,
       tr.muokattu,
       tr.muokkaaja,
       tr.luotu,
       tr.luoja,
       ARRAY_AGG(ROW (tk.id, tk.kalustotyyppi, tk.maara)) AS kalusto
  FROM talvihoitoreitti tr
           LEFT JOIN talvihoitoreitti_kalusto tk ON tr.id = tk.talvihoitoreitti_id
          -- LEFT JOIN talvihoitoreitti_reitti trr ON tr.id = trr.talvihoitoreitti_id
 WHERE tr.urakka_id = :urakka_id
 GROUP BY tr.id;


-- name: hae-reitti-talvihoitoreitille
SELECT trr.id,
       trr.tie,
       trr.alkuosa,
       trr.loppuosa,
       trr.alkuetaisyys,
       trr.loppuetaisyys,
       (trr.pituus::float / 1000) AS pituus, -- Muutetaan metrit kilometreiksi
       trr.hoitoluokka,
       trr.reitti::geometry,
       ((SELECT laske_tr_osoitteen_pituus(trr.tie, trr.alkuosa, trr.alkuetaisyys, trr.loppuosa, trr.loppuetaisyys))::float / 1000)
           AS laskettu_pituus -- Lasketaan pituus geometriasta, eikä luoteta sokeasti urakoitsijan raportoimaan pituuteen
  FROM talvihoitoreitti_reitti trr
 WHERE trr.talvihoitoreitti_id = :talvihoitoreitti_id
