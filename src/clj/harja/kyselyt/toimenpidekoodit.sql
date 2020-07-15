-- name: hae-kaikki-toimenpidekoodit
-- Listaa kaikki toimenpidekoodit.
SELECT
  t.id,
  t.koodi,
  t.nimi,
  t.emo,
  t.taso,
  t.voimassaolo_alkuvuosi as "voimassaolon-alkuvuosi",
  t.voimassaolo_loppuvuosi as "voimassaolon-loppuvuosi",
  t.yksikko,
  t.jarjestys,
  t.hinnoittelu,
  t.poistettu,
  t.luoja        AS luoja_id,
  k.kayttajanimi AS luoja_kayttajanimi,
  k.etunimi      AS luoja_etunimi,
  k.sukunimi     AS luoja_sukunimi,
  api_seuranta   AS "api-seuranta"
FROM toimenpidekoodi t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  WHERE t.piilota IS NOT TRUE

-- name: lisaa-toimenpidekoodi<!
-- Lisää uuden 4. tason toimenpidekoodin (tehtäväkoodi).
INSERT INTO toimenpidekoodi (nimi, emo, taso, voimassaolo_alkuvuosi, voimassaolo_loppuvuosi, yksikko, hinnoittelu, api_seuranta, luoja, luotu, muokattu)
VALUES (:nimi, :emo, 4, :voimassaolon-alkuvuosi, :voimassaolon-loppuvuosi, :yksikko, :hinnoittelu :: hinnoittelutyyppi [], :apiseuranta, :kayttajaid, NOW(), NOW());

-- name: poista-toimenpidekoodi!
-- Poistaa (merkitsee poistetuksi) annetun toimenpidekoodin.
UPDATE toimenpidekoodi
SET poistettu = TRUE, muokkaaja = :kayttajaid, muokattu = NOW()
WHERE id = :id;

-- name: muokkaa-toimenpidekoodi!
-- Muokkaa annetun toimenpidekoodin nimen.
UPDATE toimenpidekoodi
SET muokkaaja         = :kayttajaid,
    muokattu          = NOW(),
    poistettu         = :poistettu,
    nimi              = :nimi,
    voimassaolo_alkuvuosi  = :voimassaolon-alkuvuosi,
    voimassaolo_loppuvuosi = :voimassaolon-loppuvuosi,
    yksikko           = :yksikko,
    hinnoittelu       = :hinnoittelu :: hinnoittelutyyppi[],
    api_seuranta      = :apiseuranta
WHERE id = :id;

-- name: viimeisin-muokkauspvm
-- Antaa MAX(muokattu) päivämäärän toimenpidekoodeista
SELECT MAX(muokattu) AS muokattu
FROM toimenpidekoodi;

--name: hae-neljannen-tason-toimenpidekoodit
SELECT
  id,
  koodi,
  nimi,
  emo,
  taso,
  yksikko
FROM toimenpidekoodi
WHERE poistettu IS NOT TRUE AND
      piilota IS NOT TRUE AND
      emo = :emo;

--name: hae-emon-nimi
SELECT nimi
FROM toimenpidekoodi
WHERE id = (SELECT emo
            FROM toimenpidekoodi
            WHERE id = :id);

-- name: onko-olemassa?
-- single?: true
-- Ei karsi piilotettuja toimenpidekoodeja tarkistuksesta. Tämä auttaa ongelmanselvitystä Sampo-integraatiossa.
SELECT exists(SELECT id
              FROM toimenpidekoodi
              WHERE koodi = :toimenpidekoodi);

-- name: onko-olemassa-idlla?
-- single?: true
SELECT exists(SELECT id
              FROM toimenpidekoodi
              WHERE id = :id AND piilota IS NOT TRUE);

-- name: hae-apin-kautta-seurattavat-yksikkohintaiset-tehtavat
SELECT
  tpk.id,
  tpk.nimi,
  tpk.yksikko
FROM toimenpidekoodi tpk
WHERE
  tpk.poistettu IS NOT TRUE AND
  tpk.piilota IS NOT TRUE AND
  tpk.api_seuranta AND
  tpk.hinnoittelu @> '{yksikkohintainen}';

-- name: hae-apin-kautta-seurattavat-kokonaishintaiset-tehtavat
SELECT
  tpk.id,
  tpk.nimi,
  tpk.yksikko
FROM toimenpidekoodi tpk
WHERE
  tpk.poistettu IS NOT TRUE AND
  tpk.piilota IS NOT TRUE AND
  tpk.api_seuranta AND
  tpk.hinnoittelu @> '{kokonaishintainen}';


-- name: hae-tehtavan-id
SELECT tk4.id
FROM toimenpidekoodi tk4
  JOIN toimenpidekoodi tk3 ON tk4.emo=tk3.id
WHERE tk4.nimi=:nimi AND
      tk3.koodi IN (select koodi from toimenpidekoodi where id IN
                                    (select toimenpide from toimenpideinstanssi where urakka = :urakkaid))
LIMIT 1;

-- name: hae-hinnoittelu
-- Suljetaan pois tehtävät, joille ei saa kirjata toteumia.
SELECT hinnoittelu
FROM toimenpidekoodi
WHERE id = :id and piilota IS NOT TRUE
-- Tehtävä on piilotettu, jos sitä ei käytetä mistään urakasta.
-- Hoidon päällystyksen paikkauksen vanhat koodit TUOTANNOSSA.
                                    and id not in
                                        (select id from toimenpidekoodi where id in (
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
    FROM toimenpidekoodi
    WHERE koodi = :koodi AND piilota IS NOT TRUE);

-- name: hae-tehtavaryhmat
SELECT id, nimi, jarjestys
FROM tehtavaryhma
WHERE tyyppi = 'alataso'
ORDER BY jarjestys;
