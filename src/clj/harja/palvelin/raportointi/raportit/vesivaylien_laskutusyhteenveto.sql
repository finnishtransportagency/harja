-- name: hae-yksikkohintaisten-toimenpiteiden-hintaryhmat
-- Hakee kaikki hinnoittelut, jotka ovat hintaryhmiä, ja muodostaa summan hintaryhmän hinnoista sekä
-- hintaryhmään kuuluvien toimenpiteiden omista hinnoitteluista.
-- HUOM! Ei sisällä hintoja, joiden toimenpiteet eivät kuulu mihinkään hintaryhmään.
-- Tällaiset haetaan eri kyselyssä, ks. hae-yksikkohintaisten-toimenpiteiden-omat-hinnoittelut-ilman-hintaryhmaa.
SELECT
  hintaryhma.id,
  hintaryhma.nimi                   AS "hinnoittelu",
  -- Hae hintaryhmään kuuluvien ei-poistettujen toimenpiteiden kaikki hinnoittelut, eli
  -- hintaryhmän oma hinnoittelu ja toimenpiteiden omat hinnoittelut. Summaa kaikki yhteen.
  (SELECT COALESCE(SUM(CASE
                       WHEN summa IS NOT NULL
                         THEN (summa + (summa * (yleiskustannuslisa / 100)))
                           ELSE ((yksikkohinta * maara) + ((yksikkohinta * maara) * (yleiskustannuslisa / 100)))
                         END), 0)
   FROM vv_hinta
   WHERE "hinnoittelu-id" IN
         -- Hinta kuuluu toimenpiteeseen, joka kuuluu ko. hintaryhmään
         (SELECT "hinnoittelu-id"
          FROM vv_hinnoittelu_toimenpide
          WHERE "toimenpide-id" IN
                -- Hae hintaryhmän kaikki ei-poistetut toimenpiteet
                (SELECT id
                 FROM reimari_toimenpide
                 WHERE id IN (SELECT "toimenpide-id"
                              FROM vv_hinnoittelu_toimenpide
                              WHERE "hinnoittelu-id" = hintaryhma.id
                                    AND poistettu IS NOT TRUE)
                       AND poistettu IS NOT TRUE)
                AND poistettu IS NOT TRUE
         AND "hinnoittelu-id" IN
             (SELECT id FROM vv_hyvaksytyt_hinnoittelut WHERE "laskutus-pvm" BETWEEN :alkupvm AND :loppupvm))
         AND poistettu IS NOT TRUE)
  -- Hae hintaryhmään kuuluvien ei-poistettujen toimenpiteiden kaikki työt. Summaa kaikki yhteen.
  +
  (SELECT COALESCE(SUM(tyo.maara * yht.yksikkohinta), 0)
   FROM vv_tyo tyo
     JOIN tehtava tpk ON tyo."toimenpidekoodi-id" = tpk.id
     JOIN yksikkohintainen_tyo yht ON tpk.id = yht.tehtava
                                      -- Suunnitteluaika osuu annetun aikavälin sisälle
                                      AND yht.alkupvm <= :alkupvm
                                      AND yht.loppupvm >= :loppupvm
                                      AND yht.urakka = :urakkaid
   WHERE "hinnoittelu-id" IN
         -- Työ kuuluu toimenpiteeseen, joka kuuluu ko. hintaryhmään
         (SELECT "hinnoittelu-id"
          FROM vv_hinnoittelu_toimenpide
          WHERE "toimenpide-id" IN
                -- Hae hintaryhmän kaikki ei-poistetut toimenpiteet
                (SELECT id
                 FROM reimari_toimenpide
                 WHERE id IN (SELECT "toimenpide-id"
                              FROM vv_hinnoittelu_toimenpide
                              WHERE "hinnoittelu-id" = hintaryhma.id
                                    AND poistettu IS NOT TRUE)
                       AND poistettu IS NOT TRUE)
                AND poistettu IS NOT TRUE)
         AND "hinnoittelu-id" IN
             (SELECT id FROM vv_hyvaksytyt_hinnoittelut WHERE "laskutus-pvm" BETWEEN :alkupvm AND :loppupvm)
         AND tyo.poistettu IS NOT TRUE)
    AS "summa",
  -- Hinnoitteluryhmät sisältävät useita reimari-toimenpiteitä
  -- jotka voivat potentiaalisesti liittyä eri väyliin / väylätyyppeihin
  -- Listataan hinnoitteluun liittyvät väylätyypit taulukossa.
  (SELECT ARRAY(SELECT DISTINCT (tyyppi)
                FROM vv_vayla
                WHERE vaylanro IN
                      (SELECT "vaylanro"
                       FROM reimari_toimenpide
                       WHERE id IN
                             (SELECT "toimenpide-id"
                              FROM vv_hinnoittelu_toimenpide
                              WHERE "hinnoittelu-id" = hintaryhma.id
                                    AND poistettu IS NOT TRUE))))
                                    AS vaylatyyppi
FROM vv_hinnoittelu hintaryhma
WHERE "urakka-id" = :urakkaid
      AND hintaryhma.poistettu IS NOT TRUE
      AND hintaryhma IS TRUE
      -- Hinnoittelulle on kirjattu toimenpiteitä valitulla aikavälillä
      AND EXISTS(SELECT id
                 FROM reimari_toimenpide
                 WHERE hintatyyppi = 'yksikkohintainen'
                       AND poistettu IS NOT TRUE
                       AND id IN (SELECT "toimenpide-id"
                                  FROM vv_hinnoittelu_toimenpide
                                  WHERE "hinnoittelu-id" = hintaryhma.id
                                        AND poistettu IS NOT TRUE));

-- name: hae-yksikkohintaisten-toimenpiteiden-omat-hinnoittelut-ilman-hintaryhmaa
-- Hakee toimenpiteiden omat hinnoittelut ja laskee niiden hintojen summan.
-- Palauttaa vain ne hinnoittelut, joihin kuuluva toimenpide ei kuulu mihinkään hintaryhmään, sillä
-- tällaiset tapaukset summataan jo hintaryhmien hintaan (ks. kysely: hae-yksikkohintaisten-toimenpiteiden-hintaryhmat)
SELECT
  oma_hinnoittelu.nimi                              AS "hinnoittelu",
  (SELECT COALESCE(SUM(CASE
                       WHEN summa IS NOT NULL
                         THEN (summa + (summa * (yleiskustannuslisa / 100)))
                       ELSE ((yksikkohinta * maara) + ((yksikkohinta * maara) * (yleiskustannuslisa / 100)))
                       END), 0)
   FROM vv_hinta
   WHERE "hinnoittelu-id" = oma_hinnoittelu.id
         AND poistettu IS NOT TRUE
         AND "hinnoittelu-id" IN
             (SELECT id FROM vv_hyvaksytyt_hinnoittelut WHERE "laskutus-pvm" BETWEEN :alkupvm AND :loppupvm))
    +
  (SELECT COALESCE(SUM(tyo.maara * yht.yksikkohinta), 0)
   FROM vv_tyo tyo
     JOIN tehtava tpk ON tyo."toimenpidekoodi-id" = tpk.id
     JOIN yksikkohintainen_tyo yht ON tpk.id = yht.tehtava
                                      -- Suunnitteluaika osuu annetun aikavälin sisälle
                                      AND yht.alkupvm <= :alkupvm
                                      AND yht.loppupvm >= :loppupvm
   WHERE "hinnoittelu-id" = oma_hinnoittelu.id
         AND tyo.poistettu IS NOT TRUE
         AND "hinnoittelu-id" IN
             (SELECT id FROM vv_hyvaksytyt_hinnoittelut WHERE "laskutus-pvm" BETWEEN :alkupvm AND :loppupvm))
    AS "summa",
  (SELECT tyyppi
   FROM vv_vayla
   WHERE vaylanro =
         (SELECT "vaylanro"
          FROM reimari_toimenpide
          WHERE id =
                (SELECT "toimenpide-id"
                 FROM vv_hinnoittelu_toimenpide
                 WHERE "hinnoittelu-id" = oma_hinnoittelu.id
                       AND poistettu IS NOT TRUE))) AS vaylatyyppi
FROM vv_hinnoittelu oma_hinnoittelu
WHERE "urakka-id" = :urakkaid
      AND poistettu IS NOT TRUE
      AND hintaryhma IS FALSE
      -- Tarkista, ettei hinnoittelun toimenpide kuulu mihinkään hintaryhmään
      AND (SELECT id
           FROM vv_hinnoittelu
           WHERE id IN
                 -- Hae toimenpiteen hinnoittelujen id:t ja valitse vain ne, joita ei ole poistettu
                 -- ja jotka ovat hintaryhmiä (pitäisi olla yksi)
                 (SELECT "hinnoittelu-id"
                  FROM vv_hinnoittelu_toimenpide
                  WHERE "toimenpide-id" =
                        -- Hae hinnoittelun toimenpide, joka ei ole poistettu
                        -- Kyseessä toimenpiteen oma hinnoittelu, eli löytyy vain yksi
                        (SELECT id
                         FROM reimari_toimenpide
                         WHERE id = (SELECT "toimenpide-id"
                                     FROM vv_hinnoittelu_toimenpide
                                     WHERE "hinnoittelu-id" = oma_hinnoittelu.id)
                               AND poistettu IS NOT TRUE)
                        AND poistettu IS NOT TRUE)
                 AND hintaryhma IS TRUE
                 AND poistettu IS NOT TRUE) IS NULL
      -- Hinnoittelulle on kirjattu toimenpiteitä valitulla aikavälillä
      AND EXISTS(SELECT id
                 FROM reimari_toimenpide
                 WHERE poistettu IS NOT TRUE
                       AND hintatyyppi = 'yksikkohintainen'
                       AND id IN (SELECT "toimenpide-id"
                                  FROM vv_hinnoittelu_toimenpide
                                  WHERE "hinnoittelu-id" = oma_hinnoittelu.id
                                        AND poistettu IS NOT TRUE));

-- name: hae-kokonaishintaiset-toimenpiteet
SELECT
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo kt
     LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
     LEFT JOIN toimenpideinstanssi_vesivaylat tpi_vv ON kt.toimenpideinstanssi = tpi_vv."toimenpideinstanssi-id"
   WHERE tpi.urakka = :urakkaid
         AND tpi_vv.vaylatyyppi = :vaylatyyppi :: VV_VAYLATYYPPI
         -- Kok. hint. suunnittelu osuu aikavälille jos eka päivä osuu (välin tulisi aina olla kuukausiväli)
         AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') >= :alkupvm
         AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') <= :loppupvm) AS "suunniteltu-maara",
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo kt
     LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
     LEFT JOIN toimenpideinstanssi_vesivaylat tpi_vv ON kt.toimenpideinstanssi = tpi_vv."toimenpideinstanssi-id"
   WHERE tpi.urakka = :urakkaid
         AND tpi_vv.vaylatyyppi = :vaylatyyppi :: VV_VAYLATYYPPI
         -- Työ on toteutunut, jos sen maksupvm on aikavälillä
         AND maksupvm >= :alkupvm
         AND maksupvm <= :loppupvm)                                                         AS "toteutunut-maara";

-- name: hae-sanktiot
SELECT COALESCE(SUM(maara), 0) AS summa
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
WHERE s.poistettu IS NOT TRUE
      AND s.perintapvm <= :loppupvm
      AND s.perintapvm >= :alkupvm
      AND tpi.urakka = :urakkaid;

-- name: hae-erilliskustannukset
SELECT COALESCE(SUM(rahasumma), 0) AS summa
FROM erilliskustannus ek
  LEFT JOIN toimenpideinstanssi tpi ON ek.toimenpideinstanssi = tpi.id
WHERE ek.poistettu IS NOT TRUE
      AND ek.pvm <= :loppupvm
      AND ek.pvm >= :alkupvm
      AND tpi.urakka = :urakkaid;
