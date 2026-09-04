-- Päivitetään voi_puolittaa_omailmoituksella = TRUE Mhu26
-- A- ja B-ryhmän sanktio-profiiliriveille kaikissa A/B-sanktiotyypeissä,
-- sekä urakka- että laatupoikkeama-kontekstissa.
UPDATE sanktio_profiili_rivi spr
SET voi_puolittaa_omailmoituksella = TRUE,
    muokkaaja = i.id,
    muokattu = CURRENT_TIMESTAMP
FROM sanktio_profiili sp,
     sanktio_laji sl,
     kayttaja i
WHERE i.kayttajanimi = 'Integraatio'
  AND sp.nimi = 'teiden-hoito-mhu2026'
  AND spr.sanktio_profiili_id = sp.id
  AND spr.sanktio_laji_id = sl.id
  AND sl.koodi IN ('A', 'B')
  AND spr.soveltuvuuskonteksti IN ('urakka', 'laatupoikkeama');

WITH integraatio AS (

    SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'Integraatio'
),
     summamaaritykset (profiili_nimi,
                       laji_koodi,
                       sanktiotyyppi_koodi,
                       soveltuvuuskonteksti,
                       maaritystapa,
                       summa_euroina,
                       ohjeteksti,
                       jarjestys) AS (
         VALUES
             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2025 / A-ryhma / automaattiset summat (urakka ja lattupoikkeama)
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2025', 'A', 13, 'urakka',         'automaattinen',  4000.00::numeric, NULL::text, 1),
             ('teiden-hoito-mhu2025', 'A', 14, 'urakka',         'automaattinen',  3000.00,          NULL,       1),
             ('teiden-hoito-mhu2025', 'A', 17, 'urakka',         'automaattinen',  2000.00,          NULL,       1),
             ('teiden-hoito-mhu2025', 'A', 13, 'laatupoikkeama', 'automaattinen',  4000.00,          NULL,       1),
             ('teiden-hoito-mhu2025', 'A', 14, 'laatupoikkeama', 'automaattinen',  3000.00,          NULL,       1),
             ('teiden-hoito-mhu2025', 'A', 17, 'laatupoikkeama', 'automaattinen',  2000.00,          NULL,       1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2025 / B-ryhmä / automaattiset summat (urakka ja laatupoikkeama)
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2025', 'B', 13, 'urakka',         'automaattinen',  8000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'B', 14, 'urakka',         'automaattinen',  6000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'B', 17, 'urakka',         'automaattinen',  4000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'B', 13, 'laatupoikkeama', 'automaattinen',  8000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'B', 14, 'laatupoikkeama', 'automaattinen',  6000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'B', 17, 'laatupoikkeama', 'automaattinen',  4000.00, NULL, 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2025 / C-ryhmä / automaattiset summat (urakka ja laatupoikkeama)
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2025', 'C', 10, 'urakka',         'automaattinen',  1000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'C', 12, 'urakka',         'automaattinen', 10000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'C', 11, 'urakka',         'automaattinen',  4000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'C', 10, 'laatupoikkeama', 'automaattinen',  1000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'C', 12, 'laatupoikkeama', 'automaattinen', 10000.00, NULL, 1),
             ('teiden-hoito-mhu2025', 'C', 11, 'laatupoikkeama', 'automaattinen',  4000.00, NULL, 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2025 / C-ryhmä / manuaaliset kirjaukset ohjetekstillä (urakka ja laatupoikkeama)
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2025', 'C',  8, 'urakka',         'automaattinen',  2000.00,
              'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2025', 'C',  9, 'urakka',         'manuaalinen',   NULL,
              'Tilaajalla tehtäväkohtaisen sanktion perimisen lisäksi oikeus teettaa työ ulkopuolisella '
                  'ja periä ko. kustannukset urakoitsijalta kaksinkertaisena. '
                  'Jos tekemätön työ on sen luonteinen, ettei sitä viivästymisen vuoksi voida enää tehdä, '
                  'tilaajalla on tehtäväkohtaisen sanktion perimisen lisäksi oikeus periä tekemätöntä työtä '
                  'vastaava kustannusosuus urakoitsijalta kaksinkertaisena.', 1),
             ('teiden-hoito-mhu2025', 'C',  8, 'laatupoikkeama', 'automaattinen',  2000.00,
              'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2025', 'C',  9, 'laatupoikkeama', 'manuaalinen',   NULL,
              'Tilaajalla tehtäväkohtaisen sanktion perimisen lisäksi oikeus teettaa työ ulkopuolisella '
                  'ja periä ko. kustannukset urakoitsijalta kaksinkertaisena. '
                  'Jos tekemätön työ on sen luonteinen, ettei sitä viivästymisen vuoksi voida enää tehdä, '
                  'tilaajalla on tehtäväkohtaisen sanktion perimisen lisäksi oikeus periä tekemätöntä työtä '
                  'vastaava kustannusosuus urakoitsijalta kaksinkertaisena.', 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2025 / standalone-lajit / manuaalinen kirjaus ohjetekstillä / vain urakka-konteksti
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2025', 'pohjavesisuolan_ylitys',     7, 'urakka', 'manuaalinen', NULL,
              '1 000 euroa / ylitystonni / pohjavesialueen ajorata-km '
                  '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
             ('teiden-hoito-mhu2025', 'talvisuolan_ylitys',         7, 'urakka', 'manuaalinen', NULL,
              '5 % ylittävältä osalta sanktio: toteutunut suolan keskihinta x 1,1 '
                  '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
             ('teiden-hoito-mhu2025', 'laskutus_yli_laskutusrajan', 0, 'urakka', 'manuaalinen', NULL,
              '20 % summasta, joka on laskutettu yli', 1),
             ('teiden-hoito-mhu2025', 'tenttikeskiarvo-sanktio',    0, 'urakka', 'manuaalinen', NULL,
              'Asiakirjan taulukon mukainen sanktio jokaiselta alkavalta kuukaudelta, '
                  'kun pistekeskiarvo alittuu tarjouksessa annetusta pistekeskiarvosta.', 1),
             ('teiden-hoito-mhu2025', 'vaihtosanktio',              0, 'urakka', 'manuaalinen', NULL,
              '1 % tarjouksen mukaisesta tavoitehinnasta', 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2026 / A-ryhma / automaattiset summat (urakka ja lattupoikkeama)
             -- Huom: A+18+lattupoikkeama on jo seedattu V1_1259:ssa (jarjestys 1 ja 2).
             --       ON CONFLICT DO NOTHING suojaa olemassaolevan datan.
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2026', 'A', 18, 'urakka',         'automaattinen',  6000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 19, 'urakka',         'automaattinen',  5000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 20, 'urakka',         'automaattinen',  4000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 17, 'urakka',         'automaattinen',  3000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 21, 'urakka',         'automaattinen',  2000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 18, 'laatupoikkeama', 'automaattinen',  6000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 19, 'laatupoikkeama', 'automaattinen',  5000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 20, 'laatupoikkeama', 'automaattinen',  4000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 17, 'laatupoikkeama', 'automaattinen',  3000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'A', 21, 'laatupoikkeama', 'automaattinen',  2000.00, NULL, 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2026 / B-ryhma / automaattiset summat (urakka ja lattupoikkeama)
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2026', 'B', 18, 'urakka',         'automaattinen', 14000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 19, 'urakka',         'automaattinen', 11000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 20, 'urakka',         'automaattinen',  9000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 17, 'urakka',         'automaattinen',  6000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 21, 'urakka',         'automaattinen',  4000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 18, 'laatupoikkeama', 'automaattinen', 14000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 19, 'laatupoikkeama', 'automaattinen', 11000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 20, 'laatupoikkeama', 'automaattinen',  9000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 17, 'laatupoikkeama', 'automaattinen',  6000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'B', 21, 'laatupoikkeama', 'automaattinen',  4000.00, NULL, 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2026 / standalone-lajit / automaattiset summat / vain urakka-konteksti
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2026', 'asiakirjamerkintojen_paikkansa_pitamattomyys', 0, 'urakka',
              'automaattinen', 20000.00, NULL, 1),
             ('teiden-hoito-mhu2026', 'muu_sopimuksen_vastainen_toiminta', 0, 'urakka',
              'automaattinen', 6000.00,  NULL, 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2026 / C-ryhma / automaattiset summat ohjetekstilla (urakka ja lattupoikkeama)
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2026', 'C', 18, 'urakka',         'automaattinen', 4000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 19, 'urakka',         'automaattinen', 3000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 20, 'urakka',         'automaattinen', 2000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 17, 'urakka',         'automaattinen', 2000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 21, 'urakka',         'automaattinen', 1000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 18, 'laatupoikkeama', 'automaattinen', 4000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 19, 'laatupoikkeama', 'automaattinen', 3000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 20, 'laatupoikkeama', 'automaattinen', 2000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 17, 'laatupoikkeama', 'automaattinen', 2000.00, 'alkavalta viikolta', 1),
             ('teiden-hoito-mhu2026', 'C', 21, 'laatupoikkeama', 'automaattinen', 1000.00, 'alkavalta viikolta', 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2026 / tyon_tekematta_jattaminen / manuaalinen kirjaus / vain urakka-konteksti
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2026', 'tyon_tekematta_jattaminen', 22, 'urakka', 'manuaalinen', NULL,
              'Käyttäjä kirjaa tiekm-määrän, Harja laskee sanktion. 200,00 € / tiekm.', 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2026 / muut standalone-lajit / manuaalinen kirjaus ohjetekstilla / vain urakka-konteksti
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2026', 'pohjavesisuolan_ylitys',             0, 'urakka', 'manuaalinen', NULL,
              '1 000 euroa / ylitystonni / pohjavesialueen ajorata-km '
                  '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
             ('teiden-hoito-mhu2026', 'talvisuolan_kokonaiskayton_ylitys',  0, 'urakka', 'manuaalinen', NULL,
              '5 % ylittävältä osalta sanktio: toteutunut suolan keskihinta x 1,1 '
                  '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
             ('teiden-hoito-mhu2026', 'laskutus_yli_laskutusrajan',         0, 'urakka', 'manuaalinen', NULL,
              'Sanktio on 20 % summasta, joka on laskutettu yli.', 1),
             ('teiden-hoito-mhu2026', 'laskutus_ilman_laskutuskelpoisuutta', 0, 'urakka', 'manuaalinen', NULL,
              'Sanktio on 20 % laskun summasta, joka ei ollut laskutuskelpoinen.', 1),
             ('teiden-hoito-mhu2026', 'vastuuhenkilon_tenttipistemaara_alentuminen', 0, 'urakka',
              'manuaalinen', NULL,
              'Asiakirjan taulukon mukainen sanktio jokaiselta alkavalta kuukaudelta, '
                  'kun pistekeskiarvo alittuu tarjouksessa annetusta pistekeskiarvosta.', 1),
             ('teiden-hoito-mhu2026', 'vastuuhenkilon_vaihto', 0, 'urakka', 'manuaalinen', NULL,
              '1 % tarjouksen mukaisesta tavoitehinnasta.', 1),

             -- -----------------------------------------------------------------------
             -- teiden-hoito-mhu2026 / bonukset / vain urakka-konteksti
             -- Huom: maaraaikaan_tehtavien_toiden_aiempi_toteutusbonus, asiakastyytyvaisyysbonus ja
             --       liikennevahinkojen_aiheuttajien_selvitysbonus kirjataan normaalikirjauksina ilman
             --       summamaaritys-rivia - niilla ei ole vakiosummaa eika ohjetekstia.
             -- -----------------------------------------------------------------------
             ('teiden-hoito-mhu2026', 'alihankkijatyytyvaisyyskyselybonus', 0, 'urakka',
              'automaattinen', 5000.00, NULL, 1)
     )
INSERT INTO sanktio_profiili_rivi_summamaaritys (sanktio_profiili_rivi_id,
                                                 maaritystapa,
                                                 summa_euroina,
                                                 ohjeteksti,
                                                 jarjestys,
                                                 luoja,
                                                 luotu,
                                                 muokkaaja,
                                                 muokattu)
SELECT spr.id,
       sm.maaritystapa,
       sm.summa_euroina,
       sm.ohjeteksti,
       sm.jarjestys,
       integraatio.id,
       CURRENT_TIMESTAMP,
       integraatio.id,
       CURRENT_TIMESTAMP
FROM summamaaritykset sm
         JOIN sanktio_profiili sp
              ON sp.nimi = sm.profiili_nimi
         JOIN sanktio_laji sl
              ON sl.koodi = sm.laji_koodi
         JOIN sanktiotyyppi st
              ON st.koodi = sm.sanktiotyyppi_koodi
         JOIN sanktio_profiili_rivi spr
              ON spr.sanktio_profiili_id    = sp.id
                  AND spr.sanktio_laji_id        = sl.id
                  AND spr.sanktiotyyppi_id       = st.id
                  AND spr.soveltuvuuskonteksti   = sm.soveltuvuuskonteksti
         CROSS JOIN integraatio
ON CONFLICT (sanktio_profiili_rivi_id, jarjestys) DO NOTHING;

-- Lisätään bonusprofiilirivin euromääritykselle oma taulu.
CREATE TABLE bonus_profiili_rivi_summamaaritys (
    id                     SERIAL PRIMARY KEY,
    bonus_profiili_rivi_id INTEGER NOT NULL REFERENCES bonus_profiili_rivi (id),
    maaritystapa           TEXT NOT NULL DEFAULT 'automaattinen',
    summa_euroina          NUMERIC(12,2),
    ohjeteksti             TEXT,
    jarjestys              INTEGER NOT NULL DEFAULT 1,
    luoja                  INTEGER NOT NULL REFERENCES kayttaja (id),
    luotu                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja              INTEGER NOT NULL REFERENCES kayttaja (id),
    muokattu               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bonus_profiili_rivi_id, jarjestys),
    CHECK (summa_euroina IS NULL OR summa_euroina >= 0),
    CHECK (maaritystapa IN ('automaattinen', 'manuaalinen')),
    CHECK (ohjeteksti IS NULL OR btrim(ohjeteksti) <> ''),
    CHECK (
        (maaritystapa = 'automaattinen' AND summa_euroina IS NOT NULL)
            OR
        (maaritystapa = 'manuaalinen' AND (summa_euroina IS NOT NULL OR ohjeteksti IS NOT NULL))
    )
);

CREATE INDEX bonus_profiili_rivi_summamaaritys_haku_idx
    ON bonus_profiili_rivi_summamaaritys (bonus_profiili_rivi_id, jarjestys);

COMMENT ON TABLE bonus_profiili_rivi_summamaaritys
    IS 'Bonusprofiiliriville kiinnitettävä euromäärä ja/tai ohjeteksti.';

-- V1_1291 yritti seedata tämän bonuksen sanktioiden määritystauluun.
-- Poistetaan mahdollinen väärä rivi täsmällisellä kohdistuksella.
DELETE FROM sanktio_profiili_rivi_summamaaritys sm
 USING sanktio_profiili_rivi spr,
       sanktio_profiili sp,
       sanktio_laji sl,
       sanktiotyyppi st
 WHERE sm.sanktio_profiili_rivi_id = spr.id
   AND spr.sanktio_profiili_id = sp.id
   AND spr.sanktio_laji_id = sl.id
   AND spr.sanktiotyyppi_id = st.id
   AND sp.nimi = 'teiden-hoito-mhu2026'
   AND sl.koodi = 'alihankkijatyytyvaisyyskyselybonus'
   AND st.koodi = 0
   AND spr.soveltuvuuskonteksti = 'urakka'
   AND sm.maaritystapa = 'automaattinen'
   AND sm.summa_euroina = 5000.00
   AND sm.jarjestys = 1;

-- Seedaataan alihankkijatyytyväisyyskyselybonus-bonuksen 5 000 euron määritys.
WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO bonus_profiili_rivi_summamaaritys (bonus_profiili_rivi_id,
                                               maaritystapa,
                                               summa_euroina,
                                               ohjeteksti,
                                               jarjestys,
                                               luoja,
                                               luotu,
                                               muokkaaja,
                                               muokattu)
SELECT bpr.id,
       'automaattinen',
       5000.00,
       NULL,
       1,
       integraatio.id,
       CURRENT_TIMESTAMP,
       integraatio.id,
       CURRENT_TIMESTAMP
  FROM bonus_profiili bp
       JOIN bonus_profiili_rivi bpr
         ON bpr.bonus_profiili_id = bp.id
       JOIN bonus_laji bl
         ON bl.id = bpr.bonus_laji_id
       CROSS JOIN integraatio
 WHERE bp.nimi = 'teiden-hoito-bonus-mhu2026'
   AND bpr.toimenpiderajauksen_tyyppi = 't2-koodi'
   AND bpr.toimenpide_t2_koodi = '23150'
   AND bl.koodi = 'alihankkijatyytyvaisyyskyselybonus'
ON CONFLICT (bonus_profiili_rivi_id, jarjestys) DO NOTHING;
