-- name: hae-kaikki-toimenpidekoodit
-- Listaa kaikki toimenpidekoodit.
SELECT
    t.id,
    t.koodi,
    t.nimi,
    t.emo,
    t.taso,
    t.poistettu,
    t.luoja        AS luoja_id,
    k.kayttajanimi AS luoja_kayttajanimi,
    k.etunimi      AS luoja_etunimi,
    k.sukunimi     AS luoja_sukunimi,
    -- toimenpiteillä ei ole seuraavia kenttiä, UNION:n takia null-fillataan ne:
    NULL as "voimassaolon-alkuvuosi",
    NULL as "voimassaolon-loppuvuosi",
    NULL as yksikko,
    NULL as jarjestys,
    NULL as hinnoittelu,
    NULL as tehtavaryhma,
    NULL as "api-seuranta"
  FROM toimenpide t
           LEFT JOIN kayttaja k ON t.luoja = k.id
 WHERE t.piilota IS NOT TRUE
 UNION
SELECT
    t.id,
    null as koodi,
    t.nimi,
    t.emo,
    4 as taso,
    t.poistettu,
    t.luoja        AS luoja_id,
    k.kayttajanimi AS luoja_kayttajanimi,
    k.etunimi      AS luoja_etunimi,
    k.sukunimi     AS luoja_sukunimi,
    t.voimassaolo_alkuvuosi as "voimassaolon-alkuvuosi",
    t.voimassaolo_loppuvuosi as "voimassaolon-loppuvuosi",
    t.yksikko,
    t.jarjestys,
    t.hinnoittelu,
    t.tehtavaryhma,
    api_seuranta   AS "api-seuranta"
  FROM tehtava t
           LEFT JOIN kayttaja k ON t.luoja = k.id
 WHERE t.piilota IS NOT TRUE;

-- name: lisaa-toimenpidekoodi<!
-- Lisää uuden 4. tason toimenpidekoodin (tehtäväkoodi).
INSERT INTO tehtava (nimi, emo, taso, voimassaolo_alkuvuosi, voimassaolo_loppuvuosi, yksikko, hinnoittelu, api_seuranta, tehtavaryhma, luoja, luotu, muokattu)
VALUES (:nimi, :emo, 4, :voimassaolon-alkuvuosi, :voimassaolon-loppuvuosi, :yksikko, :hinnoittelu :: hinnoittelutyyppi [], :apiseuranta, :tehtavaryhma, :kayttajaid, NOW(), NOW());

-- name: poista-toimenpidekoodi!
-- Poistaa (merkitsee poistetuksi) annetun toimenpidekoodin.
UPDATE tehtava
SET poistettu = TRUE, muokkaaja = :kayttajaid, muokattu = NOW()
WHERE id = :id;

-- name: muokkaa-toimenpidekoodi!
-- Muokkaa annetun toimenpidekoodin nimen.
UPDATE tehtava
SET muokkaaja         = :kayttajaid,
    muokattu          = NOW(),
    poistettu         = :poistettu,
    nimi              = :nimi,
    voimassaolo_alkuvuosi  = :voimassaolon-alkuvuosi,
    voimassaolo_loppuvuosi = :voimassaolon-loppuvuosi,
    yksikko           = :yksikko,
    hinnoittelu       = :hinnoittelu :: hinnoittelutyyppi[],
    api_seuranta      = :apiseuranta,
    tehtavaryhma      = :tehtavaryhma
WHERE id = :id;

-- name: viimeisin-muokkauspvm
-- Antaa MAX(muokattu) päivämäärän toimenpidekoodeista
SELECT MAX(muokattu) AS muokattu
FROM tehtava;

-- name: onko-olemassa?
-- single?: true
-- Ei karsi piilotettuja toimenpidekoodeja tarkistuksesta. Tämä auttaa ongelmanselvitystä Sampo-integraatiossa.
SELECT exists(SELECT id
              FROM toimenpide
              WHERE koodi = :toimenpidekoodi);

-- name: hae-apin-kautta-seurattavat-yksikkohintaiset-tehtavat
SELECT tpk4.id         as "harja-id",
       tpk4.api_tunnus as "apitunnus",
       tpk4.nimi,
       tpk4.yksikko,
       tpk4.voimassaolo_alkuvuosi,
       tpk4.voimassaolo_loppuvuosi
FROM tehtava tpk4
         JOIN toimenpide tpk3 ON tpk4.emo = tpk3.id
         JOIN toimenpideinstanssi tpi on tpk3.id = tpi.toimenpide
         JOIN urakka u on tpi.urakka = u.id AND u.id = :urakka
WHERE tpk4.poistettu IS NOT TRUE
  AND tpk4.piilota IS NOT TRUE
  AND tpk4.api_seuranta
  AND tpk4.hinnoittelu @> '{yksikkohintainen}'
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER);

-- name: hae-apin-kautta-seurattavat-kokonaishintaiset-tehtavat
SELECT tpk4.id         as "harja-id",
       tpk4.api_tunnus as "apitunnus",
       tpk4.nimi,
       tpk4.yksikko,
       tpk4.voimassaolo_alkuvuosi,
       tpk4.voimassaolo_loppuvuosi
FROM tehtava tpk4
         JOIN toimenpide tpk3 ON tpk4.emo = tpk3.id
         JOIN toimenpideinstanssi tpi on tpk3.id = tpi.toimenpide
         JOIN urakka u on tpi.urakka = u.id AND u.id = :urakka
WHERE tpk4.poistettu IS NOT TRUE
  AND tpk4.piilota IS NOT TRUE
  AND tpk4.api_seuranta
  AND tpk4.hinnoittelu @> '{kokonaishintainen}'
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER);


-- name: hae-tehtavan-id
SELECT tk4.id
FROM tehtava tk4
  JOIN toimenpide tk3 ON tk4.emo=tk3.id
WHERE tk4.nimi=:nimi AND
      tk3.koodi IN (select koodi from toimenpide where id IN
                                    (select toimenpide from toimenpideinstanssi where urakka = :urakkaid))
LIMIT 1;

-- name: hae-tehtava-apitunnisteella
-- single?: true
SELECT tpk4.id
FROM tehtava tpk4,
     urakka u
WHERE api_tunnus = :apitunnus
  AND u.id = :urakka
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER);


-- name: hae-hinnoittelu
-- Suljetaan pois tehtävät, joille ei saa kirjata toteumia.
SELECT tpk4.hinnoittelu as hinnoittelu
FROM tehtava tpk4
         JOIN toimenpide tpk3 ON tpk4.emo = tpk3.id
         JOIN toimenpideinstanssi tpi on tpk3.id = tpi.toimenpide
         JOIN urakka u on tpi.urakka = u.id AND u.id = :urakka
WHERE tpk4.api_tunnus = :apitunnus and tpk4.piilota IS NOT TRUE
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
-- Tehtävä on piilotettu, jos sitä ei käytetä mistään urakasta.
-- Hoidon päällystyksen paikkauksen vanhat koodit TUOTANNOSSA.
                                    and tpk4.id not in
                                        (select id from tehtava where id in (
                                          1417,
                                          1418,
                                          1420,
                                          1415,
                                          1416,
                                          1397,
                                          1401,
                                          1400,
                                          1399,
                                          6868,
                                          1421,
                                          1433,
                                          1434,
                                          6954,
                                          6962,
                                          6951,
                                          6966,
                                          6963,
                                          6970,
                                          6968,
                                          5980,
                                          6967,
                                          6987,
                                          5979,
                                          6877,
                                          1410,
                                          1419,
                                          1409) AND nimi in  ('Päällysteiden paikkaus -kylmäpäällyste ml. SOP',
                                                                                    'Päällysteiden paikkaus - kuumapäällyste',
                                                                                    'Päällysteiden paikkaus - konetiivistetty -valuasfaltti',
                                                                                    'Päällysteiden paikkaus -saumojen juottaminen mastiksilla',
                                                                                    'Päällysteiden paikkaus -saumojen juottaminen bitumilla',
                                                                                    'SIP paikkaus (kesto+kylmä)',
                                                                                    'Sillan kannen päällysteen päätysauman korjaukset',
                                                                                    'Sillan päällysteen halkeaman avarrussaumaus',
                                                                                    'Sillan päällysteen halkeaman sulkeminen',
                                                                                    'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -saumojen juottaminen bitumilla',
                                                                                    'Päällysteen korjaus mastiksilla siltakohteiden heitoissa',
                                                                                    'Kuumapäällyste, valuasfaltti',
                                                                                    'Kuumapäällyste, ab käsityönä',
                                                                                    'Reunapalkin ja päällysteen välisen sauman tiivistäminen',
                                                                                    'Päällysteiden paikkaus - valuasfaltti',
                                                                                    'Päällysteiden paikkaus - Konetiivistetty massasaumaus 20 cm leveä',
                                                                                    'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP',
                                                                                    'Päällysteiden paikkaus, kylmäpäällyste',
                                                                                    'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - massasaumaus',
                                                                                    'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - kuumapäällyste',
                                                                                    'Konetiivistetty massasaumaus 20 cm leveä',
                                                                                   'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -kylmäpäällyste ml. SOP',
                                                                                   'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - valuasvaltti',
                                                                                   'Konetiivistetty massasaumaus 10 cm leveä',
                                                                                   'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty valuasvaltti',
                                                                                   'Reunapalkin ja päällysteen väl. sauman tiivistäminen',
                                                                                   'Päällysteiden paikkaus - massasaumaus',
                                                                                   'Reunapalkin liikuntasauman tiivistämin'));


-- name: onko-kaytossa?
-- single?: true
-- Tarkistaa onko toimenpidekoodi käytössä ja saako siihen liittää toimenpideinstanssia.
-- Piilota = koodi täysin käytöstä poistettu (poistettu = voi olla käytössä jo alkaneissa urakoissa)
SELECT exists(
    SELECT id
    FROM toimenpide
    WHERE koodi = :koodi AND piilota IS NOT TRUE);

-- name: hae-tehtavaryhmat
SELECT id, nimi, jarjestys
FROM tehtavaryhma
ORDER BY jarjestys;

-- name: listaa-tehtavat
-- Listataan tehtävät APIa varten toimenpidekoodin alatasot eli tehtävät
SELECT t.id, t.nimi, t.voimassaolo_alkuvuosi, t.voimassaolo_loppuvuosi, t.jarjestys, t.jarjestys, t.emo,
       t.yksikko, t.suunnitteluyksikko,  t.hinnoittelu,
       t.suoritettavatehtava, t.tehtavaryhma, t."mhu-tehtava?" as ensisijainen, t.yksiloiva_tunniste,
       t.kasin_lisattava_maara, t."raportoi-tehtava?", t.materiaaliluokka_id,
       t.materiaalikoodi_id, t.aluetieto, t.piilota, t.poistettu, t.luotu, t.muokattu
  FROM tehtava t
       LEFT JOIN toimenpide emo ON t.emo = emo.id
 WHERE (t.poistettu IS FALSE OR emo.poistettu IS FALSE OR (emo.poistettu IS TRUE AND t.poistettu IS FALSE))
 ORDER BY nimi ASC;

-- name: listaa-tehtavaryhmat
-- Listataan tehtäväryhmät APIa varten
SELECT id, nimi, otsikko, jarjestys, poistettu, versio, yksiloiva_tunniste, luotu, muokattu
  FROM tehtavaryhma
 ORDER BY otsikko ASC, nimi ASC;
