-- name: lisaa-talvihoitoreitti<!
INSERT INTO talvihoitoreitti (nimi, urakka_id, ulkoinen_id, varikoodi, tr_maara, ka_maara, kup_maara, luotu, luoja)
VALUES (:nimi, :urakka_id, :ulkoinen_id, :varikoodi, :tr_maara, :ka_maara, :kup_maara,NOW(), :kayttaja_id);

-- name: lisaa-sijainti-talvihoitoreitille<!
INSERT INTO talvihoitoreitti_sijainti (talvihoitoreitti_id, tie, alkuosa,
                                       loppuosa, alkuetaisyys, loppuetaisyys, pituus_m, hoitoluokka, reitti)
VALUES (:talvihoitoreitti_id, :tie, :alkuosa, :loppuosa, :alkuetaisyys, :loppuetaisyys, :pituus, :hoitoluokka,
        (SELECT *
           FROM tierekisteriosoitteelle_viiva(:tie::INT, :alkuosa::INT, :alkuetaisyys::INT, :loppuosa::INT,
                                              :loppuetaisyys::INT)));

-- name: hae-urakan-talvihoitoreitit
SELECT tr.id,
       tr.nimi,
       tr.urakka_id,
       tr.ulkoinen_id,
       tr.varikoodi,
       tr.tr_maara,
       tr.ka_maara,
       tr.kup_maara,
       tr.muokattu,
       tr.muokkaaja,
       tr.luotu,
       tr.luoja
  FROM talvihoitoreitti tr
 WHERE tr.urakka_id = :urakka_id
   AND tr.poistettu = FALSE
 GROUP BY tr.id
 ORDER BY tr.id;

-- name: hae-sijainti-talvihoitoreitille
SELECT trr.id,
       trr.tie,
       trr.alkuosa,
       trr.loppuosa,
       trr.alkuetaisyys,
       trr.loppuetaisyys,
       (trr.pituus_m::FLOAT / 1000)                    AS pituus,         -- Muutetaan metrit kilometreiksi
       trr.hoitoluokka,
       trr.reitti::geometry,
       ((SELECT laske_tr_osoitteen_pituus(trr.tie, trr.alkuosa, trr.alkuetaisyys, trr.loppuosa,
                                          trr.loppuetaisyys))::FLOAT / 1000)
                                                       AS laskettu_pituus -- Lasketaan pituus geometriasta, eikä luoteta sokeasti urakoitsijan raportoimaan pituuteen
  FROM talvihoitoreitti_sijainti trr
 WHERE trr.talvihoitoreitti_id = :talvihoitoreitti_id
 GROUP BY trr.id;

-- name: hae-talvihoitoreitti-ulkoisella-idlla
SELECT tr.id,
       tr.nimi,
       tr.tr_maara,
       tr.ka_maara,
       tr.kup_maara,
       tr.urakka_id,
       tr.ulkoinen_id,
       tr.muokattu,
       tr.muokkaaja,
       tr.luotu,
       tr.luoja
  FROM talvihoitoreitti tr
 WHERE tr.ulkoinen_id = :ulkoinen_id
   AND tr.urakka_id = :urakka_id
   AND tr.poistettu = FALSE;

-- name: poista-talvihoitoreitin-sijainnit!
DELETE
  FROM talvihoitoreitti_sijainti
 WHERE talvihoitoreitti_id = :talvihoitoreitti_id;

-- name: paivita-talvihoitoreitti<!
UPDATE talvihoitoreitti
   SET nimi      = :nimi,
       tr_maara  = :tr_maara,
       ka_maara  = :ka_maara,
       kup_maara = :kup_maara,
       muokattu  = NOW(),
       muokkaaja = :kayttaja_id
 WHERE id = :talvihoitoreitti_id;

-- name: poista-talvihoitoreitti!
UPDATE talvihoitoreitti SET poistettu = TRUE
 WHERE ulkoinen_id = :ulkoinen_id::TEXT
   AND urakka_id = :urakka_id::INT;

-- name: hae-leikkaavat-geometriat
-- Tarkista onko urakalla jo samalle tielle osuvia geometrioita
SELECT trs.id, trs.tie, trs.alkuosa, trs.loppuosa, trs.alkuetaisyys, trs.loppuetaisyys
  FROM talvihoitoreitti_sijainti trs
            -- Ei verrata itseensä. Jos ulkoinen- täsmää, niin siihen ei verrata, jotta voidaan päivittää olemassa olevaa reittiä
           JOIN talvihoitoreitti tr ON trs.talvihoitoreitti_id = tr.id AND tr.ulkoinen_id != :ulkoinen-id AND tr.poistettu = FALSE
           JOIN urakka u ON tr.urakka_id = u.id AND u.id = :urakka_id
 WHERE ST_Intersects(trs.reitti::geometry, (SELECT *
                                    FROM tierekisteriosoitteelle_viiva(:tie::INT, :aosa::INT, :aet::INT, :losa::INT,
                                                                       :let::INT))::geometry);
