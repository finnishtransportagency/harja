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
  FROM mhu_muutos m
           LEFT JOIN mhu_muutos_kustannusvaikutus kust ON (m.id = kust.muutos AND
                                                           m.versio = kust.versio AND
                                                           kust.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi)
           LEFT JOIN mhu_muutos_tehtava_ja_maaraluettelo tjm ON (m.id = tjm.muutos AND
                                                                 m.versio = tjm.versio AND
                                                                 tjm.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi)
           LEFT JOIN mhu_muutos_liite lii ON (m.id = lii.muutos AND m.versio = lii.versio)
 WHERE m.urakka = :urakka
 GROUP BY m.id, m.versio, m.urakka, m.voimassa_alkaen, m.tyyppi, m.nimi, m.syy, m.kulu_kohdistus, m.luonnos;

