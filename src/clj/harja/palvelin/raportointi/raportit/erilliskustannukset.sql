-- name: hae-erilliskustannukset
-- Hakee erilliskustannukset aikavälillä
SELECT
  e.id,
  e.tyyppi,
  e.sopimus,
  e.toimenpideinstanssi,
  e.pvm,
  e.rahasumma,
  e.indeksin_nimi,
  e.lisatieto,
  e.luotu,
  e.luoja,
  kuukauden_indeksikorotus(e.pvm, e.indeksin_nimi, e.rahasumma) AS indeksikorjattuna,
SELECT korotus
FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, ind,
                                    kht.summa, perusluku)) AS ind
  hy.nimi as hallintayksikko_nimi,
  hy.id as hallintayksikko_id,
  u.nimi as urakka_nimi,
  u.id as urakka_id
FROM erilliskustannus e
  LEFT JOIN urakka u ON e.sopimus IN (SELECT id FROM sopimus WHERE urakka = u.id)
  LEFT JOIN organisaatio hy ON (u.hallintayksikko = hy.id AND hy.tyyppi = 'hallintayksikko')
WHERE (:urakka_annettu IS FALSE OR e.sopimus in
                                   (SELECT id FROM sopimus WHERE urakka = :urakka))
      AND (:hallintayksikko_annettu IS FALSE OR
           u.id IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko))
      AND e.pvm :: DATE BETWEEN :alku AND :loppu;