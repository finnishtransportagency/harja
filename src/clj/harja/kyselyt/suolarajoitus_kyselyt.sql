-- name: hae-suolarajoitusalue
SELECT id,
       (tierekisteriosoite).tie AS tie,
       (tierekisteriosoite).aosa AS aosa,
       (tierekisteriosoite).aet AS aet,
       (tierekisteriosoite).losa AS losa,
       (tierekisteriosoite).let AS let,
       pituus,
       ajoratojen_pituus,
       urakka_id as "urakka-id",
       luotu,
       luoja,
       muokattu,
       muokkaaja
FROM rajoitusalue WHERE id = :id;

-- name: tallenna-rajoitusalue<!
INSERT INTO rajoitusalue
(tierekisteriosoite, pituus, ajoratojen_pituus, urakka_id, luotu, luoja) VALUES
    (ROW (:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE, :pituus, :ajoratojen_pituus, :urakka_id, NOW(), :kayttaja_id)
RETURNING id;

-- name: paivita-rajoitusalue!
UPDATE rajoitusalue
SET tierekisteriosoite = ROW (:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,
    pituus = :pituus,
    ajoratojen_pituus = :ajoratojen_pituus,
    urakka_id = :urakka_id,
    muokattu = NOW(),
    muokkaaja = :kayttaja_id
WHERE id = :id;

-- name: hae-suolarajoitukset-hoitokaudelle
SELECT ra.id as rajoitusalue_id,
       (ra.tierekisteriosoite).tie,
       (ra.tierekisteriosoite).aosa AS aosa,
       (ra.tierekisteriosoite).aet AS aet,
       (ra.tierekisteriosoite).losa AS losa,
       (ra.tierekisteriosoite).let AS let,
       ra.pituus AS pituus,
       ra.ajoratojen_pituus as ajoratojen_pituus,
       rr.id as rajoitus_id,
       rr.suolarajoitus as suolarajoitus,
       rr.formiaatti as formiaatti,
       rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       rr.luotu as luotu,
       rr.luoja as luoja,
       rr.muokattu as muokattu,
       rr.muokkaaja as muokkaaja
FROM rajoitusalue ra JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id
WHERE ra.poistettu = FALSE
  AND rr.poistettu = FALSE
  AND rr.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND ra.urakka_id = :urakka_id;

-- name: hae-suolarajoitus
SELECT ra.id as rajoitusalue_id,
       (ra.tierekisteriosoite).tie,
       (ra.tierekisteriosoite).aosa AS aosa,
       (ra.tierekisteriosoite).aet AS aet,
       (ra.tierekisteriosoite).losa AS losa,
       (ra.tierekisteriosoite).let AS let,
       ra.pituus AS pituus,
       ra.ajoratojen_pituus as ajoratojen_pituus,
       rr.suolarajoitus as suolarajoitus,
       rr.formiaatti as formiaatti,
       rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       rr.luotu as luotu,
       rr.luoja as luoja,
       rr.muokattu as muokattu,
       rr.muokkaaja as muokkaaja
FROM rajoitusalue ra JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id
WHERE ra.poistettu = FALSE
  AND rr.poistettu = FALSE
  AND  ra.id = :rajoitusalue_id
  AND rr.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi;

-- name: hae-suolarajoitukset-rajoitusalueelle
-- Tällä tarkistetaan, että onko rajoituksia olemassa rajoitusalueelle
SELECT ra.id as rajoitusalue_id,
       (ra.tierekisteriosoite).tie,
       (ra.tierekisteriosoite).aosa AS aosa,
       (ra.tierekisteriosoite).aet AS aet,
       (ra.tierekisteriosoite).losa AS losa,
       (ra.tierekisteriosoite).let AS let,
       rr.suolarajoitus as suolarajoitus,
       rr.formiaatti as formiaatti,
       rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       rr.luotu as luotu,
       rr.luoja as luoja,
       rr.muokattu as muokattu,
       rr.muokkaaja as muokkaaja
FROM rajoitusalue ra JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id
WHERE ra.poistettu = FALSE
  AND rr.poistettu = FALSE
  AND ra.id = :rajoitusalue_id;

-- name: poista-suolarajoitusalue<!
UPDATE rajoitusalue SET poistettu = true
 WHERE id = :id
   AND poistettu = false
RETURNING *;

-- name: tallenna-suolarajoitus<!
INSERT INTO rajoitusalue_rajoitus
(rajoitusalue_id, suolarajoitus, formiaatti, hoitokauden_alkuvuosi, luotu, luoja) VALUES
    (:rajoitusalue_id, :suolarajoitus, :formiaatti, :hoitokauden_alkuvuosi, NOW(), :kayttaja_id)
RETURNING id;

-- name: paivita-suolarajoitus!
UPDATE rajoitusalue_rajoitus
SET  rajoitusalue_id = :rajoitusalue_id,
     suolarajoitus = :suolarajoitus,
     formiaatti = :formiaatti,
     hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi,
     muokattu = NOW(),
     muokkaaja = :kayttaja_id
WHERE id = :id;

-- name: poista-suolarajoitus<!
UPDATE rajoitusalue_rajoitus SET poistettu = true
WHERE rajoitusalue_id = :rajoitusalue_id
  AND hoitokauden_alkuvuosi >= :hoitokauden_alkuvuosi
  AND poistettu = false
RETURNING *;
