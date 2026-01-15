-- name: hae-maaramitattavat-tehtavat
WITH rahavaraustehtava AS
    (
    SELECT rt.id, rt.tehtava_id
    FROM rahavaraus_urakka rvu
             JOIN rahavaraus_tehtava rt ON rvu.rahavaraus_id = rt.rahavaraus_id
    WHERE rvu.urakka_id = :urakkaid ),
 muutokset_raakadata AS (
    -- Hae muutokset tarjousmäärällä
    -- Huom: mhu_muutos_tehtava_ja_maaraluettelo päätalussa on aina vain uusin versio
    -- per (muutos, tehtava, hoitokauden_alkuvuosi) yhdistelmä, ei tarvita versiosuodatusta
    SELECT mm.id, 
           mmtm.maaramuutos, 
           mmtm.tehtava as tehtavaid,
           mm.voimassa_alkaen, 
           mm.syy,
           COALESCE(v.tarjous_maara, 0) as tarjous_maara
      FROM mhu_muutos mm
           JOIN mhu_muutos_tehtava_ja_maaraluettelo mmtm ON mmtm.muutos = mm.id
           LEFT JOIN urakka_tehtavamaara_yhteenveto v 
                ON v.tehtava = mmtm.tehtava 
                AND v.urakka = mm.urakka
                AND v.hoitokauden_alkuvuosi = mmtm.hoitokauden_alkuvuosi
    WHERE mm.urakka = :urakkaid
      AND mmtm.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
      AND mm.poistettu IS NOT TRUE
    ),
 muutokset_kumulatiiviset AS (
    -- Laske kumulatiiviset arvot window functionilla
    SELECT 
        id,
        tehtavaid,
        maaramuutos,
        voimassa_alkaen,
        syy,
        -- Edellinen määrä = tarjous + summa kaikista edellisistä muutoksista
        tarjous_maara + COALESCE(
            SUM(maaramuutos) OVER (
                PARTITION BY tehtavaid
                ORDER BY voimassa_alkaen, id
                ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
            ), 
            0
        ) as edellinen_maara,
        -- Uusi määrä = tarjous + summa tästä muutoksesta ja kaikista edellisistä
        tarjous_maara + SUM(maaramuutos) OVER (
            PARTITION BY tehtavaid
            ORDER BY voimassa_alkaen, id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) as uusi_maara
    FROM muutokset_raakadata
    )
SELECT t.id as tehtava_id, t.nimi, t.tehtavaryhma as tehtavaryhmaid, t.yksikko, t.suunnitteluyksikko, t.jarjestys,
       tr.nimi as tehtavaryhmanimi, tro.otsikko as tehtavaryhmaotsikko, tp.nimi as toimenpidenimi, 
       v.tarjous_maara,
       (SELECT array_agg(row(id, edellinen_maara, maaramuutos, uusi_maara, tehtavaid, voimassa_alkaen, syy) ORDER BY voimassa_alkaen)
        FROM muutokset_kumulatiiviset WHERE muutokset_kumulatiiviset.tehtavaid = t.id) AS muutokset,
       v.muutossumma AS muutos_maaramuutos,
       v.laskettu_maara AS yhteensa
FROM tehtavaryhma tr
      JOIN tehtavaryhmaotsikko tro ON tr.tehtavaryhmaotsikko_id = tro.id
      JOIN tehtava t ON tr.id = t.tehtavaryhma
            AND t."mhu-tehtava?" IS TRUE
            AND t.poistettu IS NOT TRUE
            AND t.piilota IS NOT TRUE
      JOIN toimenpide tp ON t.emo = tp.id
      LEFT JOIN urakka_tehtavamaara_yhteenveto v ON v.tehtava = t.id AND v.urakka = :urakkaid AND v.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
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
       CASE WHEN k.piilota_nimi IS TRUE THEN 'Järjestelmän ylläpito'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
           END AS viimeisin_muokkaaja
FROM urakka_tehtavamaara ut
         LEFT JOIN kayttaja k ON COALESCE(ut.muokkaaja, ut.luoja) = k.id
WHERE ut.urakka = :urakkaid
  AND (ut.luotu IS NOT NULL OR ut.muokattu IS NOT NULL)
  AND ut."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi
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


-- name: debug-hae-muutokset-ja-versiot
-- Hae urakan kaikki muutokset versiotietoineen debuggausta varten
SELECT 
    mm.id as muutos_id,
    mm.voimassa_alkaen,
    mm.syy as muutoksen_syy,
    mm.poistettu as muutos_poistettu,
    mmtm.tehtava as tehtava_id,
    t.nimi as tehtavan_nimi,
    mmtm.hoitokauden_alkuvuosi,
    mmtm.maaramuutos,
    mmtm.versio,
    -- Onko tämä uusin versio tälle tehtävä+muutos yhdistelmälle?
    CASE WHEN mmtm.versio = (
        SELECT MAX(mmtm2.versio)
        FROM mhu_muutos_tehtava_ja_maaraluettelo mmtm2
        WHERE mmtm2.muutos = mmtm.muutos
          AND mmtm2.tehtava = mmtm.tehtava
    ) THEN 'KYLLÄ' ELSE 'EI' END as onko_uusin_versio,
    -- Montako versiota tällä tehtävä+muutos yhdistelmällä on?
    (SELECT COUNT(*)
     FROM mhu_muutos_tehtava_ja_maaraluettelo mmtm3
     WHERE mmtm3.muutos = mmtm.muutos
       AND mmtm3.tehtava = mmtm.tehtava
    ) as versioita_yhteensa,
    -- VIEW:n laskemat arvot
    v.tarjous_maara,
    v.muutossumma,
    v.laskettu_maara
FROM mhu_muutos mm
     JOIN mhu_muutos_tehtava_ja_maaraluettelo mmtm ON mmtm.muutos = mm.id
     LEFT JOIN tehtava t ON t.id = mmtm.tehtava
     LEFT JOIN urakka_tehtavamaara_yhteenveto v 
          ON v.tehtava = mmtm.tehtava 
          AND v.urakka = mm.urakka
          AND v.hoitokauden_alkuvuosi = mmtm.hoitokauden_alkuvuosi
WHERE mm.urakka = :urakkaid
  -- Valinnainen suodatus hoitokaudelle
  AND (:hoitokauden-alkuvuosi::INTEGER IS NULL OR mmtm.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi)
  -- Valinnainen suodatus tehtävälle
  AND (:tehtavaid::INTEGER IS NULL OR mmtm.tehtava = :tehtavaid)
  -- Valinnainen suodatus: näytä vain uusimmat versiot
  AND (:vain-uusimmat-versiot::BOOLEAN IS NOT TRUE OR mmtm.versio = (
      SELECT MAX(mmtm2.versio)
      FROM mhu_muutos_tehtava_ja_maaraluettelo mmtm2
      WHERE mmtm2.muutos = mmtm.muutos
        AND mmtm2.tehtava = mmtm.tehtava
  ))
ORDER BY mm.voimassa_alkaen DESC, mmtm.tehtava, mmtm.versio DESC;