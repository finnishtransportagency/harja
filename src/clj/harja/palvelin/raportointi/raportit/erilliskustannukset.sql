-- name: hae-erilliskustannukset
-- Hakee erilliskustannukset aikavälillä
SELECT
  e.id,
  e.tyyppi,
  e.urakka as urakka_id,
  e.sopimus as sopimus_id,
  e.toimenpideinstanssi,
  e.pvm,
  e.rahasumma,
  e.indeksin_nimi,
  e.lisatieto,
  e.luotu,
  e.luoja,
  kuukauden_indeksikorotus(e.pvm, e.indeksin_nimi, e.rahasumma, e.urakka) AS indeksikorjattuna,
  (SELECT korotus FROM laske_hoitokauden_asiakastyytyvaisyysbonus(e.urakka, e.pvm, e.indeksin_nimi, e.rahasumma)) AS bonusindeksikorotus,
  hy.nimi as hallintayksikko_nimi,
  hy.id as hallintayksikko_id,
  s.sampoid as sopimus_sampoid,
  tpi.nimi as tpinimi,
  u.nimi as urakka_nimi
FROM erilliskustannus e
  LEFT JOIN toimenpideinstanssi tpi ON tpi.id = e.toimenpideinstanssi
  LEFT JOIN sopimus s ON e.sopimus = s.id
  LEFT JOIN urakka u ON e.urakka = u.id
  LEFT JOIN organisaatio hy ON (u.hallintayksikko = hy.id AND hy.tyyppi = 'hallintayksikko')
WHERE (:urakka_annettu IS FALSE OR e.sopimus in
                                   (SELECT id FROM sopimus WHERE urakka = :urakka))
      AND (:urakka_annettu IS TRUE OR (:urakka_annettu IS FALSE AND (:urakkatyyppi::urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi::urakkatyyppi)))
      AND (:urakka_annettu IS TRUE OR u.urakkanro IS NOT NULL)
      AND (:hallintayksikko_annettu IS FALSE OR
           u.id IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko))
      AND (:toimenpide::INTEGER IS NULL OR (tpi.toimenpide = :toimenpide AND e.urakka = tpi.urakka))
      AND e.pvm :: DATE BETWEEN :alku AND :loppu
      AND e.poistettu IS NOT TRUE;
