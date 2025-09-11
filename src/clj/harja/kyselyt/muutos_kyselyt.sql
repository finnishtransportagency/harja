-- name: hae-urakan-hoitovuoden-muutostiedot
SELECT m.id,
       m.versio,
       m.urakka,
       m.voimassa_alkaen,
       m.tyyppi,
       m.nimi,
       m.syy,
       m.kulu_kohdistus,
       m.luonnos,
       -- johto- ja hallintakorvausmuutosten kokonaissumma
       (SELECT sum(kokonaissumma) FROM kulu k
                                  JOIN mhu_muutos_kulu mmk ON (k.id = mmk.kulu AND m.id = mmk.muutos AND m.versio = mmk.versio)
                                  JOIN kulu_kohdistus kk ON k.id = kk.kulu AND kk.tyyppi = 'jjh-muutos'
                                  WHERE k.poistettu IS FALSE AND kk.poistettu IS FALSE
                                    AND k.erapaiva BETWEEN (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')) AND
                                      (SELECT TO_DATE(:hoitokauden_alkuvuosi + 1 || '-09-30', 'YYYY-MM-DD'))) AS "jjh-muutosten-summa",

       CASE
           WHEN COUNT(kust.*) = 0 THEN NULL
           ELSE json_agg(DISTINCT jsonb_build_object(
               'kustannuslaji', kust.kustannuslaji,
               'toimenpideinstanssi', kust.toimenpideinstanssi,
               'summa', kust.summa)) END AS kustannusvaikutukset,

       CASE
           WHEN COUNT(tjm.*) = 0 THEN NULL
                ELSE json_agg(DISTINCT jsonb_build_object(
                    'tehtava', tjm.tehtava,
                    'edellinen_maara', tjm.edellinen_maara,
                    'maaramuutos', tjm.maaramuutos,
                    'uusi_maara', tjm.uusi_maara)) END AS tehtavat_ja_maarat,

       CASE
           WHEN COUNT(lii.*) = 0 THEN NULL
           ELSE json_agg(DISTINCT jsonb_build_object(
               'muutos', lii.muutos,
               'id', lii.liite)) END  AS liitteet
-- ONLY tarvitaan, jottei kysellä historiatauluista
  FROM ONLY mhu_muutos m
           LEFT JOIN ONLY mhu_muutos_kustannusvaikutus kust ON (m.id = kust.muutos AND
                                                           m.versio = kust.versio AND
                                                           kust.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi)
           LEFT JOIN ONLY mhu_muutos_tehtava_ja_maaraluettelo tjm ON (m.id = tjm.muutos AND
                                                                 m.versio = tjm.versio AND
                                                                 tjm.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi)
           LEFT JOIN ONLY mhu_muutos_liite lii ON (m.id = lii.muutos AND m.versio = lii.versio)
 WHERE m.urakka = :urakka
       -- hox: on myös sellaisia muutoksia, jotka ovat voimassa vain meneillään olevan hoitokauden
       -- niiden käsittely puuttuu vielä tästä kyselystä
   AND  m.voimassa_alkaen <= (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD'))
 GROUP BY m.id, m.versio, m.urakka, m.voimassa_alkaen, m.tyyppi, m.nimi, m.syy, m.kulu_kohdistus, m.luonnos;

-- name: rahavarausten-toteumat
SELECT rv.id, SUM(kk.summa) as toteumat
  FROM kulu k
           JOIN kulu_kohdistus kk ON k.id = kk.kulu
           JOIN toimenpideinstanssi tpi ON kk.toimenpideinstanssi = tpi.id
           JOIN rahavaraus rv ON kk.rahavaraus_id = rv.id
           JOIN rahavaraus_urakka rvu ON rv.id = rvu.rahavaraus_id
           JOIN urakka u ON rvu.urakka_id = u.id AND tpi.urakka = u.id
 WHERE u.id = :urakka
   AND k.erapaiva BETWEEN (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')) AND
     (SELECT TO_DATE(:hoitokauden_alkuvuosi + 1 || '-09-30', 'YYYY-MM-DD'))
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


-- name: luo-tai-paivita-muutos-kustannusvaikutus<!
INSERT INTO mhu_muutos_kustannusvaikutus (
    versio,
    muutos,
    kustannuslaji,
    toimenpideinstanssi,
    hoitokauden_alkuvuosi,
    summa
  ) VALUES (
    :versio,
    :id,
    :kustannuslaji,
    :tpi,
    :hoitokauden_alkuvuosi,
    :summa
) ON CONFLICT (muutos, hoitokauden_alkuvuosi)
DO UPDATE SET
  kustannuslaji        = EXCLUDED.kustannuslaji,
  toimenpideinstanssi  = EXCLUDED.toimenpideinstanssi,
  summa                = EXCLUDED.summa;


-- name: luo-muutos-kulu-linkitys<!
INSERT INTO mhu_muutos_kulu (versio, muutos, kulu)
VALUES (:versio, :muutos, :kulu);

-- name: luo-jjh-kulun-kohdistus<!
INSERT INTO kulu_kohdistus (kulu, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, luotu, luoja,
                            tavoitehintainen)
VALUES (:kulu, 0, :summa,
        :toimenpideinstanssi,
        (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54'),
        'kokonaishintainen'::MAKSUERATYYPPI,
        'jjh-muutos'::KOHDISTUSTYYPPI, current_timestamp, :kayttaja,
        TRUE::BOOLEAN);

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

-- name: poista-muutos!
UPDATE mhu_muutos
   SET poistettu = TRUE,
       muokkaaja = :kayttaja,
       muokattu = NOW()
 WHERE id = :id
   AND versio = :versio;

-- name: linkita-muutos-ja-liite<!
INSERT INTO mhu_muutos_liite (muutos, liite, versio)
VALUES (:muutos, :liite, :versio);


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
    CASE
        WHEN COUNT(tjm.*) = 0 THEN '[]'::JSON
        ELSE json_agg(DISTINCT jsonb_build_object(
            'tehtava', tjm.tehtava,
            'edellinen_maara', tjm.edellinen_maara,
            'maaramuutos', tjm.maaramuutos,
            'uusi_maara', tjm.uusi_maara,
            'hoitokauden_alkuvuosi', tjm.hoitokauden_alkuvuosi))
    END AS tehtavat_ja_maarat,
    CASE
        WHEN COUNT(kust.*) = 0 THEN '[]'::JSON
        ELSE json_agg(DISTINCT jsonb_build_object(
            'kustannuslaji', kust.kustannuslaji,
            'toimenpideinstanssi', kust.toimenpideinstanssi,
            'summa', kust.summa,
            'hoitokauden_alkuvuosi', kust.hoitokauden_alkuvuosi))
    END AS kustannusvaikutukset,
    CASE
        WHEN COUNT(st.*) = 0 THEN NULL
        ELSE json_agg(DISTINCT jsonb_build_object(
            'budjetoitu_summa', st.budjetoitu_summa,
            'hoitokauden_alkuvuosi', st.hoitokauden_alkuvuosi))
    END AS budjetoidut_summat
FROM toimenpiteet tk
    LEFT JOIN toimenpide tp ON tp.koodi = tk.koodi
    LEFT JOIN toimenpideinstanssi tpi ON tp.id = tpi.toimenpide AND tpi.urakka = :urakka
    LEFT JOIN ONLY mhu_muutos m ON (m.id = :id AND m.versio = :versio AND m.poistettu IS FALSE)
    LEFT JOIN ONLY mhu_muutos_kustannusvaikutus kust ON (m.id = kust.muutos AND
                                                         m.versio = kust.versio AND
                                                         kust.toimenpideinstanssi = tpi.id)
    LEFT JOIN ONLY mhu_muutos_tehtava_ja_maaraluettelo tjm ON (m.id = tjm.muutos AND
                                                               m.versio = tjm.versio AND
                                                               tjm.tehtava IN (SELECT id FROM tehtava WHERE emo = tp.id))
    LEFT JOIN summatut_tyot st ON st.toimenpide = tk.nimi
GROUP BY m.id, tk.nimi, tk.koodi, tpi.id, tk.koodi, tp.jarjestys
ORDER BY tp.jarjestys;


-- name: hae-tehtava-maaramuutokset
WITH urakan_tehtavat AS (
  SELECT
    tt.toimenpidekoodi           AS toimenpidekoodi,
    SUM(tt.maara)                AS maara,
    :urakka                      AS urakka,
    MAX(t.id)                    AS toteuma_id,
    MAX(tt.id)                   AS toteuma_tehtava_id
  FROM toteuma t
    JOIN toteuma_tehtava tt ON t.id = tt.toteuma
                            AND tt.urakka_id = :urakka
                            AND tt.poistettu = FALSE
    LEFT JOIN toteuma_materiaali tm ON t.id = tm.toteuma
                                   AND tm.urakka_id = :urakka
                                   AND tm.poistettu = FALSE
  WHERE t.urakka = :urakka
    AND (t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
    AND t.poistettu = FALSE
  GROUP BY tt.toimenpidekoodi
) 
SELECT
  tk.id                                            AS id,
  o.otsikko                                        AS toimenpide,
  tk.nimi                                          AS tehtava,
  tk.id                                            AS tehtava_id,
  mmt.versio                                       AS versio,
  mmt.syy                                          AS syy,
  mmt.lahde                                        AS yksikkohinnan_lahde,
  mmt.valitun_yksikkohinnan_hoitokausi             AS yksikkohinnan_alkuvuosi,
  mmt.kasin_syotetty_tavoitehintamuutos            AS syotetty_tavoitehintamuutos,
  COALESCE(kulut.summa, 0)                         AS kirjatut_kulut_summa,
  COALESCE(SUM(urakan_tehtavat.maara), 0)          AS maara,
  SUM(ut.maara)                                    AS suunniteltu_maara,
  tk.kasin_lisattava_maara                         AS kasin_lisattava_maara,
  tk.suunnitteluyksikko                            AS yksikko,
  -- ---------------------------------------------------- --
  -- Määrämuutos  =  Toteutunut määrä - suunniteltu määrä 
  -- ---------------------------------------------------- --
  COALESCE(SUM(urakan_tehtavat.maara), 0) -
  COALESCE(SUM(ut.maara), 0)                       AS maaramuutos,
  -- ---------------------------------------------------- --
  -- Yksikköhinta =  Kirjatut kulut / toteutunut määrä   
  -- ---------------------------------------------------- --
  CASE
    -- 
	  WHEN mmt.lahde IS NULL OR mmt.lahde = 'laskettu'
	    THEN ROUND(kulut.summa / NULLIF(SUM(urakan_tehtavat.maara), 0), 2)
    -- 
    -- Yksikköhinta valittu käyttöliittymästä 
	  WHEN mmt.lahde = 'valittu' THEN NULL
    -- 
	  ELSE NULL
	END                                             AS yksikkohinta,
  -- ---------------------------------------------------- --
  -- Tavoitehinnan muutos = Määrämuutos * yksikköhinta  
  -- ---------------------------------------------------- --
  CASE 
    -- ============================================================
    -- Yksikköhinta puuttuu -> muutos syötetään käsin 
    -- ============================================================
    WHEN mmt.lahde = 'puuttuu' 
      THEN mmt.kasin_syotetty_tavoitehintamuutos
    -- ============================================================
    -- Seuraaviin tarvitaan toteumia, ei jatketa muuten
    -- ============================================================
    WHEN SUM(urakan_tehtavat.maara) = 0 THEN NULL
    -- ============================================================
    -- Tavoitehinta lasketaan itsestään, joten lasketaan se tässä 
    -- ============================================================
  	WHEN mmt.lahde IS NULL OR mmt.lahde = 'laskettu' 
      -- Toteutunut  - suunniteltu 
      THEN (COALESCE(SUM(urakan_tehtavat.maara), 0) -
		        COALESCE(SUM(ut.maara), 0)
           )  -- Kertaa yksikköhinta 
           * (COALESCE(kulut.summa, 0) / SUM(urakan_tehtavat.maara))  
    -- ============================================================
    -- Yksikköhinta valittu, lasketaan endpointissa erikseen => palauta null
    -- ============================================================
    WHEN mmt.lahde = 'valittu' THEN NULL
  END                                             AS tavoitehinnan_muutos
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
  JOIN urakka u
    ON u.id = :urakka
  -- --------------------------------------------------------------------
  -- Vedetään täältä syy sekä yksikköhinnan hk / kirjattu tavoitehinta
  -- --------------------------------------------------------------------
  LEFT JOIN LATERAL (
    SELECT mmt.*
     FROM mhu_muutos_tehtava_tiedot mmt
    WHERE mmt.urakka = :urakka
      AND mmt.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
      AND mmt.tehtava = tk.id
    ORDER BY mmt.versio DESC
    LIMIT 1
  ) mmt ON TRUE
  -- Hae tehtävän kulut
  LEFT JOIN (
    SELECT
      -- Kiinnostaa tässä  vaiheessa vaan summa, ja yhdistetään tehtava_id 
      kk.tehtava       AS tehtava_id,
      SUM(kk.summa)    AS summa
    FROM kulu k
      JOIN kulu_kohdistus kk
        ON k.id = kk.kulu
        AND kk.poistettu IS NOT TRUE
    WHERE k.urakka = :urakka
      AND (:alkupvm::DATE IS NULL OR :alkupvm::DATE <= k.erapaiva)
      AND (:loppupvm::DATE IS NULL OR k.erapaiva <= :loppupvm::DATE)
      AND k.poistettu IS NOT TRUE
    GROUP BY kk.tehtava
  ) kulut ON kulut.tehtava_id = tk.id
WHERE
  -- Tärkeä:: halutaan nimenomaan vain määrämitattavat urakan tehtävät 
  tk."maaramitattava?" IS TRUE
  -- Rajataan pois hoitoluokka- eli aluetiedot paitsi, jos niihin saa kirjata toteumia käsin
  AND (tk.aluetieto = FALSE OR (tk.aluetieto = TRUE AND tk.kasin_lisattava_maara = TRUE))
  -- Rajataan pois ne, jotka eivät ole mhu tehtäviä
  AND tk."mhu-tehtava?" = TRUE
  AND (tk.voimassaolo_alkuvuosi IS NULL OR tk.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tk.voimassaolo_loppuvuosi IS NULL OR tk.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  -- Rajataan pois tehtävät joilla ei ole suunnitteluyksikköä ja tehtävät joiden yksikkö on euro
  -- mutta otetaan mukaan Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen ja lisätyöt
  AND (
    (tk.suunnitteluyksikko IS NOT NULL AND tk.suunnitteluyksikko != 'euroa') OR
    tk.yksiloiva_tunniste IN (
      '49b7388b-419c-47fa-9b1b-3797f1fab21d',
      '63a2585b-5597-43ea-945c-1b25b16a06e2',
      'b3a7a210-4ba6-4555-905c-fef7308dc5ec',
      'e32341fc-775a-490a-8eab-c98b8849f968',
      '0c466f20-620d-407d-87b0-3cbb41e8342e',
      'c058933e-58d3-414d-99d1-352929aa8cf9'
    )
  )
  -- Näkymään halutaan vain tehtävät jotka suunniteltu 
  AND ut.maara IS NOT NULL 
  AND ut.maara > 0
GROUP BY
  tk.id,
  tk.nimi,
  o.otsikko,
  tk.kasin_lisattava_maara,
  tk.suunnitteluyksikko,
  kulut.summa,
  mmt.versio,
  mmt.syy,
  mmt.lahde,
  mmt.valitun_yksikkohinnan_hoitokausi,
  mmt.kasin_syotetty_tavoitehintamuutos
ORDER BY
  o.otsikko ASC,
  tk.nimi ASC;


-- name: paivita-tehtava-maaramuutos<!
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
