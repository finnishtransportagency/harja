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

      WITH tiedot AS (
          SELECT u.id AS urakka,
           tpi.id AS toimenpideinstanssi,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio') AS kayttaja
         FROM urakka u
           JOIN toimenpideinstanssi tpi
             ON tpi.urakka = u.id
            AND tpi.nimi = u.nimi || ' MHU ja HJU Hoidon johto'
        WHERE u.nimi = 'Sodankylän MHU 2026-2031'
      )
      INSERT INTO sanktio (sakkoryhma, maara, perintapvm, maarattypvm, indeksi,
               toimenpideinstanssi, tyyppi, suorasanktio, luoja)
      SELECT 'A'::SANKTIOLAJI,
          1800,
          DATE '2026-10-15',
          DATE '2026-10-15',
          NULL,
          toimenpideinstanssi,
          (SELECT id FROM sanktiotyyppi WHERE koodi = 18),
          TRUE,
          kayttaja
        FROM tiedot;

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
        WHERE u.nimi = 'Sodankylän MHU 2026-2031'
      )
      INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi,
                  pvm, laskutuskuukausi, rahasumma, indeksin_nimi,
                  lisatieto, luotu, luoja)
      SELECT bonus.tyyppi::erilliskustannustyyppi,
          tiedot.sopimus,
          tiedot.urakka,
          tiedot.toimenpideinstanssi,
          DATE '2026-10-15',
          DATE '2026-10-15',
          bonus.summa,
          NULL,
          'Sanktioraportin Sodankylän MHU2026-testibonus',
          CURRENT_TIMESTAMP,
          tiedot.kayttaja
        FROM tiedot
          CROSS JOIN (VALUES
           ('asiakastyytyvaisyysbonus', 1100),
           ('alihankkijatyytyvaisyyskyselybonus', 1200),
           ('maaraaikaan_tehtavien_toiden_aiempi_toteutusbonus', 1300),
           ('liikennevahinkojen_aiheuttajien_selvitysbonus', 1400)
          ) AS bonus(tyyppi, summa);

-- Profiilissa voi olla sama bonuslaji sekä kaikille toimenpiteille että t2-koodille rajattuna.
-- Yksi toteutunut bonus saa liittyä tällöin vain kerran.
INSERT INTO bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id,
                                   toimenpiderajauksen_tyyppi, toimenpide_t2_koodi,
                                   jarjestys, aktiivinen, luoja, luotu, muokkaaja, muokattu)
SELECT bp.id,
         bl.id,
         'kaikki',
         NULL,
         99,
         TRUE,
         (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
         CURRENT_TIMESTAMP,
         (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
         CURRENT_TIMESTAMP
  FROM bonus_profiili bp
         JOIN bonus_laji bl
           ON bl.koodi = 'asiakastyytyvaisyysbonus'
 WHERE bp.nimi = 'teiden-hoito-bonus-mhu2026'
   AND NOT EXISTS (
           SELECT 1
             FROM bonus_profiili_rivi bpr
            WHERE bpr.bonus_profiili_id = bp.id
              AND bpr.bonus_laji_id = bl.id
              AND bpr.toimenpiderajauksen_tyyppi = 'kaikki');

-- Rajaa MHU2026-liikennevahinkobonus vain positiiviseen testikohteeseen.
INSERT INTO bonus_profiili_rivi_urakka (bonus_profiili_rivi_id, urakka_id,
                                        luoja, luotu, muokkaaja, muokattu)
SELECT bpr.id,
       u.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
       CURRENT_TIMESTAMP,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
       CURRENT_TIMESTAMP
  FROM bonus_profiili bp
       JOIN bonus_laji bl
         ON bl.koodi = 'liikennevahinkojen_aiheuttajien_selvitysbonus'
       JOIN bonus_profiili_rivi bpr
         ON bpr.bonus_profiili_id = bp.id
        AND bpr.bonus_laji_id = bl.id
       JOIN urakka u
         ON u.lyhyt_nimi = 'Nummi 26'
 WHERE bp.nimi = 'teiden-hoito-bonus-mhu2026';

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
      WHERE u.nimi = 'Nummi 26 - liikennevahinkobonuksen kohdistus'
    )
    INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi,
                pvm, laskutuskuukausi, rahasumma, indeksin_nimi,
                lisatieto, luotu, luoja)
    SELECT 'liikennevahinkojen_aiheuttajien_selvitysbonus'::erilliskustannustyyppi,
        sopimus,
        urakka,
        toimenpideinstanssi,
        DATE '2026-10-15',
        DATE '2026-10-15',
        1400,
        NULL,
        'Sanktioraportin Nummi 26 liikennevahinkobonuksen kohdistustesti',
        CURRENT_TIMESTAMP,
        kayttaja
      FROM tiedot;
