-- name: hae-maaramitattavat-tehtavat
WITH rahavaraustehtava AS
    (
    SELECT rt.id, rt.tehtava_id
    FROM rahavaraus_urakka rvu
             JOIN rahavaraus_tehtava rt ON rvu.rahavaraus_id = rt.rahavaraus_id
    WHERE rvu.urakka_id = :urakkaid ),
 muutokset AS (
    SELECT mm.id, mmtm.edellinen_maara, mmtm.maaramuutos, mmtm.uusi_maara, mmtm.tehtava as tehtavaid,
           mm.voimassa_alkaen, mm.syy
      FROM mhu_muutos mm
           LEFT JOIN mhu_muutos_tehtava_ja_maaraluettelo mmtm ON mmtm.muutos = mm.id
    WHERE mm.urakka = :urakkaid
      AND extract(YEAR from mm.voimassa_alkaen) <= :hoitokauden-alkuvuosi
      AND mm.poistettu IS NOT TRUE
    )
SELECT t.id as tehtava_id, t.nimi, t.tehtavaryhma as tehtavaryhmaid, t.yksikko, t.suunnitteluyksikko, t.jarjestys,
       tr.nimi as tehtavaryhmanimi, tro.otsikko as tehtavaryhmaotsikko, tp.nimi as toimenpidenimi, ut.maara as tarjous_maara,
       (SELECT array_agg(row(id, ut.maara, maaramuutos, (ut.maara+maaramuutos), tehtavaid, voimassa_alkaen, syy))
        FROM muutokset WHERE muutokset.tehtavaid = t.id) AS muutokset,
       (SELECT SUM(maaramuutos) FROM muutokset WHERE muutokset.tehtavaid = t.id) AS muutos_maaramuutos,
       CASE
           WHEN ut.maara IS NOT NULL OR (SELECT SUM(maaramuutos) FROM muutokset WHERE muutokset.tehtavaid = t.id) IS NOT NULL
               THEN (COALESCE(ut.maara,0) + COALESCE((SELECT SUM(maaramuutos) FROM muutokset WHERE muutokset.tehtavaid = t.id), 0))
           ELSE null
           END AS yhteensa
FROM tehtavaryhma tr
      JOIN tehtavaryhmaotsikko tro ON tr.tehtavaryhmaotsikko_id = tro.id
      JOIN tehtava t ON tr.id = t.tehtavaryhma
            AND t."mhu-tehtava?" IS TRUE
            AND t.poistettu IS NOT TRUE
            AND t.piilota IS NOT TRUE
      JOIN toimenpide tp ON t.emo = tp.id
      LEFT JOIN urakka_tehtavamaara ut ON ut.tehtava = t.id AND ut.urakka = :urakkaid AND ut."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi
      JOIN urakka u ON u.id = :urakkaid
WHERE  (tr.voimassaolo_alkuvuosi IS NULL OR tr.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tr.voimassaolo_loppuvuosi IS NULL OR tr.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  AND (t.voimassaolo_alkuvuosi IS NULL OR t.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (t.voimassaolo_loppuvuosi IS NULL OR t.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  -- Suunnitteluyksikkö ei voi null tai euroa
  AND t.suunnitteluyksikko IS not null
  AND t.suunnitteluyksikko != 'euroa'
-- Eikä tehtävä kuulu mihinkään rahavaraukseen
  AND (select count(*) from rahavaraustehtava where tehtava_id = t.id) = 0
ORDER BY tro.id, t.jarjestys;

-- name: hae-tarjouksen-tehtavamaarien-viimeisin-muokkaaja
SELECT GREATEST(ut.muokattu, ut.luotu) AS viimeisin_muokkaus,
       CASE WHEN kr.rooli = 'jarjestelmavastuuhenkilo' THEN 'Järjestelmävastaava'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
           END AS viimeisin_muokkaaja
FROM urakka_tehtavamaara ut
         LEFT JOIN kayttaja k ON COALESCE(ut.muokkaaja, ut.luoja) = k.id
         LEFT JOIN kayttaja_rooli kr ON k.id = kr.kayttaja
WHERE ut.urakka = :urakkaid
ORDER BY viimeisin_muokkaus DESC
LIMIT 1;

-- name: hae-tarjous-tehtava-idlla
SELECT id, urakka as urakka_id, tehtava as tehtava_id, maara, muokattu, muokkaaja
  FROM urakka_tehtavamaara
 WHERE tehtava = :tehtavaid
   AND urakka = :urakkaid
   AND "hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi;

-- name: paivita-tarjous-tehtava<!
UPDATE urakka_tehtavamaara
   SET maara = :maara,
       muokattu = NOW(),
       muokkaaja = :muokkaaja
 WHERE id = :tarjous_tehtava_id;

-- name: lisaa-tarjous-tehtava<!
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luoja, luotu)
VALUES (:urakkaid, :hoitokauden-alkuvuosi, :tehtavaid, :maara, :luoja, NOW())
RETURNING id, urakka as urakka_id, "hoitokauden-alkuvuosi", tehtava as tehtava_id, maara, muokattu, muokkaaja, luotu, luoja;
