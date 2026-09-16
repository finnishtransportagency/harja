-- name: hae-urakan-hoitovuoden-kirjatut-muutokset
SELECT m.id,
       m.versio,
       m.urakka,
       m.voimassa_alkaen,
       m.tyyppi,
       m.alityyppi,
       m.nimi,
       m.syy,
       m.kulu_kohdistus,
       m.luonnos,
       -- johto- ja hallintakorvausmuutosten kokonaissumma
       (SELECT SUM(kokonaissumma)
          FROM kulu k
                   JOIN mhu_muutos_kulu mmk ON (k.id = mmk.kulu AND m.id = mmk.muutos AND m.versio = mmk.versio)
                   JOIN kulu_kohdistus kk ON k.id = kk.kulu AND kk.tyyppi = 'jjh-muutos'
         WHERE k.poistettu IS FALSE
           AND kk.poistettu IS FALSE
           AND k.erapaiva BETWEEN 
               (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')) AND
               (SELECT TO_DATE(:hoitokauden_alkuvuosi + 1 || '-09-30', 'YYYY-MM-DD'))) AS "jjh-muutosten-summa",
       -- Haetaan ja järjestellään kustannusvaikutukset erikseen alikyselyllä, jotta ei tarvitse käyttää DISTINCT, eikä
       -- järjestely ole hankalaa
       COALESCE(
           (SELECT JSON_AGG(
                       JSONB_BUILD_OBJECT(
                           'kustannuslaji', kust.kustannuslaji,
                           'toimenpideinstanssi', kust.toimenpideinstanssi,
                           'summa', kust.summa,
                           'hoitokauden_alkuvuosi', kust.hoitokauden_alkuvuosi,
                           'versio', kust.versio
                       )
                       ORDER BY kust.toimenpideinstanssi, kust.hoitokauden_alkuvuosi
                   )
            FROM ONLY mhu_muutos_kustannusvaikutus kust
            WHERE kust.muutos = m.id
              AND kust.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
           ),
           '[]'::json) AS kustannusvaikutukset,
    COALESCE(
        (SELECT JSON_AGG(
                    JSONB_BUILD_OBJECT(
                        'tehtava', tjm.tehtava,
                        'suunniteltu_maara', ut.maara,
                        'maaramuutos', tjm.maaramuutos,
                        'hoitokauden_alkuvuosi', tjm.hoitokauden_alkuvuosi,
                        'versio', tjm.versio)
                    ORDER BY tjm.tehtava, tjm.hoitokauden_alkuvuosi
                )
            FROM ONLY mhu_muutos_tehtava_ja_maaraluettelo tjm
                     LEFT JOIN urakka_tehtavamaara ut
                               ON ut.urakka = :urakka
                                   AND ut."hoitokauden-alkuvuosi" = tjm.hoitokauden_alkuvuosi
                                   AND ut.poistettu IS NOT TRUE
                                   AND tjm.tehtava = ut.tehtava
            WHERE tjm.muutos = m.id
              AND tjm.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi),
           '[]'::json) AS tehtavat_ja_maarat,
       CASE
           WHEN COUNT(lii.*) = 0 THEN NULL
           ELSE JSON_AGG(DISTINCT JSONB_BUILD_OBJECT(
               'muutos', lii.muutos,
               'id', lii.liite)) END AS liitteet
-- ONLY tarvitaan, jottei kysellä historiatauluista
FROM ONLY mhu_muutos m
         LEFT JOIN ONLY mhu_muutos_liite lii ON (m.id = lii.muutos)
WHERE m.urakka = :urakka
  AND 
    CASE  
        WHEN :hae-vain-aiemmat-pysyvat-muutokset?::BOOLEAN THEN
            (m.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI AND
             m.voimassa_alkaen < (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')))
        -- Kirjatuista muutoksista taulukossa saa näyttää vain ne, joiden voimassa_alkaen osuu valitulle hoitokaudelle
        -- Haetaan kaikkien kirjattujen muutostyyppien tiedot
        ELSE
            m.tyyppi IN
            ('pysyva', 'muutostyo', 'johto-ja-hallintokorvaus') AND
            m.voimassa_alkaen BETWEEN 
                (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')) AND
                (SELECT TO_DATE(:hoitokauden_alkuvuosi + 1 || '-09-30', 'YYYY-MM-DD'))
    END
  AND m.poistettu IS FALSE
GROUP BY m.id, m.versio, m.urakka, m.voimassa_alkaen, 
         m.tyyppi, m.nimi, m.syy, m.kulu_kohdistus, m.luonnos;


-- name: rahavarausten-toteumat
SELECT rv.id,
       COALESCE(SUM(kk.summa), 0) as toteumat
  FROM kulu k
           JOIN kulu_kohdistus kk ON k.id = kk.kulu
           JOIN toimenpideinstanssi tpi ON kk.toimenpideinstanssi = tpi.id
           JOIN rahavaraus rv ON kk.rahavaraus_id = rv.id
           JOIN rahavaraus_urakka rvu ON rv.id = rvu.rahavaraus_id
           JOIN urakka u ON rvu.urakka_id = u.id AND tpi.urakka = u.id
 WHERE u.id = :urakka
   AND k.erapaiva BETWEEN (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')) 
   AND (SELECT TO_DATE(:hoitokauden_alkuvuosi + 1 || '-09-30', 'YYYY-MM-DD'))
   AND kk.poistettu IS NOT TRUE 
   AND k.poistettu IS NOT TRUE 
 GROUP BY rv.id, rv.jarjestys
 ORDER BY rv.jarjestys;

-- name: rahavarausmuutosten-syyt
SELECT rahavaraus_id AS id, syy
  FROM mhu_muutos_rahavarausmuutoksen_syy
 WHERE urakka = :urakka
   AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi;

-- name: upsert-rahavarausmuutosten-syyt!
INSERT INTO mhu_muutos_rahavarausmuutoksen_syy
(urakka, hoitokauden_alkuvuosi, rahavaraus_id, syy, luoja)
VALUES
    (:urakka, :hoitokauden_alkuvuosi, :rahavaraus_id, :syy, :kayttaja)
    ON CONFLICT (urakka, hoitokauden_alkuvuosi, rahavaraus_id)
        DO UPDATE SET
                      syy = EXCLUDED.syy,
                      muokkaaja = EXCLUDED.luoja,
                      muokattu = NOW();

-- name: luo-muutos<!
INSERT INTO mhu_muutos (
    urakka,
    tyyppi,
    nimi,
    syy,
    kulu_kohdistus,
    luonnos,
    voimassa_alkaen,
    luoja,
    luotu,
    alityyppi
  ) VALUES (
    :urakka,
    :tyyppi::MHU_MUUTOSTYYPPI,
    :nimi,
    :syy,
    :kulu_kohdistus,
    :luonnos,
    :voimassa_alkaen,
    :kayttaja,
    NOW(),
    :alityyppi::MHU_MUUTOS_ALITYYPPI
) RETURNING id, versio;

-- name: paivita-muutos<!
UPDATE mhu_muutos
   SET muokattu = NOW(),
       muokkaaja = :kayttaja,
       nimi = :nimi,
       tyyppi = :tyyppi::MHU_MUUTOSTYYPPI,
       syy = :syy,
       kulu_kohdistus = :kulu_kohdistus,
       luonnos = :luonnos,
       voimassa_alkaen = :voimassa_alkaen,
       alityyppi = :alityyppi::MHU_MUUTOS_ALITYYPPI
 WHERE id = :id
RETURNING id, versio;

-- name: poista-muutos!
UPDATE mhu_muutos
   SET muokattu  = NOW(),
       muokkaaja = :kayttaja,
       poistettu = TRUE
 WHERE id = :id
RETURNING id, versio;

-- name: luo-tai-paivita-muutos-kustannusvaikutus<!
INSERT INTO mhu_muutos_kustannusvaikutus AS kv (
    versio,
    muutos,
    kustannuslaji,
    toimenpideinstanssi,
    hoitokauden_alkuvuosi,
    summa,
    tehtavamaaramuutos_kirjattu,
    syy
  ) VALUES (
    :versio,
    :muutos_id,
    :kustannuslaji::SUUNNITTELU_OSIO,
    :toimenpideinstanssi,
    :hoitokauden_alkuvuosi,
    :summa,
    :tehtavamaaramuutos-kirjattu?,
    :syy
) ON CONFLICT (muutos, kustannuslaji, toimenpideinstanssi, hoitokauden_alkuvuosi)
DO UPDATE SET
  versio               = EXCLUDED.versio,
  kustannuslaji        = EXCLUDED.kustannuslaji,
  toimenpideinstanssi  = EXCLUDED.toimenpideinstanssi,
  summa                = EXCLUDED.summa,
  tehtavamaaramuutos_kirjattu = EXCLUDED.tehtavamaaramuutos_kirjattu,
  syy                  = EXCLUDED.syy
-- Päivitetään vain jos tulee uusi määrämuutos
-- Tai jos halutaan tallentaa muutos ilman tehtävämääriä
WHERE (kv.summa, kv.tehtavamaaramuutos_kirjattu, kv.syy)
  IS DISTINCT FROM
      (EXCLUDED.summa, EXCLUDED.tehtavamaaramuutos_kirjattu, EXCLUDED.syy);


-- name: luo-tai-paivita-erillisrahoitettu-kustannusvaikutus<!
INSERT INTO mhu_muutos_kustannusvaikutus AS kv (
    versio,
    muutos,
    kustannuslaji,
    toimenpideinstanssi,
    hoitokauden_alkuvuosi,
    summa
  ) VALUES (
    :versio,
    :muutos_id,
    :kustannuslaji::SUUNNITTELU_OSIO,
    NULL,
    :hoitokauden_alkuvuosi,
    :summa
) ON CONFLICT ( -- Erillisrahoitetuille ei anneta tpitä, joten ideksin käyttö eri
  muutos,
  kustannuslaji,
  hoitokauden_alkuvuosi,
  COALESCE(toimenpideinstanssi, -1)
) DO UPDATE SET
  versio               = EXCLUDED.versio,
  kustannuslaji        = EXCLUDED.kustannuslaji,
  toimenpideinstanssi  = EXCLUDED.toimenpideinstanssi,
  summa                = EXCLUDED.summa
WHERE (kv.summa) IS DISTINCT FROM (excluded.summa);


-- name: luo-muutos-kulu-linkitys<!
INSERT INTO mhu_muutos_kulu (versio, muutos, kulu)
VALUES (:versio, :muutos, :kulu);

-- name: paivita-muutos-kulu-linkitys!
-- Kulu luodaan aina uusiksi taustalla, joten rivi päivitetään muutos-id:n ja vanhan kulun id:n perusteella
UPDATE mhu_muutos_kulu
   SET versio = :versio,
       kulu = :uusi-kulu
 WHERE muutos = :muutos
   AND kulu = :vanha-kulu;

-- TODO Muutostyön (erillisrahoitettu) kulujen hallinnassa on poikkeuvuus, joka irtauttaa sen mhu_muutos_kulu
--       linkityksestä ja historiaseurannasta. Katsotaan myöhemmin miten tämän kanssa toimitaan.
--       Emme varmaan halua pitkällä aikavälillä, että on kahta erilaista kulujen linkityksen hallintaa.
--       JJH-muutosten kulut vs Muutostyön kulut.

-- name: paivita-muutostyo-kulukohdistus!
UPDATE kulu_kohdistus
   SET muutos = :muutos
 WHERE id = :kohdistus-id;

-- name: hae-muutostyon-kulujen-maara
-- single?: true
-- Palauttaa muutostyöhön kohdistettujen kulujen määrän, jotta voidaan tarkistaa onko kuluja jo kohdistettu
SELECT COUNT(*) AS maara
  FROM kulu_kohdistus kk
 WHERE kk.muutos = :muutos-id
   AND kk.poistettu IS NOT TRUE;

-- name: onko-muutoksella-kuluja-ennen-voimassa-paivaa?
-- single?: true
SELECT k.id FROM kulu k 
	LEFT JOIN kulu_kohdistus kk ON kk.kulu = k.id 
WHERE 
	kk.tyyppi = :tyyppi::kohdistustyyppi
  AND kk.poistettu IS FALSE  
  AND k.erapaiva < :voimassa::DATE
  AND kk.muutos = :muutos;

-- name: muutostyolle-jo-kirjatut-kulut-yhteensa
-- single?: true
SELECT COALESCE(SUM(kk.summa), 0) AS kirjattu_summa
 FROM kulu k
         JOIN kulu_kohdistus kk ON kk.kulu = k.id
WHERE
    kk.tyyppi = :tyyppi::kohdistustyyppi
  AND kk.poistettu IS FALSE
  AND kk.muutos = :muutos;

-- name: luo-jjh-kulun-kohdistus<!
INSERT INTO kulu_kohdistus (kulu, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, luotu, luoja,
                            tavoitehintainen)
VALUES (:kulu, 0, :summa,
        :toimenpideinstanssi,
        (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54'),
        'kokonaishintainen'::MAKSUERATYYPPI,
        'jjh-muutos'::KOHDISTUSTYYPPI, current_timestamp, :kayttaja,
        TRUE::BOOLEAN);

-- name: hae-jjh-muutoksen-kulut
SELECT mk.kulu AS "kulu-id"
  FROM mhu_muutos_kulu mk
 WHERE mk.muutos = :muutos
   AND mk.versio = :versio;

-- name: hae-johto-ja-hallintokorvausmuutoksen-tiedot
SELECT m.id,
       m.versio,
       json_agg(DISTINCT jsonb_build_object(
           'kulu-id', k.id,
           'pvm', k.erapaiva,
           'tavoitehinnan-muutos', k.kokonaissumma))  AS kulut
  FROM ONLY mhu_muutos m
           LEFT JOIN ONLY mhu_muutos_kulu mk ON mk.muutos = m.id
           LEFT JOIN kulu k ON mk.kulu = k.id AND k.poistettu IS FALSE
 WHERE m.id = :id
   AND m.versio = :versio
   AND m.urakka = :urakka
 GROUP BY m.id, m.versio ;

-- name: linkita-muutos-ja-liite<!
INSERT INTO mhu_muutos_liite (muutos, liite, versio)
VALUES (:muutos, :liite, :versio);

-- name: hae-muutoksen-liite-idt
SELECT liite
  FROM mhu_muutos_liite
 WHERE muutos = :muutos;

-- name: poista-muutos-liite-linkitys!
DELETE FROM mhu_muutos_liite
 WHERE muutos = :muutos
   AND liite = :liite;


-- name: hae-pysyvan-muutoksen-kustannustiedot
WITH toimenpiteet AS (
    SELECT *
    FROM (VALUES
        ('23104', 'Talvihoito'),
        ('23116', 'Liikenneympäristön hoito'),
        ('23124', 'Sorateiden hoito'),
        ('20107', 'Päällysteiden paikkaus'),
        ('20191', 'MHU Ylläpito'),
        ('14301', 'MHU Korvausinvestointi')
    ) AS t(koodi, nimi)
),
haetut_tyot AS (
    -- kustannusarvioidut työt
    SELECT COALESCE(SUM(kt.summa), 0) AS budjetoitu_summa,
           CASE
               WHEN tk.koodi = '23104' THEN 'Talvihoito'
               WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
               WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
               WHEN tk.koodi = '20107' THEN 'Päällysteiden paikkaus'
               WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
               WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           END AS toimenpide,
           CASE
               WHEN kt.kuukausi >= 10 THEN kt.vuosi
               WHEN kt.kuukausi <= 9 THEN kt.vuosi - 1
               END AS hoitokauden_alkuvuosi
    FROM toimenpide tk
        JOIN toimenpideinstanssi tpi ON tpi.toimenpide = tk.id
        JOIN kustannusarvioitu_tyo kt ON kt.toimenpideinstanssi = tpi.id
        JOIN sopimus s ON kt.sopimus = s.id
        LEFT JOIN toimenpide tp ON tp.id = tpi.toimenpide
    WHERE s.urakka = :urakka
        AND kt.rahavaraus_id IS NULL
        AND (tk.koodi IN ('23104', '23116', '23124', '20107', '20191', '14301'))
    GROUP BY tk.koodi, tk.nimi, hoitokauden_alkuvuosi

    UNION ALL

    -- kiinteähintaiset työt
    SELECT COALESCE(SUM(kt.summa), 0) AS budjetoitu_summa,
           CASE
               WHEN tk.koodi = '23104' THEN 'Talvihoito'
               WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
               WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
               WHEN tk.koodi = '20107' THEN 'Päällysteiden paikkaus'
               WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
               WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           END AS toimenpide,
           CASE
               WHEN kt.kuukausi >= 10 THEN kt.vuosi
               WHEN kt.kuukausi <= 9 THEN kt.vuosi - 1
               END AS hoitokauden_alkuvuosi
    FROM toimenpide tk
        JOIN toimenpideinstanssi tpi ON tpi.toimenpide = tk.id
        JOIN kiinteahintainen_tyo kt ON kt.toimenpideinstanssi = tpi.id
        JOIN sopimus s ON kt.sopimus = s.id
        LEFT JOIN toimenpide tp ON tp.id = tpi.toimenpide
    WHERE tpi.urakka = :urakka
        AND (tk.koodi IN ('23104', '23116', '23124', '20107', '20191', '14301'))
    GROUP BY tk.koodi, tk.nimi, hoitokauden_alkuvuosi
),
summatut_tyot AS (
    SELECT toimenpide,
           hoitokauden_alkuvuosi,
           SUM(budjetoitu_summa) AS budjetoitu_summa
    FROM haetut_tyot
    GROUP BY toimenpide, hoitokauden_alkuvuosi
)
SELECT
    m.id,
    tk.nimi AS toimenpide,
    tk.koodi AS toimenpidekoodi,
    tpi.id as toimenpideinstanssi,
    -- Haetaan ja järjestellään tehtavan määrämuutokset erikseen alikyselyllä, jotta ei tarvitse käyttää DISTINCT, eikä
    -- järjestely ole hankalaa
    COALESCE(
        (SELECT JSON_AGG(
                    JSONB_BUILD_OBJECT(
                        'tehtava', tjm.tehtava,
                        'suunniteltu_maara', ut.maara,
                        'maaramuutos', tjm.maaramuutos,
                        'hoitokauden_alkuvuosi', tjm.hoitokauden_alkuvuosi,
                        'versio', tjm.versio)
                    ORDER BY tjm.tehtava, tjm.hoitokauden_alkuvuosi
                )
           FROM ONLY mhu_muutos_tehtava_ja_maaraluettelo tjm
                LEFT JOIN urakka_tehtavamaara ut
                          ON ut.urakka = :urakka
                              AND ut."hoitokauden-alkuvuosi" = tjm.hoitokauden_alkuvuosi
                              AND ut.poistettu IS NOT TRUE
                              AND tjm.tehtava = ut.tehtava
          WHERE tjm.muutos = m.id
            AND tjm.tehtava IN (SELECT id FROM tehtava WHERE emo = tp.id)),
        '[]'::json) AS tehtavat_ja_maarat,

    -- Haetaan ja järjestellään kustannusvaikutukset erikseen alikyselyllä, jotta ei tarvitse käyttää DISTINCT, eikä
    -- järjestely ole hankalaa
    COALESCE(
        (SELECT JSON_AGG(
                    JSONB_BUILD_OBJECT(
                        'kustannuslaji', kust.kustannuslaji,
                        'toimenpideinstanssi', kust.toimenpideinstanssi,
                        'summa', kust.summa,
                        'hoitokauden_alkuvuosi', kust.hoitokauden_alkuvuosi,
                        'tehtavamaaramuutos-kirjattu?', kust.tehtavamaaramuutos_kirjattu,
                        'syy', kust.syy,
                        'versio', kust.versio
                    )
                    ORDER BY kust.toimenpideinstanssi, kust.hoitokauden_alkuvuosi
                )
           FROM ONLY mhu_muutos_kustannusvaikutus kust
          WHERE kust.muutos = m.id
            AND kust.toimenpideinstanssi = tpi.id),
        '[]'::json)
        AS kustannusvaikutukset,
    CASE
        WHEN COUNT(st.*) = 0 THEN NULL
        ELSE json_agg(DISTINCT jsonb_build_object(
            'budjetoitu_summa', st.budjetoitu_summa,
            'hoitokauden_alkuvuosi', st.hoitokauden_alkuvuosi))
    END AS budjetoidut_summat
FROM toimenpiteet tk
    LEFT JOIN toimenpide tp ON tp.koodi = tk.koodi
    LEFT JOIN toimenpideinstanssi tpi ON tp.id = tpi.toimenpide AND tpi.urakka = :urakka
    LEFT JOIN ONLY mhu_muutos m ON (m.id = :id
                                    -- FIXME Versiointi ei toimi kunnolla tapauksissa, joissa useita riveja
                                    --       liittyy yhteen muutokseen. Purettava mahdollisesti versiointia
                                    --       ja antaa alitaulujen yksittäisten rivien elää itsenäisemmin elämää historian suhteen
                                    --       mhu_muutos taulun versio voisi edustaa ylintä versionnumeroa
                                    --       joka löytyy jostakin lapsitaulun riveistä. Vanhat versiot on
                                    --       silti löydettävissä äiti muutos id:n avulla.
                                    --m.versio = :versio AND
                                    AND m.poistettu IS FALSE 
                                    AND m.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI)
    LEFT JOIN summatut_tyot st ON st.toimenpide = tk.nimi
GROUP BY m.id, tk.nimi, tk.koodi, tpi.id, tp.id, tp.jarjestys
ORDER BY tp.jarjestys;


-- name: luo-tai-paivita-tehtavan-maaramuutos<!
-- Poikkeaminen tehtävä- ja määräluettelon määrästä
INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo AS tjm (versio, muutos, tehtava, hoitokauden_alkuvuosi,
                                                        maaramuutos)
VALUES (:versio,
        :muutos-id,
        :tehtava,
        :hoitokauden_alkuvuosi,
        :maaramuutos)
    ON CONFLICT (muutos, tehtava, hoitokauden_alkuvuosi)
        DO UPDATE SET versio          = EXCLUDED.versio,
                      maaramuutos     = EXCLUDED.maaramuutos
-- Päivitetään vain jos tulee uusi määrämuutos
 WHERE (tjm.maaramuutos) IS DISTINCT FROM (excluded.maaramuutos);

-- name: poista-tehtavan-maaramuutos!
-- Poistaa määrämuutosrivin, tästä tallentuu historiarivi mhu_muutos_tehtava_ja_maaraluettelo_historia tauluun
DELETE FROM mhu_muutos_tehtava_ja_maaraluettelo
 WHERE muutos = :muutos-id
   AND tehtava = :tehtava
   AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi;


-- name: hae-tehtava-maaramuutokset
WITH urakan_tehtavat AS (
    SELECT
        tt.toimenpidekoodi           AS toimenpidekoodi,
        SUM(tt.maara)                AS maara,
        :urakka                      AS urakka,
        MAX(tt.toteuma)              AS toteuma_id,
        MAX(tt.id)                   AS toteuma_tehtava_id
      FROM toteuma_tehtava tt
     WHERE tt.urakka_id = :urakka
       AND tt.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
       AND tt.poistettu = FALSE
     GROUP BY tt.toimenpidekoodi
),
 materiaalimaara AS NOT MATERIALIZED (
     -- Aggregoi materiaalit ensin (vähemmän rivejä toteuma-joiniin)
     WITH mat_summat AS (
         SELECT tm.materiaalikoodi,
                tm.toteuma,
                SUM(tm.maara) AS maara
         FROM toteuma_materiaali tm
         WHERE tm.urakka_id = :urakka
           AND tm.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
           AND tm.poistettu IS FALSE
         GROUP BY tm.materiaalikoodi, tm.toteuma
     )
     -- Vasta aggregoinnin jälkeen JOINataan toteumaan (paljon vähemmän rivejä)
     SELECT teh.id        AS tehtava_id,
            teh.nimi      AS tehtava,
            SUM(ms.maara) AS maara,
            mk.yksikko
     FROM mat_summat ms
              JOIN toteuma t ON t.id = ms.toteuma
         AND t.urakka = :urakka
         AND t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE
         AND t.poistettu IS FALSE
              JOIN materiaalikoodi mk ON ms.materiaalikoodi = mk.id
              JOIN tehtava teh
                   ON teh.materiaaliluokka_id = mk.materiaaliluokka_id
                       AND teh."maaramitattava?" IS TRUE
                       AND (teh.materiaalikoodi_id = ms.materiaalikoodi
                           OR teh.materiaalikoodi_id IS NULL)
     GROUP BY teh.id, teh.nimi, mk.yksikko
 ),
maaramuutokset AS (
    SELECT
        tk.id                                            AS id,
        o.otsikko                                        AS toimenpide,
        tk.nimi                                          AS tehtava,
        tk.id                                            AS tehtava_id,
        mmt.versio                                       AS versio,
        mmt.syy                                          AS syy,
        mmt.lahde                                        AS lahde,
        mmt.lahde                                        AS yksikkohinnan_lahde,
        mmt.valitun_yksikkohinnan_hoitokausi             AS yksikkohinnan_alkuvuosi,
        mmt.kasin_syotetty_tavoitehintamuutos            AS syotetty_tavoitehintamuutos,
        COALESCE(kulut.summa, 0)                         AS kirjatut_kulut_summa,
        -- toteutunut määrä: materiaalitoteuma -> muuten urakan_tehtavat
        COALESCE(MAX(mm.maara), SUM(urakan_tehtavat.maara)) AS toteutunut_maara,
        SUM(ut.maara)                                    AS suunniteltu_maara,
        tk.kasin_lisattava_maara                         AS kasin_lisattava_maara,
        tk.suunnitteluyksikko                            AS yksikko,
        tr_alataso.yksiloiva_tunniste                    AS tr_tunniste, 
        :talvisuolakerroin AS talvisuola_kerroin -- ;; Kerrointa käytetään talvisuolalle, jos toteuma alle suunnitellun
      FROM tehtava tk
          JOIN tehtavaryhma tr_alataso
            ON tr_alataso.id = tk.tehtavaryhma
          JOIN tehtavaryhmaotsikko o 
            ON tr_alataso.tehtavaryhmaotsikko_id = o.id
           AND (:tehtavaryhma::TEXT IS NULL OR o.otsikko = :tehtavaryhma)
     LEFT JOIN urakka_tehtavamaara ut
            ON ut.urakka = :urakka
           AND ut."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
           AND ut.poistettu IS NOT TRUE
           AND tk.id = ut.tehtava
           AND (CAST(:tehtava AS INTEGER) IS NULL OR tk.id = :tehtava)
     LEFT JOIN urakan_tehtavat
            ON tk.id = urakan_tehtavat.toimenpidekoodi
           AND tr_alataso.yksiloiva_tunniste NOT IN ('3d5962b4-c7ca-4750-81f1-f589b9c7c52b')
     LEFT JOIN materiaalimaara mm
            ON mm.tehtava_id = tk.id
          JOIN urakka u
            ON u.id = :urakka
     -- Syy, yksikköhinnan hk, kirjattu tavoitehinta 
     LEFT JOIN LATERAL (
           SELECT mmt.*
             FROM mhu_muutos_tehtava_tiedot mmt
            WHERE mmt.urakka = :urakka
              AND mmt.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
              AND mmt.tehtava = tk.id
         ORDER BY mmt.versio DESC LIMIT 1 ) mmt ON TRUE
     -- Kirjatut kulut 
     LEFT JOIN (
           SELECT kk.tehtava    AS tehtava_id, 
                  SUM(kk.summa) AS summa
            FROM kulu k
                  JOIN kulu_kohdistus kk
                    ON k.id = kk.kulu
                   AND kk.poistettu IS NOT TRUE
           WHERE k.urakka = :urakka
             AND (:alkupvm::DATE IS NULL OR :alkupvm::DATE <= k.erapaiva)
             AND (:loppupvm::DATE IS NULL OR k.erapaiva <= :loppupvm::DATE)
             AND k.poistettu IS NOT TRUE
    GROUP BY kk.tehtava ) kulut ON kulut.tehtava_id = tk.id
 WHERE tk."maaramitattava?" IS TRUE   -- Vain määrämitattavat 
   AND ut.maara IS NOT NULL           -- Vain joilla on suunniteltu arvo 
   AND ut.maara > 0
   AND (tk.voimassaolo_alkuvuosi IS NULL OR tk.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
   AND (tk.voimassaolo_loppuvuosi IS NULL OR tk.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
   -- Rajataan pois tehtävät joilla ei ole suunnitteluyksikköä ja tehtävät joiden yksikkö on euro
   AND ((tk.suunnitteluyksikko IS NOT NULL AND tk.suunnitteluyksikko != 'euroa') OR
       tk.yksiloiva_tunniste IN ('49b7388b-419c-47fa-9b1b-3797f1fab21d',
                                '63a2585b-5597-43ea-945c-1b25b16a06e2',
                                'b3a7a210-4ba6-4555-905c-fef7308dc5ec',
                                'e32341fc-775a-490a-8eab-c98b8849f968',
                                '0c466f20-620d-407d-87b0-3cbb41e8342e',
                                'c058933e-58d3-414d-99d1-352929aa8cf9'))
GROUP BY
     tk.id, tk.nimi, o.otsikko, tk.kasin_lisattava_maara, 
     tk.suunnitteluyksikko, kulut.summa, mmt.versio, mmt.syy, mmt.lahde, 
     mmt.valitun_yksikkohinnan_hoitokausi, mmt.kasin_syotetty_tavoitehintamuutos, tr_alataso.yksiloiva_tunniste
)
SELECT
    id,
    toimenpide,
    tehtava,
    tehtava_id,
    versio,
    syy,
    yksikkohinnan_lahde,
    yksikkohinnan_alkuvuosi,
    syotetty_tavoitehintamuutos,
    COALESCE(kirjatut_kulut_summa, 0) AS kirjatut_kulut_summa,
    COALESCE(toteutunut_maara, 0)     AS maara,
    suunniteltu_maara,
    kasin_lisattava_maara,
    yksikko,
    -- Määrämuutos = Toteutunut määrä - suunniteltu määrä
    COALESCE(toteutunut_maara, 0) - COALESCE(suunniteltu_maara, 0) AS maaramuutos,
    tr_tunniste,
    (tr_tunniste = '3d5962b4-c7ca-4750-81f1-f589b9c7c52b') AS talvisuola, -- = 'Liukkaudentorjunta suolaamalla (materiaali)' - onko kyseessä talvisuola? 
    talvisuola_kerroin,
    CASE
        -- ---------------------------------------------------------
        -- Yksikköhinta = Kirjatut kulut / toteutunut määrä   
        -- Ei autom. laskentaa        -> Tav hinnan muutos / Määrämuutos
        -- Valittu käyttöliittymästä  -> pass 
        WHEN syotetty_tavoitehintamuutos IS NOT NULL
            THEN ROUND(syotetty_tavoitehintamuutos / NULLIF(COALESCE(toteutunut_maara,0) - COALESCE(suunniteltu_maara,0), 0), 2)
        WHEN (lahde IS NULL OR lahde = 'laskettu')
            THEN ROUND(COALESCE(kirjatut_kulut_summa,0) / NULLIF(toteutunut_maara, 0), 2)
        WHEN lahde = 'valittu' THEN NULL
        END AS yksikkohinta,
    CASE
        -- ---------------------------------------------------------
        -- Tavoitehinnan muutos = Määrämuutos * yksikköhinta  
        -- Yksikköhinta puuttuu        -> muutos syötetään käsin, myös ennen 2025 alkaneet syöttävät käsin
        -- Ei toteumia, vanha urakka   -> pass 
        -- Yksikköhinta valittu        -> pass 
        -- Automatiikka käytössä       -> laske 
        WHEN (lahde = 'puuttuu' OR :laskenta-automatiikka? = FALSE)
            THEN syotetty_tavoitehintamuutos
        WHEN (:laskenta-automatiikka? = FALSE OR COALESCE(toteutunut_maara,0) = 0)
            THEN NULL
        WHEN (lahde IS NULL OR lahde = 'laskettu')
            THEN (COALESCE(toteutunut_maara,0) - COALESCE(suunniteltu_maara,0))
            * (COALESCE(kirjatut_kulut_summa,0) / NULLIF(toteutunut_maara,0))
        WHEN lahde = 'valittu' THEN NULL
        END AS tavoitehinnan_muutos
    FROM maaramuutokset
ORDER BY toimenpide, tehtava; 


-- name: paivita-tehtava-tiedot<!
INSERT INTO mhu_muutos_tehtava_tiedot (
  urakka,
  tehtava,
  hoitokauden_alkuvuosi,
  valitun_yksikkohinnan_hoitokausi,
  kasin_syotetty_tavoitehintamuutos,
  lahde,
  syy,
  luotu,
  luoja,
  muokattu,
  muokkaaja
) VALUES (
  :urakka,
  :tehtava,
  :hk_alkuvousi,
  :yksikkohinta_hk_alkuvuosi,
  :kasin_syotetty_tavoitehinta,
  :lahde::muutos_yksikkohinta_lahde_enum,
  :syy,
  NOW(),
  :kayttaja,
  NULL,
  NULL
)
ON CONFLICT (urakka, tehtava, hoitokauden_alkuvuosi)
DO UPDATE SET
  valitun_yksikkohinnan_hoitokausi   = EXCLUDED.valitun_yksikkohinnan_hoitokausi,
  kasin_syotetty_tavoitehintamuutos  = EXCLUDED.kasin_syotetty_tavoitehintamuutos,
  lahde                              = EXCLUDED.lahde::muutos_yksikkohinta_lahde_enum,
  syy                                = EXCLUDED.syy,
  muokattu                           = NOW(),
  muokkaaja                          = EXCLUDED.luoja;

-- name: hae-muutos
SELECT *
  FROM mhu_muutos m
 WHERE m.id = :id;

-- name: hae-urakan-muutostyot
SELECT  DISTINCT ON (m.id)
        m.id,
        m.tyyppi, 
        m.alityyppi,
        m.nimi, 
        m.voimassa_alkaen,
        mmk.summa AS budjetoitu_summa,
        COALESCE(kk.kirjattu_summa, 0) AS kirjattu_summa
 FROM mhu_muutos m
 JOIN mhu_muutos_kustannusvaikutus mmk ON mmk.muutos = m.id
 LEFT JOIN (
     SELECT muutos, SUM(summa) AS kirjattu_summa
     FROM kulu_kohdistus
     WHERE poistettu IS NOT TRUE
     GROUP BY muutos
 ) kk ON kk.muutos = m.id
WHERE m.tyyppi =  'muutostyo'::MHU_MUUTOSTYYPPI
  AND m.urakka =  :urakka
  AND (m.voimassa_alkaen BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND m.poistettu IS FALSE 
ORDER BY m.id DESC;

-- name: hae-laskutusrajan-muutosten-summa-hoitovuodelle
SELECT COALESCE(SUM(mk.summa), 0) AS muutokset_yhteensa
FROM mhu_muutos_kustannusvaikutus mk
         JOIN mhu_muutos m ON m.id = mk.muutos
WHERE m.urakka = :urakka-id
  AND mk.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND m.poistettu IS FALSE
  AND (m.tyyppi = 'muutostyo'::MHU_MUUTOSTYYPPI
       OR (m.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI AND mk.summa > 0))
  AND (:paivitettava-muutos-id::INTEGER IS NULL OR m.id != :paivitettava-muutos-id::INTEGER);
