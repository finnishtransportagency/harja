-- name: hae-kanavien-urakkakohtaiset-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan ja aikavälin perusteella
-- Kaikki kohteet ja kaikki tehtävät
(select
   ktp.id AS kan_toimenpide_id,
   ktp.urakka AS urakka,
   ktp.tyyppi AS toimenpidetyyppi,
   ktp.toimenpidekoodi,
   ktp.pvm AS pvm,
   ktp.toimenpidekoodi AS tehtava,
   ktp."kohde-id" AS kohde,
   ktp."kohteenosa-id" AS kohteenosa,
   ktp.huoltokohde AS huoltokohde,
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
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(select
   ktp.id AS kan_toimenpide_id,
   ktp.urakka AS urakka,
   ktp.tyyppi AS toimenpidetyyppi,
   ktp.toimenpidekoodi,
   ktp.pvm AS pvm,
   ktp.toimenpidekoodi AS tehtava,
   ktp."kohde-id" AS kohde,
   ktp."kohteenosa-id" AS kohteenosa,
   ktp.huoltokohde AS huoltokohde,
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
   (pvm BETWEEN '2018-03-07' AND '2018-03-13')
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
  urakka = 31 AND
  toimenpidekoodi = :tehtava AND
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
  urakka = 31 AND
  "kohde-id" = :kohde AND
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

