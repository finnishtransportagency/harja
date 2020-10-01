-- name: hae-hoitokauden-tehtavamaarat-urakassa
-- Hakee kannasta nykytilanteen, jota käytetään päättelemään luodaanko vai päivitetäänkö tallennettavaa tietoa.
SELECT ut.urakka                  as "urakka",
       ut."hoitokauden-alkuvuosi" as "hoitokauden-alkuvuosi",
       ut.tehtava                 as "tehtava-id",
       ut.maara                   as "maara"
FROM urakka_tehtavamaara ut
WHERE ut.urakka = :urakka
  AND ut."hoitokauden-alkuvuosi" = :hoitokausi
  AND ut.poistettu IS NOT TRUE;

-- name: lisaa-tehtavamaara<!
INSERT INTO urakka_tehtavamaara
    (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu, luoja)
VALUES (:urakka, :hoitokausi, :tehtava, :maara, current_timestamp, :kayttaja);

-- name: paivita-tehtavamaara!
-- Päivittää urakan hoitokauden tehtävämäärät
UPDATE urakka_tehtavamaara
SET maara     = :maara,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE urakka = :urakka
  AND "hoitokauden-alkuvuosi" = :hoitokausi
  AND tehtava = :tehtava;

-- name: tehtavaryhmat-ja-toimenpiteet-urakalle
SELECT tpk3.id       as "toimenpide-id",
       tpk3.nimi     as "toimenpide",
       tr3.nimi      as "tehtavaryhma-nimi",
       tr3.id        as "tehtavaryhma-id",
       tr3.jarjestys as "jarjestys",
       tpi.id        as "toimenpideinstanssi"
FROM tehtavaryhma tr1
         JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
         JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
         LEFT JOIN toimenpidekoodi tpk4
                   ON tr3.id = tpk4.tehtavaryhma and tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                      tpk4.poistettu is not true AND tpk4.piilota is not true
         JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
         JOIN toimenpideinstanssi tpi on tpi.toimenpide = tpk3.id and tpi.urakka = :urakka
WHERE tr1.emo is null
order by tr3.jarjestys;

-- name: hae-tehtavahierarkia
-- Palauttaa tehtävähierarkian kokonaisuudessaan.
-- Käytä tehtävä- ja määräluettelossa hierarkian hakemiseen SQL-lausetta: hae-tehtavahierarkia-maarineen.
SELECT tr1.jarjestys           as "otsikon-jarjestys",
       tpk4.jarjestys          as "jarjestys",
       tpk4.id                 as "tehtava-id",
       tr3.otsikko             as "otsikko",
       tpk3.nimi               as "Toimenpide",
       tpk3.koodi              as "Toimenpidekoodi",
       tr1.nimi                as "ylataso",
       tr1.id                  as "ylataso-id",
       tr2.nimi                as "valitaso",
       tr2.id                  as "valitaso-id",
       tr3.nimi                as "alataso",
       tr3.id                  as "alataso-id",
       tpk4.nimi               as "tehtava",
       tpk4.suunnitteluyksikko as "yksikko",
       tpk4.api_seuranta       as "API-seuranta",
       tpk4.api_tunnus         as "API-tunnus",
       tpk4.poistettu          as "Poistettu",
       tpk4.piilota            as "Piilota", -- älä näytä riviä käyttäjälle
       tpk4.ensisijainen       as "Ensisijainen",
       tpk4.voimassaolo_alkuvuosi          as "voimassaolo_alkuvuosi",
       tpk4.voimassaolo_loppuvuosi          as "voimassaolo_loppuvuosi"
FROM tehtavaryhma tr1
         JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
         JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
         LEFT JOIN toimenpidekoodi tpk4
                   ON tr3.id = tpk4.tehtavaryhma and tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                      tpk4.poistettu is not true AND tpk4.piilota is not true
         JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
         JOIN toimenpideinstanssi tpi on tpk3.id = tpi.toimenpide
         JOIN urakka u on tpi.urakka = u.id AND u.id = :urakka
WHERE tr1.emo is null
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
ORDER BY tpk4.jarjestys, tpk4.ensisijainen desc;

-- name: hae-tehtavahierarkia-maarineen
-- Palauttaa tehtävähierarkian käyttöliittymän Suunnittelu > Tehtävä- ja määräluettelo-näkymää varten.
-- Äkillistä hoitotyötä ja Kolmansien osapuolten aiheuttaminen vahinkojen korjausta ei suunnitella tehtävälistalla.
SELECT ut.urakka                  as "urakka",
       ut."hoitokauden-alkuvuosi" as "hoitokauden-alkuvuosi",
       tr1.jarjestys              as "otsikon-jarjestys",
       tpk4.jarjestys             as "jarjestys",
       tpk4.id                    as "tehtava-id",
       ut.maara                   as "maara",
       tr3.otsikko                as "otsikko",
       tpk3.nimi                  as "Toimenpide",
       tpk3.koodi                 as "Toimenpidekoodi",
       tr1.nimi                   as "ylataso",
       tr2.nimi                   as "valitaso",
       tr3.nimi                   as "alataso",
       tpk4.nimi                  as "tehtava",
       tpk4.suunnitteluyksikko    as "yksikko",
       tpk4.api_seuranta          as "API-seuranta",
       tpk4.api_tunnus            as "API-tunnus",
       tpk4.poistettu             as "Poistettu",
       tpk4.piilota               as "Piilota", -- älä näytä riviä käyttäjälle
       tpk4.ensisijainen          as "Ensisijainen",
       tpk4.voimassaolo_alkuvuosi          as "voimassaolo_alkuvuosi",
       tpk4.voimassaolo_loppuvuosi          as "voimassaolo_loppuvuosi"
FROM tehtavaryhma tr1
         JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
         JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
         LEFT JOIN toimenpidekoodi tpk4
                   ON tr3.id = tpk4.tehtavaryhma AND tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                      tpk4.poistettu is not true AND tpk4.piilota is not true AND tpk4.nimi NOT IN
                                                                                  (
                                                                                   'Äkillinen hoitotyö (talvihoito)',
                                                                                   'Äkillinen hoitotyö (l.ymp.hoito)',
                                                                                   'Äkillinen hoitotyö (soratiet)',
                                                                                   'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)',
                                                                                   'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)',
                                                                                   'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)'
                                                                                      )
         JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
         LEFT OUTER JOIN urakka_tehtavamaara ut
                         ON tpk4.id = ut.tehtava AND ut.urakka = :urakka AND ut."hoitokauden-alkuvuosi" in (:hoitokausi)
         LEFT OUTER JOIN urakka u ON ut.urakka = u.id
WHERE tr1.emo is null
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
ORDER BY tpk4.jarjestys, tpk4.ensisijainen desc;


-- name: hae-validit-tehtava-idt
SELECT id as "tehtava-id"
FROM toimenpidekoodi
WHERE tehtavaryhma IS NOT NULL
  AND poistettu IS NOT TRUE
  AND piilota IS NOT TRUE;
