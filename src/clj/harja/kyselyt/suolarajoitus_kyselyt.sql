-- name: hae-suolarajoitusalue
SELECT id,
       (tierekisteriosoite).tie AS tie,
       (tierekisteriosoite).aosa AS aosa,
       (tierekisteriosoite).aet AS aet,
       (tierekisteriosoite).losa AS losa,
       (tierekisteriosoite).let AS let,
       pituus,
       ajoratojen_pituus,
       (select array_agg(row( tunnus, nimi)) from leikkaavat_pohjavesialueet((tierekisteriosoite).tie::int,
                                                 (tierekisteriosoite).aosa::int,
                                                 (tierekisteriosoite).aet::int,
                                                 (tierekisteriosoite).losa::int,
                                                 (tierekisteriosoite).let::int)) as pohjavesialueet,
       urakka_id,
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
       (select array_agg(row( tunnus, nimi)) from leikkaavat_pohjavesialueet((ra.tierekisteriosoite).tie::int,
                                                 (ra.tierekisteriosoite).aosa::int,
                                                 (ra.tierekisteriosoite).aet::int,
                                                 (ra.tierekisteriosoite).losa::int,
                                                 (ra.tierekisteriosoite).let::int)) as pohjavesialueet,
       rr.id as rajoitus_id,
       rr.suolarajoitus as suolarajoitus,
       rr.formiaatti as formiaatti,
       rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       ra.urakka_id,
       rr.luotu as luotu,
       rr.luoja as luoja,
       rr.muokattu as muokattu,
       rr.muokkaaja as muokkaaja
FROM rajoitusalue ra JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id
WHERE ra.poistettu = FALSE
  AND rr.poistettu = FALSE
  AND rr.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND ra.urakka_id = :urakka_id
  ORDER BY suolarajoitus DESC;

-- name: hae-suolarajoitus
SELECT ra.id as rajoitusalue_id,
       (ra.tierekisteriosoite).tie,
       (ra.tierekisteriosoite).aosa AS aosa,
       (ra.tierekisteriosoite).aet AS aet,
       (ra.tierekisteriosoite).losa AS losa,
       (ra.tierekisteriosoite).let AS let,
       ra.pituus AS pituus,
       ra.ajoratojen_pituus as ajoratojen_pituus,
       (select array_agg(row( tunnus, nimi)) from leikkaavat_pohjavesialueet((ra.tierekisteriosoite).tie::int,
                                                 (ra.tierekisteriosoite).aosa::int,
                                                 (ra.tierekisteriosoite).aet::int,
                                                 (ra.tierekisteriosoite).losa::int,
                                                 (ra.tierekisteriosoite).let::int)) as pohjavesialueet,
       rr.id as rajoitus_id,
       rr.suolarajoitus as suolarajoitus,
       rr.formiaatti as formiaatti,
       rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       ra.urakka_id,
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
       ra.pituus AS pituus,
       ra.ajoratojen_pituus as ajoratojen_pituus,
       (select array_agg(row( tunnus, nimi)) from leikkaavat_pohjavesialueet((ra.tierekisteriosoite).tie::int,
                                                 (ra.tierekisteriosoite).aosa::int,
                                                 (ra.tierekisteriosoite).aet::int,
                                                 (ra.tierekisteriosoite).losa::int,
                                             (ra.tierekisteriosoite).let::int)) as pohjavesialueet,
       rr.suolarajoitus as suolarajoitus,
       rr.formiaatti as formiaatti,
       rr.hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       ra.urakka_id,
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
  AND ((:poista-tulevat::TEXT IS NOT NULL AND hoitokauden_alkuvuosi >= :hoitokauden_alkuvuosi)
        OR (:poista-tulevat::TEXT IS NULL AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi))
  AND poistettu = false
RETURNING *;

-- name: hae-leikkaavat-pohjavesialueet-tierekisterille
select * from leikkaavat_pohjavesialueet(:tie::int, :aosa::int, :aet::int, :losa::int, :let::int);

-- name: hae-talvisuolan-kokonaiskayttoraja
SELECT ut.maara as talvisuolan_kayttoraja
  FROM urakka_tehtavamaara ut
 WHERE ut.tehtava = (SELECT id
                       FROM toimenpidekoodi
                      WHERE taso = 4
                        AND suunnitteluyksikko = 'kuivatonnia'
                        AND suoritettavatehtava = 'suolaus')
  AND ut."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi
  AND ut.urakka = :urakka-id;

-- name: hae-talvisuolan-sanktiot
SELECT id, hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi", indeksi, urakka as "urakka-id",
       muokattu, muokkaaja, luotu, luoja, kaytossa, tyyppi, maara as sanktio_ylittavalta_tonnilta
  FROM suolasakko s
WHERE hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
  AND urakka = :urakka-id
  AND tyyppi = 'kokonaismaara'::suolasakko_tyyppi;

-- name: hae-rajoitusalueiden-suolasanktio
SELECT id, hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi", indeksi, urakka as "urakka-id",
       muokattu, muokkaaja, luotu, luoja, kaytossa, tyyppi, maara as sanktio_ylittavalta_tonnilta
FROM suolasakko s
WHERE hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
  AND urakka = :urakka-id
  AND tyyppi = 'rajoitusalue'::suolasakko_tyyppi;

-- name: paivita-talvisuolan-kayttoraja!
UPDATE suolasakko SET maara = :sanktio_ylittavalta_tonnilta,
                      hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi,
                      kaytossa = :kaytossa,
                      tyyppi = 'kokonaismaara'::suolasakko_tyyppi,
                      indeksi = :indeksi,
                      muokattu = NOW(),
                      muokkaaja = :kayttaja-id
WHERE id = :id;

-- name: tallenna-talvisuolan-kayttoraja!
INSERT INTO suolasakko (maara, urakka, hoitokauden_alkuvuosi, kaytossa, tyyppi, indeksi, luotu, luoja)
VALUES (:sanktio_ylittavalta_tonnilta, :urakka-id, :hoitokauden-alkuvuosi, :kaytossa, 'kokonaismaara'::suolasakko_tyyppi, :indeksi, NOW(), :kayttaja-id);

-- name: paivita-rajoitusalueen-suolasanktio!
UPDATE suolasakko SET maara = :sanktio_ylittavalta_tonnilta,
                      hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi,
                      kaytossa = :kaytossa,
                      tyyppi = 'rajoitusalue'::suolasakko_tyyppi,
                      indeksi = :indeksi,
                      muokattu = NOW(),
                      muokkaaja = :kayttaja-id
WHERE id = :id;

-- name: tallenna-rajoitusalueen-suolasanktio!
INSERT INTO suolasakko (maara, urakka, hoitokauden_alkuvuosi, kaytossa, tyyppi, indeksi, luotu, luoja)
VALUES (:sanktio_ylittavalta_tonnilta, :urakka-id, :hoitokauden-alkuvuosi, :kaytossa, 'rajoitusalue'::suolasakko_tyyppi, :indeksi, NOW(), :kayttaja-id);

