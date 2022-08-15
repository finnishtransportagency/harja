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
  ORDER BY suolarajoitus DESC, (ra.tierekisteriosoite).tie ASC, (ra.tierekisteriosoite).aosa ASC;

-- name: hae-rajoitusalueet-summatiedoin
SELECT ra.id                                                               AS rajoitusalue_id,
       (ra.tierekisteriosoite).tie                                         AS tie,
       (ra.tierekisteriosoite).aosa                                        AS aosa,
       (ra.tierekisteriosoite).aet                                         AS aet,
       (ra.tierekisteriosoite).losa                                        AS losa,
       (ra.tierekisteriosoite).let                                         AS let,
       ra.pituus                                                           AS pituus,
       ra.ajoratojen_pituus                                                AS ajoratojen_pituus,
       (select array_agg(row (tunnus, nimi))
        from leikkaavat_pohjavesialueet((ra.tierekisteriosoite).tie::int,
                                        (ra.tierekisteriosoite).aosa::int,
                                        (ra.tierekisteriosoite).aet::int,
                                        (ra.tierekisteriosoite).losa::int,
                                        (ra.tierekisteriosoite).let::int)) AS pohjavesialueet,
       rr.id                                                               AS rajoitus_id,
       rr.suolarajoitus                                                    AS suolarajoitus,
       rr.formiaatti                                                       AS formiaatti,
       rr.hoitokauden_alkuvuosi                                            AS hoitokauden_alkuvuosi,
       ra.urakka_id,
       (SELECT row (MIN(materiaali_id), SUM(maara))
        FROM tr_valin_suolatoteumat(:urakka-id::integer,
                                    (ra.tierekisteriosoite).tie::int,
                                    (ra.tierekisteriosoite).aosa::int,
                                    (ra.tierekisteriosoite).aet::int,
                                    (ra.tierekisteriosoite).losa::int,
                                    (ra.tierekisteriosoite).let::int,
                                    50::integer,
                                    :alkupvm::DATE, :loppupvm::DATE)) as suolatoteumat,
       (SELECT row (MIN(mat.materiaalikoodi), SUM(mat.maara))
        FROM toteuma tot
                 LEFT JOIN toteuman_reittipisteet tr ON tr.toteuma = tot.id -- Rajoitetaan nämä vain formiaatteihin
                 LEFT JOIN LATERAL unnest(tr.reittipisteet) AS trp ON TRUE
                 LEFT JOIN LATERAL unnest(trp.materiaalit) as mat ON TRUE AND mat.materiaalikoodi in (6,15,16),
             materiaalikoodi mk
        WHERE tot.urakka = 35
          AND tot.poistettu = false
          AND tot.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE
          AND ST_DWithin((SELECT tierekisteriosoitteelle_viiva((ra.tierekisteriosoite).tie::int,
                                                               (ra.tierekisteriosoite).aosa::int,
                                                               (ra.tierekisteriosoite).aet::int,
                                                               (ra.tierekisteriosoite).losa::int,
                                                               (ra.tierekisteriosoite).let::int)), trp.sijainti::geometry, 50)) as formiaattitoteumat,
       rr.luotu                                                            as luotu,
       rr.luoja                                                            as luoja,
       rr.muokattu                                                         as muokattu,
       rr.muokkaaja                                                        as muokkaaja
FROM rajoitusalue ra
         JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id
WHERE ra.poistettu = FALSE
  AND rr.poistettu = FALSE
  AND rr.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
  AND ra.urakka_id = :urakka-id
ORDER BY suolarajoitus DESC, (ra.tierekisteriosoite).tie ASC, (ra.tierekisteriosoite).aosa ASC;

-- name: hae-rajoitusalueen-suolatoteumasummat
WITH rageom AS (
    select (tierekisteriosoitteelle_viiva((ra.tierekisteriosoite).tie::int,
    (ra.tierekisteriosoite).aosa::int,
    (ra.tierekisteriosoite).aet::int,
    (ra.tierekisteriosoite).losa::int,
    (ra.tierekisteriosoite).let::int)) as sijainti
    FROM rajoitusalue ra
    WHERE ra.id = :rajoitusalue-id AND ra.poistettu = FALSE
    )
SELECT rp.materiaalikoodi             AS materiaali_id,
       mk.nimi                        AS "materiaali-nimi",
       date_trunc('day', tot.alkanut) AS pvm,
       SUM(rp.maara)                  AS suolamaara,
       null                           AS formiaattimaara,
       count(rp.maara)                AS suolalukumaara,
       null                           AS formiaattilukumaara,
       TRUE                           AS koneellinen
FROM toteuma tot
         LEFT JOIN suolatoteuma_reittipiste rp ON rp.toteuma = tot.id, -- Talvisuolat saadaan täältä
      rageom,
      materiaalikoodi mk
WHERE tot.poistettu = FALSE
  AND tot.urakka = :urakka-id
  AND ST_DWithin(rageom.sijainti, rp.sijainti::geometry, 50)
  AND tot.luotu BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND mk.id = rp.materiaalikoodi
GROUP BY pvm, rp.materiaalikoodi, mk.nimi
UNION
SELECT mk.id                          AS materiaali_id,
       mk.nimi                        AS "materiaali-nimi",
       date_trunc('day', tot.alkanut) AS pvm,
       null                           AS suolamaara,
       SUM(mat.maara)                 AS formiaattimaara,
       null                           AS suolalukumaara,
       count(mat.maara)               AS lukumaara,
       TRUE                           AS koneellinen
FROM toteuma tot
         LEFT JOIN toteuman_reittipisteet tr ON tr.toteuma = tot.id -- Rajoitetaan nämä vain formiaatteihin
         LEFT JOIN LATERAL unnest(tr.reittipisteet) AS trp ON TRUE
         LEFT JOIN LATERAL unnest(trp.materiaalit) as mat ON TRUE AND mat.materiaalikoodi in (6,15,16),
     materiaalikoodi mk,
     rageom
WHERE mat.materiaalikoodi = mk.id
  AND tot.poistettu = FALSE
  AND tot.urakka = :urakka-id
  AND ST_DWithin(rageom.sijainti, trp.sijainti::geometry, 50)
  AND tot.luotu BETWEEN :alkupvm::DATE AND :loppupvm::DATE
GROUP BY pvm, mk.id;

-- name: hae-rajoitusalueen-paivan-toteumat
WITH rageom AS (
    select (tierekisteriosoitteelle_viiva((ra.tierekisteriosoite).tie::int,
                                          (ra.tierekisteriosoite).aosa::int,
                                          (ra.tierekisteriosoite).aet::int,
                                          (ra.tierekisteriosoite).losa::int,
                                          (ra.tierekisteriosoite).let::int)) as sijainti
    FROM rajoitusalue ra
    WHERE ra.id = :rajoitusalue-id AND ra.poistettu = FALSE
)
SELECT tot.id AS id,
       tot.alkanut AS alkanut,
       tot.paattynyt AS paattynyt,
       SUM(rp.maara) as suolamaara,
       null as formiaattimaara
FROM toteuma tot
         LEFT JOIN suolatoteuma_reittipiste rp
             ON rp.toteuma = tot.id
                AND rp.materiaalikoodi = :materiaali-id,
     rageom
WHERE tot.urakka = :urakka-id
  AND ST_DWithin(rageom.sijainti, rp.sijainti::geometry, 50)
  AND tot.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE
GROUP BY tot.id
UNION
SELECT tot.id AS id,
       tot.alkanut AS alkanut,
       tot.paattynyt AS paattynyt,
       null as suolamaara,
       SUM(mat.maara) as formiaattimaara
FROM toteuma tot
         LEFT JOIN toteuman_reittipisteet tr ON tr.toteuma = tot.id -- Rajoitetaan nämä vain formiaatteihin
         LEFT JOIN LATERAL unnest(tr.reittipisteet) AS trp ON TRUE
         LEFT JOIN LATERAL unnest(trp.materiaalit) as mat ON TRUE AND mat.materiaalikoodi in (6,15,16),
     materiaalikoodi mk,
     rageom
WHERE mat.materiaalikoodi = mk.id
  AND mat.materiaalikoodi = :materiaali-id
  AND tot.urakka = :urakka-id
  AND ST_DWithin(rageom.sijainti, trp.sijainti::geometry, 50)
  AND tot.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE
GROUP BY tot.id;

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

-- name: onko-tierekisteriosoite-paallekainen
SELECT ra.id, rr.id, rr.suolarajoitus, rr.hoitokauden_alkuvuosi, ra.pituus, ra.ajoratojen_pituus, ra.urakka_id,
       (ra.tierekisteriosoite).tie, (ra.tierekisteriosoite).aosa, (ra.tierekisteriosoite).aet, (ra.tierekisteriosoite).losa, (ra.tierekisteriosoite).let
  FROM rajoitusalue ra
      JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id AND rr.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
 WHERE ra.poistettu = FALSE
   AND rr.poistettu = FALSE
   AND ra.urakka_id = :urakka-id
   -- Ei vertailla samalla id:llä olevia
   AND (:rajoitusalue-id::INT IS NULL OR :rajoitusalue-id::INT IS NOT NULL AND ra.id != :rajoitusalue-id)

   -- tien täytyy aina mätsätä
   AND (ra.tierekisteriosoite).tie = :tie

   AND (
       -- Annettu alkuosa osuu kannassa olevan väliin
       (:aosa != :losa AND ((ra.tierekisteriosoite).aosa BETWEEN :aosa AND :losa))
        OR
        -- Annettu loppuosa osuu kannassa olevan väliin
        (:aosa != :losa AND ((ra.tierekisteriosoite).losa BETWEEN :aosa AND :losa))
       -- Alkuosa ja loppuosa on samat, tarkista etäisyydet
       OR
       -- Alkuosa ja loppuosa on samat ja ne täsmää kannassa jo olevaan alkuosaan,
       -- joten tarkistetaan onko etäisyydet annettujen etäisyyksien välissä
       (:aosa = :losa AND (ra.tierekisteriosoite).aosa = :aosa
            AND ((ra.tierekisteriosoite).aet BETWEEN :aet AND :let
                    OR
                 ((ra.tierekisteriosoite).let - 1) BETWEEN :aet AND :let)));

