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
   INSERT INTO mhu_muutos
   (urakka, tyyppi, nimi, syy, kulu_kohdistus, luonnos, voimassa_alkaen, luoja, luotu)
   VALUES
       (:urakka, :tyyppi::MHU_MUUTOSTYYPPI, :nimi, :syy, :kulu_kohdistus, :luonnos, :voimassa_alkaen, :kayttaja, NOW())
RETURNING id, versio;

-- name: paivita-muutos<!
UPDATE ONLY mhu_muutos
   SET versio = versio + 1,
       muokattu = NOW(),
       muokkaaja = :kayttaja,
       nimi = :nimi,
       tyyppi = :tyyppi::MHU_MUUTOSTYYPPI,
       syy = :syy,
       kulu_kohdistus = :kulu_kohdistus,
       luonnos = :luonnos,
       voimassa_alkaen = :voimassa_alkaen
 WHERE id = :id
RETURNING id, versio;

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
  )
SELECT
    m.id,

    tk.nimi AS toimenpide,
    tpi.id as toimenpideinstanssi,
    CASE
        WHEN COUNT(tjm.*) = 0 THEN NULL
        ELSE json_agg(DISTINCT jsonb_build_object(
            'tehtava', tjm.tehtava,
            'edellinen_maara', tjm.edellinen_maara,
            'maaramuutos', tjm.maaramuutos,
            'uusi_maara', tjm.uusi_maara)) END AS tehtavat_ja_maarat,
    CASE
        WHEN COUNT(kust.*) = 0 THEN NULL
        ELSE json_agg(DISTINCT jsonb_build_object(
            'kustannuslaji', kust.kustannuslaji,
            'toimenpideinstanssi', kust.toimenpideinstanssi,
            'summa', kust.summa)) END AS kustannusvaikutukset,
    1000 AS "suunniteltu-kustannus" -- FIXME: tähän haut toimenpidekohtaiset haut kustannussuunnitelman tietoihin
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
 GROUP BY m.id, tk.nimi, tpi.id, tk.koodi, tp.jarjestys
 ORDER BY tp.jarjestys;

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


-- name: hae-toimenpiteiden-tavoitehintaan-vaikuttavat-kustannukset
-- muokataan kustannusten seurannan kyselyä "listaa-kustannukset-paaryhmittain" siten,
-- että jätetään muutosten kannalta turhat tietolajit pois, ja pyydetään yksittäisten rivien sijaan
-- budjetoitujen hankintakustannusten summatiedot toimenpiteittäin
SELECT COALESCE(SUM(kt.summa), 0)                     AS budjetoitu_summa,
       COALESCE(SUM(kt.summa_indeksikorjattu), 0)     AS budjetoitu_summa_indeksikorjattu,
       0                                              AS toteutunut_summa,
       kt.tyyppi::TEXT                                AS maksutyyppi,
       'hankinta'                                     AS toimenpideryhma,
       COALESCE(tr.nimi, tk_tehtava.nimi)             AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           END                                        AS toimenpide,
       MIN(CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'budjetointi'                                  AS toteutunut,
       tk_tehtava.jarjestys                           AS jarjestys,
       'hankintakustannukset'                         AS paaryhma,
       kt.indeksikorjaus_vahvistettu                  AS indeksikorjaus_vahvistettu
  FROM toimenpide tk,
       kustannusarvioitu_tyo kt
           LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = kt.tehtava
           LEFT JOIN tehtavaryhma tr ON tk_tehtava.tehtavaryhma = tr.id
           LEFT JOIN rahavaraus_urakka ru
                     ON kt.rahavaraus_id = ru.rahavaraus_id
                         AND ru.urakka_id = :urakka
           LEFT JOIN rahavaraus r ON kt.rahavaraus_id = r.id,
       toimenpideinstanssi tpi,
       sopimus s
 WHERE s.urakka = :urakka
   AND kt.sopimus = s.id
   AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   -- Jätetään rahavaraukset pois
   AND kt.rahavaraus_id IS NULL
   AND kt.toimenpideinstanssi = tpi.id
   AND tpi.toimenpide = tk.id
   AND (tk.koodi = '23104' -- talvihoito
     OR tk.koodi = '23116' -- liikenneympariston-hoito
     OR tk.koodi = '23124' -- sorateiden-hoito
     OR tk.koodi = '20107' -- paallystepaikkaukset
     OR tk.koodi = '20191' -- mhu-yllapito
     OR tk.koodi = '14301' -- mhu-korvausinvestointi
     )
 GROUP BY paaryhma, toimenpide, toimenpideryhma, maksutyyppi, tehtava_nimi, tk.koodi,
          tk_tehtava.jarjestys, tr.nimi, kt.indeksikorjaus_vahvistettu



 UNION ALL
-- Haetaan budjetoidut hankintakustannukset myös kiintehintainen_tyo taulusta
-- kiinteahintainen_tyo taulusta haetaan (suurin?) osa suunnitelluista kustannuksista.
-- Hinta on kiinteä, kun se on sopimuksessa sovittu, yleensä kuukausille jaettava könttäsumma.
SELECT kt.summa                                  AS budjetoitu_summa,
       kt.summa_indeksikorjattu                  AS budjetoitu_summa_indeksikorjattu,
       0                                         AS toteutunut_summa,
       'kiinteahintainen'                        AS maksutyyppi,
       'hankinta'                                AS toimenpideryhma,
       tk_tehtava.nimi                           AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           END                                   AS toimenpide,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       tk_tehtava.jarjestys                      AS jarjestys,
       'hankintakustannukset'                    AS paaryhma,
       kt.indeksikorjaus_vahvistettu             AS indeksikorjaus_vahvistettu
  from toimenpide tk,
       kiinteahintainen_tyo kt
           LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = kt.tehtava,
       toimenpideinstanssi tpi
 WHERE tpi.urakka = :urakka
   AND kt.toimenpideinstanssi = tpi.id
   AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND tpi.toimenpide = tk.id
   AND (tk.koodi = '23104' -- talvihoito
     OR tk.koodi = '23116' -- liikenneympariston-hoito
     OR tk.koodi = '23124' -- sorateiden-hoito
     OR tk.koodi = '20107' -- paallystepaikkaukset
     OR tk.koodi = '20191' -- mhu-yllapito
     OR tk.koodi = '14301' -- mhu-korvausinvestointi
     )
 UNION ALL

-- Toteutuneet kustannukset haetaan kulu_kohdistus taulusta. Nämäkin on ryhmitelty vastaavasti kuten
-- budjetoidut kustannukset eli Hankintakustannukset, Johto- ja hallintokorvaus, Hoidonjohdonpalkkio sekä Erillishankinnat
-- Jos tehtävä on merkattu rahavaraukseksi, niin laitetaan se rahavarausten alle
SELECT 0                          AS budjetoitu_summa,
       0                          AS budjetoitu_summa_indeksikorjattu,
       coalesce(SUM(lk.summa), 0) AS toteutunut_summa,
       lk.maksueratyyppi::TEXT    AS maksutyyppi,
       CASE
           WHEN lk.tyyppi::TEXT = 'hankintakulu' THEN 'hankinta'
           WHEN lk.tyyppi::TEXT = 'muukulu' THEN 'muukulu'
           WHEN lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           WHEN lk.tyyppi::TEXT = 'rahavaraus' THEN 'rahavaraus'
           ELSE 'hankinta'
           END                    AS toimenpideryhma,
       COALESCE(tr.nimi, tk.nimi) AS tehtava_nimi,
       CASE
           WHEN (tk.koodi = '23104' AND lk.rahavaraus_id IS NULL) THEN 'Talvihoito'
           WHEN (tk.koodi = '23116' AND lk.rahavaraus_id IS NULL) THEN 'Liikenneympäristön hoito'
           WHEN (tk.koodi = '23124' AND lk.rahavaraus_id IS NULL) THEN 'Sorateiden hoito'
           WHEN (tk.koodi = '20107' AND lk.rahavaraus_id IS NULL) THEN 'Päällystepaikkaukset'
           WHEN (tk.koodi = '20191' AND lk.rahavaraus_id IS NULL) THEN 'MHU Ylläpito'
           WHEN (tk.koodi = '14301' AND lk.rahavaraus_id IS NULL) THEN 'MHU Korvausinvestointi'
           WHEN lk.rahavaraus_id IS NOT NULL THEN COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi)
           END                    AS toimenpide,
       MIN(l.erapaiva)::TEXT      AS ajankohta,
       'toteutunut'               AS toteutunut,
       tr.jarjestys               AS jarjestys,
       CASE
           WHEN lk.tyyppi::TEXT = 'rahavaraus' THEN 'rahavaraukset'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS TRUE) THEN 'muukulu-tavoitehintainen'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS FALSE) THEN 'muukulu-eitavoitehintainen'
           WHEN lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           ELSE 'hankintakustannukset'
           END                    AS paaryhma,
       NOW()                      AS indeksikorjaus_vahvistettu -- kuluja ei indeksivahvisteta, joten ne on aina "true"
  FROM kulu_kohdistus lk
           LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma
           LEFT JOIN rahavaraus_urakka ru
                     ON lk.rahavaraus_id = ru.rahavaraus_id
                         AND ru.urakka_id = :urakka
           LEFT JOIN rahavaraus r ON lk.rahavaraus_id = r.id,
       toimenpideinstanssi tpi,
       toimenpide tk,
       kulu l
 WHERE l.urakka = :urakka
   AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
   AND l.poistettu IS NOT TRUE
   AND lk.kulu = l.id
   AND lk.toimenpideinstanssi = tpi.id
   AND lk.poistettu IS NOT TRUE
   AND tpi.toimenpide = tk.id
   -- Näillä toimenpidekoodi.koodi rajauksilla rajataan johto- ja hallintokorvaus, hoidonjohdonpalkkio ja erilliskorvaus ulos
   AND (tk.koodi = '23104' OR tk.koodi = '23116'
     OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
        tk.koodi = '14301')
 GROUP BY tr.nimi, tk.nimi, lk.tyyppi, lk.maksueratyyppi, tk.koodi, tr.jarjestys, tr.yksiloiva_tunniste,
          lk.rahavaraus_id, COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi), lk.tavoitehintainen
 UNION ALL
-- Toteutuneet erillishankinnat, hoidonjohdonpalkkio, johto- ja hallintokorvaukset
-- ja vuoden päättämiseen liittyvät kulut kulu_kohdistus taulusta.
-- Rajaus tehty toimenpidekoodi.koodi = 23151 perusteella
SELECT 0                          AS budjetoitu_summa,
       0                          AS budjetoitu_summa_indeksikorjattu,
       coalesce(SUM(lk.summa), 0) AS toteutunut_summa,
       lk.maksueratyyppi::TEXT    AS maksutyyppi,
       CASE
           WHEN tr.nimi = 'W - Erillishankinnat' THEN 'erillishankinnat'
           WHEN tr.nimi = 'J - Johto- ja hallintokorvaus' THEN 'hankinta'
           WHEN tr.nimi = 'G - Hoidonjohtopalkkio' THEN 'hoidonjohdonpalkkio'
           WHEN lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           WHEN lk.tyyppi::TEXT = 'muukulu' THEN 'muukulu'
           WHEN tr.yksiloiva_tunniste IN ('55c920e7-5656-4bb0-8437-1999add714a3',
                                          '19907c24-dd26-460f-9cb4-2ed974b891aa',
                                          'be34116b-2264-43e0-8ac8-3762b27a9557')
               THEN 'Hoitokauden päättäminen'
           END                   AS toimenpideryhma,
       CASE
           WHEN lk.tehtavaryhma IS NULL AND lk.tyyppi::TEXT = 'lisatyo' THEN tk.nimi
           WHEN lk.tehtavaryhma IS NULL AND lk.tyyppi::TEXT = 'muukulu' THEN tk.nimi
           WHEN tr.nimi = 'J - Johto- ja hallintokorvaus' THEN 'Johto- ja hallintokorvaus (käsin kirjattu)'
           WHEN tr.nimi = 'G - Hoidonjohtopalkkio'  AND lk.tyyppi::TEXT = 'paatos' THEN 'Hoidonjohtopalkkion muutos'
           ELSE tr.nimi
           END                   AS tehtava_nimi,
       CASE
           WHEN tr.nimi = 'W - Erillishankinnat' THEN 'Erillishankinnat'
           WHEN tr.yksiloiva_tunniste = '55c920e7-5656-4bb0-8437-1999add714a3' THEN 'Tavoitepalkkio'
           WHEN tr.yksiloiva_tunniste = '19907c24-dd26-460f-9cb4-2ed974b891aa' THEN 'Urakoitsija maksaa tavoitehinnan ylityksestä'
           WHEN tr.yksiloiva_tunniste = 'be34116b-2264-43e0-8ac8-3762b27a9557' THEN 'Urakoitsija maksaa kattohinnan ylityksestä'
           else 'Johto- ja Hallintokorvaus'
           END                   AS toimenpide,
       MIN(l.erapaiva)::TEXT     AS ajankohta,
       'toteutunut'              AS toteutunut,
       MIN(tr.jarjestys) AS jarjestys,
       CASE
           WHEN tr.nimi = 'W - Erillishankinnat' THEN 'erillishankinnat'
           WHEN tr.nimi = 'J - Johto- ja hallintokorvaus' THEN 'johto-ja-hallintokorvaus'
           WHEN tr.nimi = 'G - Hoidonjohtopalkkio' AND lk.tavoitehintainen IS TRUE THEN 'hoidonjohdonpalkkio'
           WHEN lk.tehtavaryhma IS NULL AND lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS TRUE) THEN 'muukulu-tavoitehintainen'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS FALSE) THEN 'muukulu-eitavoitehintainen'
           WHEN (lk.tyyppi::TEXT = 'paatos' AND lk.tavoitehintainen IS FALSE) THEN 'muukulu-eitavoitehintainen'
           WHEN tr.yksiloiva_tunniste = '55c920e7-5656-4bb0-8437-1999add714a3' THEN 'tavoitepalkkio'
           WHEN tr.yksiloiva_tunniste = '19907c24-dd26-460f-9cb4-2ed974b891aa' THEN 'tavoitehinnan-ylitys'
           WHEN tr.yksiloiva_tunniste = 'be34116b-2264-43e0-8ac8-3762b27a9557' THEN 'kattohinnan-ylitys'
           END                   AS paaryhma,
       NOW()                     AS indeksikorjaus_vahvistettu -- kuluja ei indeksivahvisteta, joten ne on aina "true"
  FROM kulu_kohdistus lk
           LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
       toimenpideinstanssi tpi,
       toimenpide tk,
       kulu l
 WHERE l.urakka = :urakka
   AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
   AND l.poistettu IS NOT TRUE
   AND lk.kulu = l.id
   AND lk.poistettu IS NOT TRUE
   AND lk.toimenpideinstanssi = tpi.id
   AND tpi.toimenpide = tk.id
   -- Näillä toimenpidekoodi.koodi rajauksilla rajataan Hankintakustannukset ulos
   AND tk.koodi = '23151'
 GROUP BY tehtava_nimi, tr.nimi, tr.yksiloiva_tunniste, lk.tyyppi, lk.maksueratyyppi, toimenpideryhma, toimenpide, paaryhma
 UNION ALL
-- Osa toteutuneista erillishankinnoista, hoidonjohdonpalkkioista ja johdon- hallintakorvauksesta
-- siirretään kustannusarvoitu_tyo taulusta toteutuneet_kustannukset tauluun aina kuukauden viimeisenä päivänä.
-- Rajaus tehty toimenpidekoodi.koodi = 23151 perusteella
SELECT 0                                            AS budjetoitu_summa,
       0                                            AS budjetoitu_summa_indeksikorjattu,
       coalesce(SUM(t.summa_indeksikorjattu), 0)    AS toteutunut_summa,
       'kokonaishintainen'                          AS maksutyyppi,
       CASE
           WHEN tr.nimi = 'W - Erillishankinnat' THEN 'erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'hankinta'
           WHEN tr.nimi = 'J - Johto- ja hallintokorvaus' THEN 'hankinta'
           WHEN tk_tehtava.yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8' then 'hoidonjohdonpalkkio'
--           WHEN tr.nimi = 'G - Hoidonjohtopalkkio' THEN 'hoidonjohdonpalkkio'
           END                                      AS toimenpideryhma,
       coalesce(tr.nimi, tk_tehtava.nimi)           AS tehtava_nimi,
       CASE
           WHEN tr.nimi = 'W - Erillishankinnat' THEN 'Erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'Johto- ja Hallintokorvaus'
           END                                      AS toimenpide,
       MIN(concat(t.vuosi, '-', t.kuukausi, '-01')) AS ajankohta,
       'toteutunut'                                      AS toteutunut,
       MIN(tk_tehtava.jarjestys)                    AS jarjestys,
       CASE
           WHEN tr.nimi = 'W - Erillishankinnat' THEN 'erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'johto-ja-hallintokorvaus'
           WHEN tr.nimi = 'J - Johto- ja hallintokorvaus' THEN 'johto-ja-hallintokorvaus'
           WHEN tr.nimi = 'G - Hoidonjohtopalkkio' THEN 'hoidonjohdonpalkkio'
           WHEN tk_tehtava.yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8' then 'hoidonjohdonpalkkio' --'c9712637-fbec-4fbd-ac13-620b5619c744' THEN 'hoidonjohdonpalkkio'
           END                                      AS paaryhma,
       t.indeksikorjaus_vahvistettu                 AS indeksikorjaus_vahvistettu
  FROM toteutuneet_kustannukset t
           LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = t.tehtava
           LEFT JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma,
       toimenpideinstanssi tpi,
       toimenpide tk
 WHERE t.urakka_id = :urakka
   AND (concat(t.vuosi, '-', t.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND t.toimenpideinstanssi = tpi.id
   AND tpi.toimenpide = tk.id
   -- Rajataan vain hoidon johto toimenpiteeseen
   AND tk.koodi = '23151'
 GROUP BY tehtava_nimi, toimenpideryhma, paaryhma, tr.nimi, tk_tehtava.yksiloiva_tunniste, indeksikorjaus_vahvistettu;


-- mhu ylläpito 9960.5 * 12 = 119526
-- päällystepaikk 153904,92
--soaritet 217236
--mhu korv 51983,64
--liik ymp 250376,28
--talvihoito 1 281 972,84
