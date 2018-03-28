-- name: hae-kanavien-urakkakohtaiset-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan ja aikavälin perusteella
-- Kaikki kohteet ja kaikki tehtävät
(SELECT
   ktp.pvm                                                        AS pvm,
   tpk.nimi                                                       AS tehtava,
   k.nimi                                                         AS kohde,
   ko.nimi                                                        AS kohteenosa,
   hk.nimi                                                        AS huoltokohde,
   ktp.lisatieto                                                  AS lisatieto,
   ''                                                             AS otsikko,
   '-'                                                            AS hinta_ryhma,
   'sopimushintainen-tyo-tai-materiaali'                          AS hinnoittelu_ryhma,
   sopimus_tyo.id                                                 AS tyo_id,
   sopimus_tyo.maara                                              AS maara,
   tyo.yksikkohinta                                               AS yksikkohinta,
   COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) AS summa,
   ktp.urakka                                                     AS urakka,
   ktp.id                                                         AS toimenpide_id,
   ktp.tyyppi                                                     AS toimenpidetyyppi,
   ktp.toimenpidekoodi                                            AS tehtava_id,
   ktp."kohde-id"                                                 AS kohde_id,
   ktp."kohteenosa-id"                                            AS kohteenosa_id,
   ktp.huoltokohde                                                AS huoltokohde_id,
   NULL                                                           AS materiaali_id
 FROM kan_toimenpide ktp
   JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
   JOIN kan_huoltokohde hk ON (ktp.huoltokohde = hk.id)
   JOIN kan_kohde k ON (ktp."kohde-id" = k.id)
   JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu IS NOT TRUE)
   JOIN yksikkohintainen_tyo tyo ON (tyo.tehtava = sopimus_tyo."toimenpidekoodi-id")
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid
   AND (ktp.pvm BETWEEN '2017-03-07' AND '2018-05-13')
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(SELECT
   ktp.pvm                                                                        AS pvm,
   tpk.nimi                                                                       AS tehtava,
   k.nimi                                                                         AS kohde,
   ko.nimi                                                                        AS kohteenosa,
   hk.nimi                                                                        AS huoltokohde,
   ktp.lisatieto                                                                  AS lisatieto,
   hinnoiteltu_tyo.otsikko                                                        AS otsikko,
   hinnoiteltu_tyo.ryhma                                                          AS hinta_ryhma,
   CASE
   WHEN hinnoiteltu_tyo.ryhma = 'oma'
     THEN 'omakustanteinen-tyo-tai-materiaali'
   WHEN (hinnoiteltu_tyo.ryhma = 'materiaali' AND hinnoiteltu_tyo.ryhma IS NULL)
     THEN 'sopimushintainen-tyo-tai-materiaali'
   WHEN (hinnoiteltu_tyo.ryhma = 'materiaali' AND hinnoiteltu_tyo.ryhma NOTNULL)
     THEN 'varaosat-ja-materiaalit'
   WHEN hinnoiteltu_tyo.ryhma = 'tyo'
     THEN 'muu-tyo'
   WHEN hinnoiteltu_tyo.ryhma = 'muu'
     THEN 'muut-kulut'
   END                                                                            AS hinnoittelu_ryhma,
   hinnoiteltu_tyo.id                                                             AS hinta_id,
   hinnoiteltu_tyo.maara                                                          AS maara,
   hinnoiteltu_tyo.yksikkohinta                                                   AS yksikkohinta,
   COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) AS summa,
   ktp.urakka                                                                     AS urakka,
   ktp.id                                                                         AS toimenpide_id,
   ktp.tyyppi                                                                     AS toimenpidetyyppi,
   ktp.toimenpidekoodi                                                            AS tehtava_id,
   ktp."kohde-id"                                                                 AS kohde_id,
   ktp."kohteenosa-id"                                                            AS kohteenosa_id,
   ktp.huoltokohde                                                                AS huoltokohde_id,
   hinnoiteltu_tyo."materiaali-id"                                                AS materiaali_id
 FROM kan_toimenpide ktp
   JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
   JOIN kan_huoltokohde hk ON (ktp.huoltokohde = hk.id)
   JOIN kan_kohde k ON (ktp."kohde-id" = k.id)
   JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_hinta hinnoiteltu_tyo -- join?
     ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu IS NOT TRUE)
   LEFT OUTER JOIN vv_materiaali sopimus_materiaali ON sopimus_materiaali.id = hinnoiteltu_tyo."materiaali-id"
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid
   AND (ktp.pvm BETWEEN '2017-03-07' AND '2018-05-13')
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi);



-- name: hae-kanavien-tehtavakohtaiset-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan, aikavälin ja tehtävän perusteella
-- Kaikki kohteet
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  sopimus_tyo.id as tyo_id,
  sopimus_tyo.maara as tyo_maara,
  '' AS otsikko,
  'sopimushintainen-tyo' AS ryhma
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
  JOIN  kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = 31 AND
  toimenpidekoodi = :tehtavaid AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13')
UNION
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  hinnoiteltu_tyo.id as hinta_id,
  hinnoiteltu_tyo.maara as hinta_maara,
  hinnoiteltu_tyo.otsikko AS otsikko,
  hinnoiteltu_tyo.ryhma AS ryhma -- TODO: Päättele onko kyseessä sopimushintainen materiaali hinnoiteltu_tyo."materiaali-id" IS NOTNULL
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi AND tpk.id = :tehtavaid)
  LEFT OUTER JOIN  kan_hinta hinnoiteltu_tyo ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = 31 AND
  toimenpidekoodi = :tehtavaid AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13');




-- name: hae-kanavien-kohdekohtaiset-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan, aikavälin ja kohteen perusteella
-- Kaikki tehtävät
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  sopimus_tyo.id as tyo_id,
  sopimus_tyo.maara as tyo_maara,
  '' AS otsikko,
  'sopimushintainen-tyo' AS ryhma
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
  JOIN  kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = 31 AND
  "kohde-id" = :kohde AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13')
UNION
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  hinnoiteltu_tyo.id as hinta_id,
  hinnoiteltu_tyo.maara as hinta_maara,
  hinnoiteltu_tyo.otsikko AS otsikko,
  hinnoiteltu_tyo.ryhma AS ryhma -- TODO: Päättele onko kyseessä sopimushintainen materiaali hinnoiteltu_tyo."materiaali-id" IS NOTNULL
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi AND tpk.id = :tehtavaid)
  LEFT OUTER JOIN  kan_hinta hinnoiteltu_tyo ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = 31 AND
  "kohde-id" = :kohde AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13');


-- name: hae-kanavien-kohde-ja-tehtavakohtaiset-muutos-ja-lisatyot-raportille
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan, aikavälin, kohteen ja tehtävän perusteella
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  sopimus_tyo.id as tyo_id,
  sopimus_tyo.maara as tyo_maara,
  '' AS otsikko,
  'sopimushintainen-tyo' AS ryhma
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
  JOIN  kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = :urakkaid AND
  "kohde-id" = :kohde AND
  toimenpidekoodi = :tehtavaid AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13')
UNION
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  hinnoiteltu_tyo.id as hinta_id,
  hinnoiteltu_tyo.maara as hinta_maara,
  hinnoiteltu_tyo.otsikko AS otsikko,
  hinnoiteltu_tyo.ryhma AS ryhma -- TODO: Päättele onko kyseessä sopimushintainen materiaali hinnoiteltu_tyo."materiaali-id" IS NOTNULL
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi AND tpk.id = :tehtavaid)
  LEFT OUTER JOIN  kan_hinta hinnoiteltu_tyo ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = 31 AND
  "kohde-id" = :kohde AND
  toimenpidekoodi = :tehtava AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13');

-- name: hae-kanavien-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt aikavälin perusteella
-- Kaikki urakat, kohteet ja tehtävät
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  sopimus_tyo.id as tyo_id,
  sopimus_tyo.maara as tyo_maara,
  '' AS otsikko,
  'sopimushintainen-tyo' AS ryhma
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
  JOIN  kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = :urakka AND (pvm BETWEEN '2018-03-07' AND '2018-03-13')
UNION
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  hinnoiteltu_tyo.id as hinta_id,
  hinnoiteltu_tyo.maara as hinta_maara,
  hinnoiteltu_tyo.otsikko AS otsikko,
  hinnoiteltu_tyo.ryhma AS ryhma -- TODO: Päättele onko kyseessä sopimushintainen materiaali hinnoiteltu_tyo."materiaali-id" IS NOTNULL
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
  LEFT OUTER JOIN  kan_hinta hinnoiteltu_tyo ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  urakka = :urakka AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13');

-- name: hae-kanavien-tehtavakohtaiset-muutos-ja-lisatyot-raportille
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt aikavälin ja tehtävän perusteella
-- Kaikki urakat, kaikki kohteet
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  sopimus_tyo.id as tyo_id,
  sopimus_tyo.maara as tyo_maara,
  '' AS otsikko,
  'sopimushintainen-tyo' AS ryhma
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
  JOIN  kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  toimenpidekoodi = :tehtava AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13')
UNION
select
  ktp.id AS kan_toimenpide_id,
  ktp.urakka AS urakka,
  ktp.tyyppi AS toimenpidetyyppi,
  ktp.pvm AS pvm,
  tpk.nimi AS nimi,
  ktp.lisatieto AS lisatieto,
  hinnoiteltu_tyo.id as hinta_id,
  hinnoiteltu_tyo.maara as hinta_maara,
  hinnoiteltu_tyo.otsikko AS otsikko,
  hinnoiteltu_tyo.ryhma AS ryhma -- TODO: Päättele onko kyseessä sopimushintainen materiaali hinnoiteltu_tyo."materiaali-id" IS NOTNULL
from kan_toimenpide ktp
  JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi AND tpk.id = :tehtavaid)
  LEFT OUTER JOIN  kan_hinta hinnoiteltu_tyo ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu is not TRUE)
WHERE
  tyyppi = 'muutos-lisatyo' AND
  toimenpidekoodi = :tehtava AND
  (pvm BETWEEN '2018-03-07' AND '2018-03-13');



-- name: hae-kanavakohteen-nimi
SELECT nimi FROM kan_kohde where id = :kohdeid;


--name: hae-kanavatoimenpiteen-nimi;
SELECT nimi FROM toimenpidekoodi WHERE id = :tehtavaid;

