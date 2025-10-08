-- name: hae-maaramitattavat-tehtavat
SELECT t.id as tehtava_id, t.nimi, t.tehtavaryhma as tehtavaryhmaid, t.yksikko, t.suunnitteluyksikko, t.jarjestys,
       tr.nimi as tehtavaryhmanimi, tp.nimi as toimenpidenimi, tt.maara as tarjous_maara
  FROM tehtava t
         JOIN tehtavaryhma tr on tr.id = t.tehtavaryhma
         JOIN toimenpide tp on tp.id = tr.toimenpide_id
         LEFT JOIN tarjous_tehtavamaara tt ON tt.tehtava_id = t.id AND tt.urakka_id = :urakkaid
 WHERE t.tehtavaryhma IS NOT NULL
   AND t.yksikko IS NOT NULL
   AND t.poistettu IS NOT TRUE
   AND t.piilota IS NOT TRUE
   AND t."maaramitattava?" = TRUE
   AND t."mhu-tehtava?" = TRUE
 ORDER BY t.jarjestys;

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
