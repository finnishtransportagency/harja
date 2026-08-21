-- Sanktioraportin minimidata POP MHU Kajaani 2025-2030 -urakalle.
WITH tiedot AS (
    SELECT u.id AS urakka,
           s.id AS sopimus,
           tpi.id AS toimenpideinstanssi,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio') AS kayttaja
      FROM urakka u
           JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
           JOIN toimenpideinstanssi tpi
             ON tpi.urakka = u.id
            AND tpi.nimi = u.nimi || ' MHU ja HJU Hoidon johto'
     WHERE u.nimi = 'POP MHU Kajaani 2025-2030'
)
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, maarattypvm, indeksi,
                     toimenpideinstanssi, tyyppi, suorasanktio, luoja)
SELECT 'A'::SANKTIOLAJI,
       1000,
       DATE '2026-01-15',
       DATE '2026-01-15',
       NULL,
       toimenpideinstanssi,
       (SELECT id FROM sanktiotyyppi WHERE koodi = 13),
       TRUE,
       kayttaja
  FROM tiedot;

WITH tiedot AS (
    SELECT tpi.id AS toimenpideinstanssi,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio') AS kayttaja
      FROM urakka u
           JOIN toimenpideinstanssi tpi
             ON tpi.urakka = u.id
            AND tpi.nimi = u.nimi || ' MHU ja HJU Hoidon johto'
     WHERE u.nimi = 'POP MHU Kajaani 2025-2030'
)
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, maarattypvm, indeksi,
                     toimenpideinstanssi, tyyppi, suorasanktio, luoja)
SELECT 'arvonvahennyssanktio'::SANKTIOLAJI,
       2500,
       DATE '2026-01-15',
       DATE '2026-01-15',
       NULL,
       toimenpideinstanssi,
       (SELECT id FROM sanktiotyyppi WHERE koodi = 0),
       TRUE,
       kayttaja
  FROM tiedot;

INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi,
                              pvm, laskutuskuukausi, rahasumma, indeksin_nimi,
                              lisatieto, luotu, luoja)
SELECT 'asiakastyytyvaisyysbonus',
       s.id,
       u.id,
       tpi.id,
       DATE '2026-01-15',
       DATE '2026-01-15',
       1500,
       NULL,
       'Sanktioraportin minimibonus',
       CURRENT_TIMESTAMP,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM urakka u
      JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
       JOIN toimenpideinstanssi tpi
         ON tpi.urakka = u.id
        AND tpi.nimi = u.nimi || ' MHU ja HJU Hoidon johto'
 WHERE u.nimi = 'POP MHU Kajaani 2025-2030';

WITH tiedot AS (
    SELECT u.id AS urakka,
           s.id AS sopimus,
           tpi.id AS toimenpideinstanssi,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio') AS kayttaja
      FROM urakka u
           JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
           JOIN toimenpideinstanssi tpi
             ON tpi.urakka = u.id
            AND tpi.nimi = u.nimi || ' MHU ja HJU Hoidon johto'
     WHERE u.nimi = 'POP MHU Suomussalmi 2024-2029'
)
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, maarattypvm, indeksi,
                     toimenpideinstanssi, tyyppi, suorasanktio, luoja)
SELECT 'A'::SANKTIOLAJI,
       1200,
       DATE '2026-01-15',
       DATE '2026-01-15',
       NULL,
       toimenpideinstanssi,
       (SELECT id FROM sanktiotyyppi WHERE koodi = 13),
       TRUE,
       kayttaja
  FROM tiedot;

WITH tiedot AS (
    SELECT tpi.id AS toimenpideinstanssi,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio') AS kayttaja
      FROM urakka u
           JOIN toimenpideinstanssi tpi
             ON tpi.urakka = u.id
            AND tpi.nimi = u.nimi || ' MHU ja HJU Hoidon johto'
     WHERE u.nimi = 'POP MHU Suomussalmi 2024-2029'
)
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, maarattypvm, indeksi,
                     toimenpideinstanssi, tyyppi, suorasanktio, luoja)
SELECT 'arvonvahennyssanktio'::SANKTIOLAJI,
       2800,
       DATE '2026-01-15',
       DATE '2026-01-15',
       NULL,
       toimenpideinstanssi,
       (SELECT id FROM sanktiotyyppi WHERE koodi = 0),
       TRUE,
       kayttaja
  FROM tiedot;

INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi,
                              pvm, laskutuskuukausi, rahasumma, indeksin_nimi,
                              lisatieto, luotu, luoja)
SELECT 'asiakastyytyvaisyysbonus',
       s.id,
       u.id,
       tpi.id,
       DATE '2026-01-15',
       DATE '2026-01-15',
       1700,
       NULL,
       'Sanktioraportin Suomussalmen testibonus',
       CURRENT_TIMESTAMP,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM urakka u
       JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
       JOIN toimenpideinstanssi tpi
         ON tpi.urakka = u.id
        AND tpi.nimi = u.nimi || ' MHU ja HJU Hoidon johto'
 WHERE u.nimi = 'POP MHU Suomussalmi 2024-2029';
