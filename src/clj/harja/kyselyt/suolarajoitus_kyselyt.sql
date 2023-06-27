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
(tierekisteriosoite, sijainti, pituus, ajoratojen_pituus, urakka_id, luotu, luoja, tierekisteri_muokattu) VALUES
    (ROW (:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,
     (select * from tierekisteriosoitteelle_viiva(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT) as sijainti),
     :pituus, :ajoratojen_pituus, :urakka_id, NOW(), :kayttaja_id, true)
RETURNING id;

-- name: paivita-rajoitusalue!
UPDATE rajoitusalue
SET tierekisteriosoite = ROW (:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,
    sijainti = (select * from tierekisteriosoitteelle_viiva(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT) as sijainti),
    pituus = :pituus,
    ajoratojen_pituus = :ajoratojen_pituus,
    urakka_id = :urakka_id,
    muokattu = NOW(),
    muokkaaja = :kayttaja_id,
    tierekisteri_muokattu = :tierekisteri_muokattu?
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
  ORDER BY suolarajoitus DESC, (ra.tierekisteriosoite).tie ASC, (ra.tierekisteriosoite).aosa ASC, (ra.tierekisteriosoite).aet ASC;

-- name: hae-rajoitusalueet-summatiedoin
WITH suola AS (
    SELECT SUM(rp.maara) as maara,
           CASE
               WHEN mk.materiaalityyppi = 'formiaatti' THEN 'formiaatti'
               ELSE 'talvisuola'
           END as tyyppi,
           ra.id as rajoitusalue_id
     FROM suolatoteuma_reittipiste AS rp
              JOIN toteuma tot ON (tot.id = rp.toteuma AND tot.poistettu = false and
                                   -- Otetaan toteumiin laajempi aikamääre, koska toteuma voi alkaa edellisenä päivänä
                                   tot.alkanut BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE)
              JOIN materiaalikoodi mk ON rp.materiaalikoodi = mk.id,
          rajoitusalue ra
     WHERE tot.urakka = :urakka-id
       AND ra.poistettu = FALSE
       AND ra.urakka_id = :urakka-id
       AND rp.rajoitusalue_id = ra.id
       AND rp.aika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
     GROUP BY tot.urakka, ra.id, mk.materiaalityyppi
)
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
       (SELECT SUM(s.maara) FROM suola s WHERE s.tyyppi = 'talvisuola' AND s.rajoitusalue_id = ra.id)    AS suolatoteumat,
       (SELECT SUM(s.maara) FROM suola s WHERE s.tyyppi = 'formiaatti' AND s.rajoitusalue_id = ra.id)    AS formiaattitoteumat,
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
SELECT sr.materiaalikoodi             AS materiaali_id,
       mk.nimi                        AS "materiaali-nimi",
       date_trunc('day', tot.alkanut) AS pvm,
       SUM(sr.maara)                  AS suolamaara,
       null                           AS formiaattimaara,
       count(sr.maara)                AS suolalukumaara,
       null                           AS formiaattilukumaara,
       k.jarjestelma                  AS "koneellinen?"
FROM toteuma tot
     JOIN suolatoteuma_reittipiste sr ON sr.toteuma = tot.id AND sr.rajoitusalue_id = :rajoitusalue-id
     JOIN materiaalikoodi mk ON mk.id = sr.materiaalikoodi AND mk.materiaalityyppi IN ('talvisuola', 'erityisalue')
     JOIN kayttaja k ON tot.luoja = k.id
WHERE tot.poistettu = FALSE
  AND tot.urakka = :urakka-id
  AND tot.alkanut BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE
  AND sr.aika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND sr.materiaalikoodi = mk.id
GROUP BY pvm, sr.materiaalikoodi, mk.nimi, k.jarjestelma
UNION
SELECT sr.materiaalikoodi             AS materiaali_id,
       mk.nimi                        AS "materiaali-nimi",
       date_trunc('day', tot.alkanut) AS pvm,
       null                           AS suolamaara,
       SUM(sr.maara)                  AS formiaattimaara,
       null                           AS suolalukumaara,
       count(sr.maara)                AS formiaattilukumaara,
       k.jarjestelma                  AS "koneellinen?"
FROM toteuma tot
     JOIN suolatoteuma_reittipiste sr ON sr.toteuma = tot.id AND sr.rajoitusalue_id = :rajoitusalue-id
     JOIN materiaalikoodi mk ON mk.id = sr.materiaalikoodi AND mk.materiaalityyppi = 'formiaatti'
     JOIN kayttaja k ON tot.luoja = k.id
WHERE tot.poistettu = FALSE
  AND tot.urakka = :urakka-id
  AND tot.alkanut BETWEEN :alkupvm::DATE - INTERVAL '1 day' AND :loppupvm::DATE
  AND sr.aika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND sr.materiaalikoodi = mk.id
GROUP BY pvm, sr.materiaalikoodi, mk.nimi, k.jarjestelma
ORDER BY pvm ASC, "materiaali-nimi" ASC;

-- name: hae-rajoitusalueen-paivan-toteumat
SELECT tot.id AS id,
       tot.alkanut AS alkanut,
       tot.paattynyt AS paattynyt,
       SUM(rp.maara) as maara,
       concat(tot.tyokonetyyppi, ', ', tot.tyokonetunniste, ', ', tot.tyokoneen_lisatieto) as lisatieto
FROM toteuma tot
     JOIN suolatoteuma_reittipiste rp
             ON rp.toteuma = tot.id
                AND rp.materiaalikoodi = :materiaali-id
                AND rp.rajoitusalue_id = :rajoitusalue-id
     JOIN kayttaja k ON tot.luoja = k.id AND k.jarjestelma = :koneellinen?
WHERE tot.urakka = :urakka-id
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
UPDATE rajoitusalue SET poistettu = true, tierekisteri_muokattu = true
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
-- Kokonaiskäyttöraja on voimassa vain, jos sopimuksen tehtävämäärät on tallennettu
SELECT ut.maara as talvisuolan_kayttoraja
  FROM urakka_tehtavamaara ut
 WHERE ut.tehtava = (SELECT id
                       from tehtava
                      WHERE suunnitteluyksikko = 'kuivatonnia'
                        AND suoritettavatehtava = 'suolaus')
  AND ut."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi
  AND ut.urakka = :urakka-id
  AND true = (SELECT tallennettu
                FROM sopimuksen_tehtavamaarat_tallennettu
               WHERE urakka = :urakka-id
               LIMIT 1);

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

-- name: hae-suunniteltu-suolan-kaytto-hoitovuosittain-alueurakalle
SELECT s.hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi", s.talvisuolaraja
FROM suolasakko s
     JOIN urakka u on s.urakka = u.id AND u.tyyppi = 'hoito'
WHERE s.urakka = :urakka-id
  AND s.tyyppi = 'kokonaismaara'::suolasakko_tyyppi
  and s.kaytossa = true
ORDER BY s.hoitokauden_alkuvuosi asc;

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
             OR (:aosa = (ra.tierekisteriosoite).losa AND (ra.tierekisteriosoite).let - 1 >= :aet)))
         OR
         -- Annettu loppuosa osuu kannassa olevan väliin
         (:aosa != :losa
           AND (
                 -- Loppuosa selkeästi välissä
                 (:losa > (ra.tierekisteriosoite).aosa AND :losa <= (ra.tierekisteriosoite).losa)
                  -- Loppuosa välissä, mutta alkuosa edestä ulkona selkeästi
                  OR (:aosa < (ra.tierekisteriosoite).aosa AND :losa = (ra.tierekisteriosoite).losa  AND :let > (ra.tierekisteriosoite).aet AND :let <= (ra.tierekisteriosoite).let)
                  -- Loppuosa välissä, mutta alkuosa edestä ulkona vain vähän
                  OR (:aosa = (ra.tierekisteriosoite).aosa AND :aet >= (ra.tierekisteriosoite).aet AND :losa <= (ra.tierekisteriosoite).losa)))
         -- Alkuosa ja loppuosa on samat, tarkista etäisyydet
         OR
         -- Kun alkuosa ja loppuosa on samat
         (:aosa = :losa AND (
                -- Alkuosa osuu kokonaan kannassa olevien aosan ja losan väliin
                 (:aosa > (ra.tierekisteriosoite).aosa AND :aosa < (ra.tierekisteriosoite).losa)
                 OR (:aosa > (ra.tierekisteriosoite).aosa AND :aosa = (ra.tierekisteriosoite).losa AND :aet < (ra.tierekisteriosoite).let)
                 -- Alkuosa osuu väliin ja loppuosa menee yli
                 OR (:aosa = (ra.tierekisteriosoite).aosa AND :losa = (ra.tierekisteriosoite).losa AND :aet <= (ra.tierekisteriosoite).let-1 AND :let >= (ra.tierekisteriosoite).let)
                 -- Erikoistapaukset, jossa verrataan aosa = losa ja vertailtavissa rajoitusaluiessa on myös aosa = losa
                 OR (:aosa = (ra.tierekisteriosoite).aosa AND :losa = (ra.tierekisteriosoite).losa AND :aet < (ra.tierekisteriosoite).let AND :aet >= (ra.tierekisteriosoite).aet)
                 OR (:aosa = (ra.tierekisteriosoite).aosa AND :losa = (ra.tierekisteriosoite).losa AND :aet < (ra.tierekisteriosoite).aet AND :let >= (ra.tierekisteriosoite).let)
                 -- Alkuosa osuu osittain kannassa olevien aosan ja losan väliin
                  OR (:aosa = (ra.tierekisteriosoite).aosa AND :aet < (ra.tierekisteriosoite).aet AND :let > (ra.tierekisteriosoite).aet AND :let <= (ra.tierekisteriosoite).let)
             )));




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

-- name: hae-rajoitusaluetta-muokanneet-urakat
SELECT ra.urakka_id as urakka_id
  FROM rajoitusalue ra
 WHERE ra.tierekisteri_muokattu = TRUE
GROUP BY ra.urakka_id;

-- name: nollaa-paivittyneet-rajoitusalueet!
UPDATE rajoitusalue SET tierekisteri_muokattu = FALSE
WHERE tierekisteri_muokattu = TRUE;

-- name: paivita-suolatoteumat-urakalle
SELECT paivita_suolatoteumat_urakalle(:urakka_id, :alkupvm::DATE, :loppupvm::DATE);

-- name: onko-urakalla-suolatoteumia
SELECT exists(
SELECT rp.maara
FROM suolatoteuma_reittipiste AS rp
         JOIN toteuma tot ON tot.id = rp.toteuma AND tot.poistettu = false
WHERE tot.urakka = :urakka-id
  AND tot.poistettu = false
LIMIT 1);


-- name: hae-urakan-siirrettavat-pohjavesialueet
select distinct on (pk.alue) pk.alue,
                             pt.hoitokauden_alkuvuosi                      AS "hoitokauden-alkuvuosi",
                             pk.nimi                                        AS nimi,
                             pk.tunnus                                   AS tunnus,
                             pk.tie                                 AS tie,
                             pk.alkuosa                                AS aosa,
                             pk.alkuet                           AS aet,
                             pk.loppuosa                               AS losa,
                             pk.loppuet                          AS let,
                             pa.luoja                                       AS luoja,
                             pt.urakka                                     AS urakkaid,
                             pk.talvisuolaraja                             AS talvisuolaraja
from pohjavesialue_kooste pk, pohjavesialue_talvisuola pt, pohjavesialue pa
where pk.tunnus = pa.tunnus
  and pa.tunnus = pt.pohjavesialue
  and pt.urakka = :urakkaid
  and pk.talvisuolaraja is not null
  and pk.rajoituksen_alkuvuosi = pt.hoitokauden_alkuvuosi;

-- name: hae-rajoitusalueiden-pituudet
SELECT u.id as "urakka-id", u.nimi as urakka_nimi, ra.id, (ra.tierekisteriosoite).tie, (ra.tierekisteriosoite).aosa,
       (ra.tierekisteriosoite).aet, (ra.tierekisteriosoite).losa, (ra.tierekisteriosoite).let,
       ra.pituus as "pituus-kannasta", ra.ajoratojen_pituus as "ajoradan-pituus-kannasta"
FROM rajoitusalue ra
     JOIN urakka u on ra.urakka_id = u.id
order by urakka_id;

-- name: hae-urakan-rajoitusaluegeometriat
SELECT r.id, r.tierekisteriosoite, r.sijainti
  FROM rajoitusalue r
 WHERE r.urakka_id = :urakka-id::INT;

-- name: hae-suolatoteumageometriat
SELECT sr.sijainti, t.alkanut
  FROM toteuma t
       JOIN suolatoteuma_reittipiste sr on sr.toteuma = t.id
 WHERE t.urakka = :urakka-id::INT
  AND t.alkanut BETWEEN :alkupaiva::DATE AND :loppupaiva::DATE + INTERVAL '1 days'
