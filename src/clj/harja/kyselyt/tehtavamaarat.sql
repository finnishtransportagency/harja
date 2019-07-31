-- name: hae-hoitokauden-tehtavamaarat-urakassa
SELECT ut.urakka                  as "urakka",
       ut."hoitokauden-alkuvuosi" as "hoitokauden-alkuvuosi",
       ut.tehtava                 as "tehtava-id",
       ut.maara                   as "maara"
FROM urakka_tehtavamaara ut
       JOIN urakka u on ut.urakka = u.id
       JOIN toimenpidekoodi tpk on ut.tehtava = tpk.id
WHERE ut.urakka = :urakka
  AND ut."hoitokauden-alkuvuosi" = :hoitokausi
  AND ut.poistettu IS NOT TRUE;

-- name: paivita-tehtavamaara!
-- Päivittää urakan hoitokauden tehtävämäärät
UPDATE urakka_tehtavamaara
SET maara     = :maara,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE urakka = :urakka
  AND "hoitokauden-alkuvuosi" = :hoitokausi
  AND tehtava = :tehtava;

-- name: lisaa-tehtavamaara<!
INSERT INTO urakka_tehtavamaara
  (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu, luoja)
VALUES (:urakka, :hoitokausi, :tehtava, :maara, current_timestamp, :kayttaja);

-- name: hae-tehtavahierarkia
SELECT tr1.jarjestys     as "otsikon-jarjestys",
       tpk4.jarjestys    as "jarjestys",
       tpk4.id           as "tehtava-id",
       tr3.otsikko       as "otsikko",
       tpk3.nimi         as "Toimenpide",
       tpk3.koodi        as "Toimenpidekoodi",
       tr1.nimi          as "Ylätaso",
       tr2.nimi          as "Välitaso",
       tr3.nimi          as "Alataso",
       tpk4.nimi         as "tehtava",
       tpk4.yksikko      as "Yksikkö",
       tpk4.api_seuranta as "Seurataan API-n kautta",
       tpk4.api_tunnus   as "API-tunnus",
       tpk4.poistettu    as "Poistettu",
       tpk4.piilota      as "Piilota", -- älä näytä riviä käyttäjälle
       tpk4.ensisijainen as "Ensisijainen"
FROM tehtavaryhma tr1
       JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
       JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
       LEFT JOIN toimenpidekoodi tpk4 ON tr3.id = tpk4.tehtavaryhma and tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                                         tpk4.poistettu is not true AND tpk4.piilota is not true
       JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
WHERE tr1.emo is null
ORDER BY tpk4.jarjestys, tpk4.ensisijainen desc;

-- name: hae-tehtavahierarkia-maarineen
SELECT ut.urakka                  as "urakka",
       ut."hoitokauden-alkuvuosi" as "hoitokauden-alkuvuosi",
       tr1.jarjestys              as "otsikon-jarjestys",
       tpk4.jarjestys             as "jarjestys",
       tpk4.id                    as "tehtava-id",
       ut.maara                   as "maara",
       tr3.otsikko                as "otsikko",
       tpk3.nimi                  as "Toimenpide",
       tpk3.koodi                 as "Toimenpidekoodi",
       tr1.nimi                   as "Ylätaso",
       tr2.nimi                   as "Välitaso",
       tr3.nimi                   as "Alataso",
       tpk4.nimi                  as "tehtava",
       tpk4.yksikko               as "Yksikkö",
       tpk4.api_seuranta          as "Seurataan API-n kautta",
       tpk4.api_tunnus            as "API-tunnus",
       tpk4.poistettu             as "Poistettu",
       tpk4.piilota               as "Piilota", -- älä näytä riviä käyttäjälle
       tpk4.ensisijainen          as "Ensisijainen"
FROM tehtavaryhma tr1
       JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
       JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
       LEFT JOIN toimenpidekoodi tpk4 ON tr3.id = tpk4.tehtavaryhma AND tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                                         tpk4.poistettu is not true AND tpk4.piilota is not true
       JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
       LEFT OUTER JOIN urakka_tehtavamaara ut
                       ON tpk4.id = ut.tehtava AND ut.urakka = :urakka AND ut."hoitokauden-alkuvuosi" = :hoitokausi
       LEFT OUTER JOIN urakka u ON ut.urakka = u.id
WHERE tr1.emo is null
ORDER BY tpk4.jarjestys, tpk4.ensisijainen desc;

-- name: hae-validit-tehtava-idt
SELECT id as "tehtava-id"
FROM toimenpidekoodi
WHERE tehtavaryhma IS NOT NULL
  AND poistettu IS NOT TRUE
  AND piilota IS NOT TRUE;
