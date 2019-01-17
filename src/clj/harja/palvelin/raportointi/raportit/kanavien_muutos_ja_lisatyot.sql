-- name: hae-kanavien-urakkakohtaiset-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan ja aikavälin perusteella
-- Kaikki kohteet ja kaikki tehtävät
(SELECT
   ktp.pvm                                                        AS pvm,
   tpk.nimi                                                       AS tehtava,
   k.nimi                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                                    AS kohteenosa,
   hk.nimi                                                        AS huoltokohde,
   ktp.lisatieto                                                  AS lisatieto,
   tpk.nimi                                                       AS otsikko,
   '- 1'                                                          AS hinta_ryhma,
   'sopimushintainen-tyo-tai-materiaali'                          AS hinnoittelu_ryhma,
   sopimus_tyo.id                                                 AS tyo_id,
   sopimus_tyo.maara                                              AS maara,
   tyo.yksikkohinta                                               AS yksikkohinta,
   COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu IS NOT TRUE)
   JOIN yksikkohintainen_tyo tyo ON (tyo.tehtava = sopimus_tyo."toimenpidekoodi-id")
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid
   AND (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(SELECT
   ktp.pvm                                                                        AS pvm,
   tpk.nimi                                                                       AS tehtava,
   k.nimi                                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
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
     COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
   ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
    * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) )  AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
                                       ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
                                        * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) ) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_hinta hinnoiteltu_tyo -- join?
     ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu IS NOT TRUE)
   LEFT OUTER JOIN vv_materiaali sopimus_materiaali ON sopimus_materiaali.id = hinnoiteltu_tyo."materiaali-id"
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid
   AND (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi);

-- name: hae-kanavien-tehtavakohtaiset-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan, aikavälin ja tehtävän perusteella
-- Kaikki kohteet
(SELECT
   ktp.pvm                                                        AS pvm,
   tpk.nimi                                                       AS tehtava,
   k.nimi                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
   hk.nimi                                                        AS huoltokohde,
   ktp.lisatieto                                                  AS lisatieto,
   tpk.nimi                                                       AS otsikko,
   '- tehtava'                                                    AS hinta_ryhma,
   'sopimushintainen-tyo-tai-materiaali'                          AS hinnoittelu_ryhma,
   sopimus_tyo.id                                                 AS tyo_id,
   sopimus_tyo.maara                                              AS maara,
   tyo.yksikkohinta                                               AS yksikkohinta,
   COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu IS NOT TRUE)
   JOIN yksikkohintainen_tyo tyo ON (tyo.tehtava = sopimus_tyo."toimenpidekoodi-id")
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid AND
   ktp.toimenpidekoodi = :tehtavaid AND
   (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(SELECT
   ktp.pvm                                                                        AS pvm,
   tpk.nimi                                                                       AS tehtava,
   k.nimi                                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
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
     COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
   ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
    * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) )  AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
                                       ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
                                        * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) ) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_hinta hinnoiteltu_tyo -- join?
     ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu IS NOT TRUE)
   LEFT OUTER JOIN vv_materiaali sopimus_materiaali ON sopimus_materiaali.id = hinnoiteltu_tyo."materiaali-id"
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid AND
   ktp.toimenpidekoodi = :tehtavaid AND
   (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi);

-- name: hae-kanavien-kohdekohtaiset-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan, aikavälin ja kohteen perusteella
-- Kaikki tehtävät
(SELECT
   ktp.pvm                                                        AS pvm,
   tpk.nimi                                                       AS tehtava,
   k.nimi                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
   hk.nimi                                                        AS huoltokohde,
   ktp.lisatieto                                                  AS lisatieto,
   ''                                                             AS otsikko,
   '- kohde'                                                      AS hinta_ryhma,
   'sopimushintainen-tyo-tai-materiaali'                          AS hinnoittelu_ryhma,
   sopimus_tyo.id                                                 AS tyo_id,
   sopimus_tyo.maara                                              AS maara,
   tyo.yksikkohinta                                               AS yksikkohinta,
   COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu IS NOT TRUE)
   JOIN yksikkohintainen_tyo tyo ON (tyo.tehtava = sopimus_tyo."toimenpidekoodi-id")
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid AND
   ktp."kohde-id" = :kohdeid AND
   (ktp.pvm BETWEEN :alkupvm AND :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(SELECT
   ktp.pvm                                                                        AS pvm,
   tpk.nimi                                                                       AS tehtava,
   k.nimi                                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
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
     COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
   ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
    * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) )  AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
                                       ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
                                        * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) ) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_hinta hinnoiteltu_tyo -- join?
     ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu IS NOT TRUE)
   LEFT OUTER JOIN vv_materiaali sopimus_materiaali ON sopimus_materiaali.id = hinnoiteltu_tyo."materiaali-id"
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid AND
   ktp."kohde-id" = :kohdeid AND
   (ktp.pvm BETWEEN :alkupvm AND :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi);

-- name: hae-kanavien-kohde-ja-tehtavakohtaiset-muutos-ja-lisatyot-raportille
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt urakan, aikavälin, kohteen ja tehtävän perusteella
(SELECT
   ktp.pvm                                                        AS pvm,
   tpk.nimi                                                       AS tehtava,
   k.nimi                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
   hk.nimi                                                        AS huoltokohde,
   ktp.lisatieto                                                  AS lisatieto,
   tpk.nimi                                                       AS otsikko,
   '- tehtävä ja kohde'                                           AS hinta_ryhma,
   'sopimushintainen-tyo-tai-materiaali'                          AS hinnoittelu_ryhma,
   sopimus_tyo.id                                                 AS tyo_id,
   sopimus_tyo.maara                                              AS maara,
   tyo.yksikkohinta                                               AS yksikkohinta,
   COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu IS NOT TRUE)
   JOIN yksikkohintainen_tyo tyo ON (tyo.tehtava = sopimus_tyo."toimenpidekoodi-id")
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid AND
   ktp.toimenpidekoodi = :tehtavaid AND
   ktp."kohde-id" = :kohdeid AND
   (ktp.pvm BETWEEN :alkupvm AND :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(SELECT
   ktp.pvm                                                                        AS pvm,
   tpk.nimi                                                                       AS tehtava,
   k.nimi                                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
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
     COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
   ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
    * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) )  AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
                                       ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
                                        * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) ) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_hinta hinnoiteltu_tyo -- join?
     ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu IS NOT TRUE)
   LEFT OUTER JOIN vv_materiaali sopimus_materiaali ON sopimus_materiaali.id = hinnoiteltu_tyo."materiaali-id"
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.urakka = :urakkaid AND
   ktp.toimenpidekoodi = :tehtavaid AND
   ktp."kohde-id" = :kohdeid AND
   (ktp.pvm BETWEEN :alkupvm AND :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi);

-- name: hae-kanavien-muutos-ja-lisatyot
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt aikavälin perusteella
-- Kaikki urakat, kohteet ja tehtävät
(SELECT
   ktp.pvm                                                        AS pvm,
   tpk.nimi                                                       AS tehtava,
   k.nimi                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
   hk.nimi                                                        AS huoltokohde,
   '- kaikki'                                                     AS hinta_ryhma,
   'sopimushintainen-tyo-tai-materiaali'                          AS hinnoittelu_ryhma,
   sopimus_tyo.id                                                 AS tyo_id,
   sopimus_tyo.maara                                              AS maara,
   tyo.yksikkohinta                                               AS yksikkohinta,
   COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
   ktp.urakka                                                     AS urakka,
   u.nimi                                                         AS urakan_nimi,
   ktp.id                                                         AS toimenpide_id,
   ktp.tyyppi                                                     AS toimenpidetyyppi,
   ktp.toimenpidekoodi                                            AS tehtava_id,
   ktp."kohde-id"                                                 AS kohde_id,
   ktp."kohteenosa-id"                                            AS kohteenosa_id,
   ktp.huoltokohde                                                AS huoltokohde_id,
   NULL                                                           AS materiaali_id
 FROM kan_toimenpide ktp
   JOIN urakka u ON (u.id = ktp.urakka)
   JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
   JOIN kan_huoltokohde hk ON (ktp.huoltokohde = hk.id)
   JOIN kan_kohde k ON (ktp."kohde-id" = k.id)
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu IS NOT TRUE)
   JOIN yksikkohintainen_tyo tyo ON (tyo.tehtava = sopimus_tyo."toimenpidekoodi-id")
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(SELECT
   ktp.pvm                                                                        AS pvm,
   tpk.nimi                                                                       AS tehtava,
   k.nimi                                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                                                    AS kohteenosa,
   hk.nimi                                                                        AS huoltokohde,
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
   COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
   ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
    * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) )  AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
                                       ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
                                        * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) ) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
   ktp.urakka                                                                     AS urakka,
   u.nimi                                                                         AS urakan_nimi,
   ktp.id                                                                         AS toimenpide_id,
   ktp.tyyppi                                                                     AS toimenpidetyyppi,
   ktp.toimenpidekoodi                                                            AS tehtava_id,
   ktp."kohde-id"                                                                 AS kohde_id,
   ktp."kohteenosa-id"                                                            AS kohteenosa_id,
   ktp.huoltokohde                                                                AS huoltokohde_id,
   hinnoiteltu_tyo."materiaali-id"                                                AS materiaali_id
 FROM kan_toimenpide ktp
   JOIN urakka u ON (u.id = ktp.urakka)
   JOIN toimenpidekoodi tpk ON (tpk.id = ktp.toimenpidekoodi)
   JOIN kan_huoltokohde hk ON (ktp.huoltokohde = hk.id)
   JOIN kan_kohde k ON (ktp."kohde-id" = k.id)
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_hinta hinnoiteltu_tyo -- join?
     ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu IS NOT TRUE)
   LEFT OUTER JOIN vv_materiaali sopimus_materiaali ON sopimus_materiaali.id = hinnoiteltu_tyo."materiaali-id"
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi);

-- name: hae-kanavien-tehtavakohtaiset-muutos-ja-lisatyot-raportille
-- Hakee sopimus- ja yksikköhintaiset muutos- ja lisätyöt aikavälin ja tehtävän perusteella
-- Kaikki urakat, kaikki kohteet
(SELECT
   ktp.pvm                                                        AS pvm,
   tpk.nimi                                                       AS tehtava,
   k.nimi                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                      AS kohteenosa,
   hk.nimi                                                        AS huoltokohde,
   ktp.lisatieto                                                  AS lisatieto,
   tpk.nimi                                                       AS otsikko,
   '-'                                                            AS hinta_ryhma,
   'sopimushintainen-tyo-tai-materiaali'                          AS hinnoittelu_ryhma,
   sopimus_tyo.id                                                 AS tyo_id,
   sopimus_tyo.maara                                              AS maara,
   tyo.yksikkohinta                                               AS yksikkohinta,
   COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(sopimus_tyo.maara, 0) * COALESCE(tyo.yksikkohinta, 0) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_tyo sopimus_tyo ON (sopimus_tyo.toimenpide = ktp.id AND sopimus_tyo.poistettu IS NOT TRUE)
   JOIN yksikkohintainen_tyo tyo ON (tyo.tehtava = sopimus_tyo."toimenpidekoodi-id")
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.toimenpidekoodi = :tehtavaid AND
   (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi)
UNION
(SELECT
   ktp.pvm                                                                        AS pvm,
   tpk.nimi                                                                       AS tehtava,
   k.nimi                                                                         AS kohde,
   ko.nimi || ' ' || ko.tyyppi                                                    AS kohteenosa,
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
   COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
   ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
    * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) )                  AS summa,
   COALESCE((SELECT korotus from
       laske_kuukauden_indeksikorotus (DATE_PART('year', ktp.pvm) ::INTEGER ,
                                       DATE_PART('month', ktp.pvm) ::INTEGER,
                                       (select indeksi from urakka where id = ktp.urakka),
                                       COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0) +
                                       ((COALESCE(hinnoiteltu_tyo.summa, 0) + COALESCE(hinnoiteltu_tyo.maara, 0) * COALESCE(hinnoiteltu_tyo.yksikkohinta, 0))
                                        * (COALESCE (hinnoiteltu_tyo.yleiskustannuslisa, 0) / 100) ) ::NUMERIC,
                                       (select indeksilaskennan_perusluku (ktp.urakka)))), 0) AS indeksi,
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
   LEFT OUTER JOIN kan_kohteenosa ko ON (ktp."kohteenosa-id" = ko.id)
   LEFT OUTER JOIN kan_hinta hinnoiteltu_tyo -- join?
     ON (hinnoiteltu_tyo.toimenpide = ktp.id AND hinnoiteltu_tyo.poistettu IS NOT TRUE)
   LEFT OUTER JOIN vv_materiaali sopimus_materiaali ON sopimus_materiaali.id = hinnoiteltu_tyo."materiaali-id"
 WHERE
   ktp.tyyppi = 'muutos-lisatyo' AND
   ktp.toimenpidekoodi = :tehtavaid AND
   (ktp.pvm >= :alkupvm AND ktp.pvm <= :loppupvm)
 ORDER BY ktp.urakka, ktp."kohde-id", ktp.toimenpideinstanssi, ktp.toimenpidekoodi);


-- name: hae-urakan-kohteet-mukaanlukien-poistetut
SELECT k.id as id, k.nimi as nimi
FROM kan_kohde k, kan_kohde_urakka u
WHERE k.id = u."kohde-id" AND u."urakka-id" = :urakkaid; -- Huom. palauttaa myös poistetut kohteet. Raportilla voidaan tarvita.

-- name: hae-kanavakohteen-nimi
SELECT nimi
FROM kan_kohde
WHERE id = :kohdeid;

--name: hae-kanavatoimenpiteen-nimi
SELECT nimi
FROM toimenpidekoodi
WHERE id = :tehtavaid;

--name: hae-urakan-nimi
SELECT nimi
FROM urakka
WHERE id = :urakkaid;
