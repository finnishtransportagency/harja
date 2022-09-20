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
(tierekisteriosoite, sijainti, pituus, ajoratojen_pituus, urakka_id, luotu, luoja) VALUES
    (ROW (:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,
     (select * from tierekisteriosoitteelle_viiva(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT) as sijainti),
     :pituus, :ajoratojen_pituus, :urakka_id, NOW(), :kayttaja_id)
RETURNING id;

-- name: paivita-rajoitusalue!
UPDATE rajoitusalue
SET tierekisteriosoite = ROW (:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,
    sijainti = (select * from tierekisteriosoitteelle_viiva(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT) as sijainti),
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
       rr.hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi",
       ra.urakka_id,
       rr.luotu as luotu,
       rr.luoja as luoja,
       rr.muokattu as muokattu,
       rr.muokkaaja as muokkaaja
FROM rajoitusalue ra JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id
WHERE ra.poistettu = FALSE
  AND rr.poistettu = FALSE
  AND rr.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
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
       rr.hoitokauden_alkuvuosi                                            AS "hoitokauden-alkuvuosi",
       ra.urakka_id,
       (SELECT SUM(rp.maara)
        FROM suolatoteuma_reittipiste AS rp
                 JOIN toteuma tot ON (tot.id = rp.toteuma AND tot.poistettu = false and
                                      tot.alkanut BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE)
                 JOIN materiaalikoodi mk ON rp.materiaalikoodi = mk.id
        WHERE tot.urakka = :urakka-id
          AND ST_DWithin(ra.sijainti, rp.sijainti::geometry, 0)
          AND rp.aika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
        GROUP BY tot.urakka)                                               as suolatoteumat,
       (SELECT SUM(mat.maara)
        FROM toteuma tot
                 LEFT JOIN toteuman_reittipisteet tr ON tr.toteuma = tot.id -- Rajoitetaan nämä vain formiaatteihin
                 LEFT JOIN LATERAL unnest(tr.reittipisteet) AS trp ON TRUE
                 LEFT JOIN LATERAL unnest(trp.materiaalit) as mat ON TRUE
            AND mat.materiaalikoodi in
                (SELECT id FROM materiaalikoodi WHERE materiaalityyppi = 'formiaatti'::materiaalityyppi)
        WHERE tot.urakka = :urakka-id
          AND tot.poistettu = false
          AND tot.alkanut BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE
          AND ST_DWithin(ra.sijainti, trp.sijainti::geometry, 0))         as formiaattitoteumat,
       rr.formiaatti                                                       as "formiaatti?",
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
WITH formiaatit AS (
        SELECT id FROM materiaalikoodi WHERE materiaalityyppi = 'formiaatti'::materiaalityyppi)
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
      rajoitusalue ra,
      materiaalikoodi mk
WHERE tot.poistettu = FALSE
  AND tot.urakka = :urakka-id
  AND tot.alkanut BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE
  AND mk.id = rp.materiaalikoodi
  AND ra.id = :rajoitusalue-id
  AND ST_DWithin(ra.sijainti, rp.sijainti::geometry, 0)
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
         LEFT JOIN LATERAL unnest(tr.reittipisteet) AS trp ON TRUE AND trp.aika BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE
         LEFT JOIN LATERAL unnest(trp.materiaalit) as mat ON TRUE AND mat.materiaalikoodi in (SELECT id FROM formiaatit),
     materiaalikoodi mk,
     rajoitusalue ra
WHERE ra.id = :rajoitusalue-id
  AND mat.materiaalikoodi = mk.id
  AND tot.poistettu = FALSE
  AND tot.urakka = :urakka-id
  AND ST_DWithin(ra.sijainti, trp.sijainti::geometry, 0)
  AND tot.alkanut BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE
GROUP BY pvm, mk.id
ORDER BY pvm ASC, "materiaali-nimi" ASC;

-- name: hae-rajoitusalueen-paivan-toteumat
WITH rageom AS (
    SELECT sijainti
    FROM rajoitusalue ra
    WHERE ra.id = :rajoitusalue-id AND ra.poistettu = FALSE
),
 formiaatit AS (
     SELECT id FROM materiaalikoodi WHERE materiaalityyppi = 'formiaatti'::materiaalityyppi
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
  AND ST_DWithin(rageom.sijainti, rp.sijainti::geometry, 0)
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
         LEFT JOIN LATERAL unnest(trp.materiaalit) as mat ON TRUE AND mat.materiaalikoodi in (SELECT id FROM formiaatit),
     materiaalikoodi mk,
     rageom
WHERE mat.materiaalikoodi = mk.id
  AND mat.materiaalikoodi = :materiaali-id
  AND tot.urakka = :urakka-id
  AND ST_DWithin(rageom.sijainti, trp.sijainti::geometry, 0)
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
       rr.hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi",
       ra.urakka_id,
       rr.luotu as luotu,
       rr.luoja as luoja,
       rr.muokattu as muokattu,
       rr.muokkaaja as muokkaaja
FROM rajoitusalue ra JOIN rajoitusalue_rajoitus rr ON rr.rajoitusalue_id = ra.id
WHERE ra.poistettu = FALSE
  AND rr.poistettu = FALSE
  AND  ra.id = :rajoitusalue_id
  AND rr.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi;

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
       rr.hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi",
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
    (:rajoitusalue_id, :suolarajoitus, :formiaatti, :hoitokauden-alkuvuosi, NOW(), :kayttaja_id)
RETURNING id;

-- name: paivita-suolarajoitus!
UPDATE rajoitusalue_rajoitus
SET  rajoitusalue_id = :rajoitusalue_id,
     suolarajoitus = :suolarajoitus,
     formiaatti = :formiaatti,
     hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi,
     muokattu = NOW(),
     muokkaaja = :kayttaja_id
WHERE id = :id;

-- name: poista-suolarajoitus<!
UPDATE rajoitusalue_rajoitus SET poistettu = true
WHERE rajoitusalue_id = :rajoitusalue_id
  AND ((:poista-tulevat::TEXT IS NOT NULL AND hoitokauden_alkuvuosi >= :hoitokauden-alkuvuosi)
        OR (:poista-tulevat::TEXT IS NULL AND hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi))
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


-- name: tallenna-talvisuolan-kayttoraja-alueurakka<!
INSERT INTO suolasakko (maara, vainsakkomaara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, tyyppi, luotu,
                        luoja, talvisuolaraja, kaytossa)
VALUES (:suolasakko-tai-bonus-maara, :vain-sakko-maara, :hoitokauden_alkuvuosi, :maksukuukausi, :indeksi, :urakka-id,
        'kokonaismaara'::SUOLASAKKO_TYYPPI, NOW(), :kayttaja, :talvisuolan-kayttoraja, :suolasakko-kaytossa);

-- name: paivita-talvisuolan-kayttoraja-alueurakka!
UPDATE suolasakko
   SET maara = :suolasakko-tai-bonus-maara, vainsakkomaara = :vain-sakko-maara, maksukuukausi = :maksukuukausi,
       indeksi = :indeksi, muokattu = NOW(), muokkaaja = :kayttaja,
       talvisuolaraja = :talvisuolan-kayttoraja, kaytossa = :suolasakko-kaytossa
 WHERE id = :id;

-- name: hae-talvisuolan-kayttoraja-alueurakka
SELECT id, hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi", indeksi, urakka as "urakka-id",
       muokattu, muokkaaja, luotu, luoja, tyyppi, kaytossa as "suolasakko-kaytossa",
       maara as "suolasakko-tai-bonus-maara", vainsakkomaara as "vain-sakko-maara", maksukuukausi,
       talvisuolaraja as "talvisuolan-kayttoraja"
  FROM suolasakko s
 WHERE hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
   AND urakka = :urakka-id
   AND tyyppi = 'kokonaismaara'::suolasakko_tyyppi;

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

-- name: hae-rajoitusalueiden-suolasanktio
SELECT id, hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi", indeksi, urakka as "urakka-id",
       muokattu, muokkaaja, luotu, luoja, kaytossa, tyyppi, maara as sanktio_ylittavalta_tonnilta
  FROM suolasakko s
 WHERE hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
   AND urakka = :urakka-id
   AND tyyppi = 'rajoitusalue'::suolasakko_tyyppi;

-- name: onko-tierekisteriosoite-paallekainen
SELECT ra.id as rajoitusalue_id, rr.id as rajoitus_id, rr.suolarajoitus, rr.hoitokauden_alkuvuosi, ra.pituus, ra.ajoratojen_pituus, ra.urakka_id,
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
     -- Annetut alkuosa ja loppusa ylittävät rajoitusalueet alku ja loppuosasta |----*----*----|
         (:aosa != :losa AND ((:aosa < (ra.tierekisteriosoite).aosa AND :losa > (ra.tierekisteriosoite).losa)))
         OR
         -- Annettu alkuosa osuu kannassa olevan väliin
         (:aosa != :losa AND ((:aosa > (ra.tierekisteriosoite).aosa AND :aosa < (ra.tierekisteriosoite).losa)
             -- Sallitaan tilanne, jossa uusi rajoitusalue alkaa samasta pisteestä, mihin joku toinen loppuu
             OR (:aosa = (ra.tierekisteriosoite).losa AND (ra.tierekisteriosoite).let - 1 >= :aet)
             ))
         OR
         -- Annettu loppuosa osuu kannassa olevan väliin
         (:aosa != :losa AND ((:losa > (ra.tierekisteriosoite).aosa AND :losa < (ra.tierekisteriosoite).losa)
             OR (:losa = (ra.tierekisteriosoite).losa AND (ra.tierekisteriosoite).let <= :let)
             OR (:losa = (ra.tierekisteriosoite).aosa AND (ra.tierekisteriosoite).aet <= :let)))
         -- Alkuosa ja loppuosa on samat, tarkista etäisyydet
         OR
         -- Kun alkuosa ja loppuosa on samat
         (:aosa = :losa AND (
             -- Alkuosa osuu kokonaan kannassa olevien aosan ja losan väliin
                 (:aosa > (ra.tierekisteriosoite).aosa AND :aosa < (ra.tierekisteriosoite).losa)
                 -- Alkuosa osuu osittain kannassa olevien aosan ja losan väliin
                 OR (:aosa = (ra.tierekisteriosoite).losa AND :aet < (ra.tierekisteriosoite).let))));


-- name: hae-pohjavesialueidenurakat
SELECT DISTINCT ON (u.id) u.id, u.nimi
  FROM urakka u,
       pohjavesialue p
         JOIN pohjavesialue_talvisuola pt ON pt.pohjavesialue = p.tunnus AND pt.talvisuolaraja IS NOT NULL
 WHERE pt.urakka = u.id
   AND  NOT EXISTS ( SELECT FROM rajoitusalue WHERE urakka_id = u.id);


-- name: hae-urakan-siirrettavat-pohjavesialueet
SELECT MIN(pt.hoitokauden_alkuvuosi)                      AS "hoitokauden-alkuvuosi",
       MIN(p.nimi)                                        AS nimi,
       MIN(p.tunnus)                                      AS tunnus,
       MIN(p."tr_numero")                                 AS tie,
       MIN(p."tr_alkuosa")                                AS aosa,
       MIN(p."tr_alkuetaisyys")                           AS aet,
       MAX(p."tr_loppuosa")                               AS losa,
       MAX(p."tr_loppuetaisyys")                          AS let,
       MIN(p.luoja)                                       AS luoja,
       MIN(pt.urakka)                                     AS urakkaid,
       MIN(pt.talvisuolaraja)                             AS talvisuolaraja,
       concat(p.tunnus, p."tr_numero", pt.talvisuolaraja) AS tunniste
FROM pohjavesialue_talvisuola pt
         JOIN pohjavesialue p ON pt.pohjavesialue = p.tunnus AND p."tr_numero" = pt.tie
WHERE pt.urakka = :urakkaid
  AND pt.talvisuolaraja IS NOT NULL
GROUP BY tunniste;
