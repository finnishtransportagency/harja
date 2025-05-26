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
 WHERE m.urakka = :urakka AND
       -- hox: on myös sellaisia muutoksia, jotka ovat voimassa vain meneillään olevan hoitokauden
       -- niiden käsittely puuttuu vielä tästä kyselystä
       m.voimassa_alkaen <= (SELECT TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD'))
 GROUP BY m.id, m.versio, m.urakka, m.voimassa_alkaen, m.tyyppi, m.nimi, m.syy, m.kulu_kohdistus, m.luonnos;

