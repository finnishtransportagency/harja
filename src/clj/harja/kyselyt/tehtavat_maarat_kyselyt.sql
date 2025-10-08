-- name: hae-maaramitattavat-tehtavat
WITH rahavaraustehtava AS (
    SELECT rt.id, rt.tehtava_id
    FROM rahavaraus_urakka rvu
             JOIN rahavaraus_tehtava rt ON rvu.rahavaraus_id = rt.rahavaraus_id
    WHERE rvu.urakka_id = :urakkaid
)
SELECT t.id as tehtava_id, t.nimi, t.tehtavaryhma as tehtavaryhmaid, t.yksikko, t.suunnitteluyksikko, t.jarjestys,
       tr.nimi as tehtavaryhmanimi, tro.otsikko as tehtavaryhmaotsikko, tp.nimi as toimenpidenimi, tt.maara as tarjous_maara
FROM tehtavaryhma tr
      JOIN tehtavaryhmaotsikko tro ON tr.tehtavaryhmaotsikko_id = tro.id
      JOIN tehtava t ON tr.id = t.tehtavaryhma
            AND t."mhu-tehtava?" IS TRUE
            AND t.poistettu IS NOT TRUE
            AND t.piilota IS NOT TRUE
      JOIN toimenpide tp ON t.emo = tp.id
      LEFT JOIN tarjous_tehtavamaara tt ON tt.tehtava_id = t.id AND tt.urakka_id = :urakkaid
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
SELECT GREATEST(tt.muokattu, tt.luotu) AS viimeisin_muokkaus,
       CASE WHEN kr.rooli = 'jarjestelmavastuuhenkilo' THEN 'Järjestelmävastaava'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
           END AS viimeisin_muokkaaja
FROM tarjous_tehtavamaara tt
         LEFT JOIN kayttaja k ON COALESCE(tt.muokkaaja, tt.luoja) = k.id
         LEFT JOIN kayttaja_rooli kr ON k.id = kr.kayttaja
WHERE tt.urakka_id = :urakkaid
ORDER BY viimeisin_muokkaus DESC
LIMIT 1;

-- name: hae-tarjous-tehtava-idlla
SELECT id, urakka_id, tehtava_id, maara, muokattu, muokkaaja
  FROM tarjous_tehtavamaara
 WHERE tehtava_id = :tehtavaid
AND urakka_id = :urakkaid;

-- name: paivita-tarjous-tehtava<!
UPDATE tarjous_tehtavamaara
   SET maara = :maara,
       muokattu = NOW(),
       muokkaaja = :muokkaaja
 WHERE id = :tarjous_tehtava_id;

-- name: lisaa-tarjous-tehtava<!
INSERT INTO tarjous_tehtavamaara (urakka_id, tehtava_id, maara, luoja, luotu)
VALUES (:urakkaid, :tehtavaid, :maara, :luoja, NOW())
RETURNING id, urakka_id, tehtava_id, maara, muokattu, muokkaaja, luotu, luoja;
