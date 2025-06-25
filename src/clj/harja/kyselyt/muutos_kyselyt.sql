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
                                   AND erapaiva BETWEEN (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')) AND
                                            (SELECT TO_DATE(:hoitokauden_alkuvuosi + 1 || '-09-30', 'YYYY-MM-DD'))) AS "jjh-muutosten-summa",

       CASE
           WHEN COUNT(kust.*) = 0 THEN NULL
           ELSE json_agg(DISTINCT jsonb_build_object(
               'kustannuslaji', kust.kustannuslaji,
               'toimenpide', kust.toimenpide,
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
               'liite', lii.liite)) END  AS liitteet
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
    -- name: luo-muutos<!
    INSERT INTO mhu_muutos
    (urakka, tyyppi, nimi, syy, kulu_kohdistus, luonnos, voimassa_alkaen, luoja, luotu)
    VALUES
    (:urakka, :tyyppi::MHU_MUUTOSTYYPPI, :nimi, :syy, :kulu_kohdistus, :luonnos, :voimassa_alkaen, :kayttaja, NOW())
    RETURNING id, versio;

-- name: paivita-muutos<!
UPDATE mhu_muutos
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
INSERT
  INTO kulu_kohdistus (kulu, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, luotu, luoja,
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
