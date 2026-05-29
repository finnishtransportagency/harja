-- Vaihe 1: Lisataan MHU25 B-ryhmaan puuttuvat profiilirivit
--           (B + sanktiotyyppi 10/11/12 ei ole V1_1259-seedissa teiden-hoito-mhu2025-profiilille)
WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
),
uudet_profiilirivit (profiili_nimi, laji_koodi, sanktiotyyppi_koodi, soveltuvuuskonteksti, jarjestys) AS (
    VALUES ('teiden-hoito-mhu2025', 'B', 10, 'urakka',         4),
           ('teiden-hoito-mhu2025', 'B', 10, 'laatupoikkeama', 4),
           ('teiden-hoito-mhu2025', 'B', 12, 'urakka',         5),
           ('teiden-hoito-mhu2025', 'B', 12, 'laatupoikkeama', 5),
           ('teiden-hoito-mhu2025', 'B', 11, 'urakka',         6),
           ('teiden-hoito-mhu2025', 'B', 11, 'laatupoikkeama', 6)
)
INSERT INTO sanktio_profiili_rivi (sanktio_profiili_id, sanktio_laji_id, sanktiotyyppi_id, soveltuvuuskonteksti,
                                   jarjestys, aktiivinen, lisametatiedot, luoja, luotu, muokkaaja, muokattu)
SELECT sp.id,
       sl.id,
       st.id,
       pr.soveltuvuuskonteksti,
       pr.jarjestys,
       TRUE,
       NULL,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP
  FROM uudet_profiilirivit pr
       JOIN sanktio_profiili sp ON sp.nimi = pr.profiili_nimi
       JOIN sanktio_laji     sl ON sl.koodi = pr.laji_koodi
       JOIN sanktiotyyppi    st ON st.koodi = pr.sanktiotyyppi_koodi
    ON CONFLICT (sanktio_profiili_id, sanktio_laji_id, sanktiotyyppi_id, soveltuvuuskonteksti) DO NOTHING;


-- Vaihe 2: Lisataan summamaaritykset
--
-- Lahde: plans/HARJA-2470-sanktioiden-ja-bonusten-euromaarat/summamaaritysten-syottopohja.md
-- Profiilirivi-koodi- ja kontekstiparit johdettu V1_1259-seedista.
-- "urakka + laatupoikkeama" -merkinta = sama summamaaritys lisataan molempiin konteksteihin.
-- Rivit joilla vain "urakka" = soveltuvuuskonteksti on 'urakka', ei lattupoikkeama-riviä.
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
        -- teiden-hoito-mhu2025 / A-ryhma / kiinteat summat (urakka ja lattupoikkeama)
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2025', 'A', 13, 'urakka',         'kiintea_euromaara',  4000.00::numeric, NULL::text, 1),
        ('teiden-hoito-mhu2025', 'A', 14, 'urakka',         'kiintea_euromaara',  3000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'A', 17, 'urakka',         'kiintea_euromaara',  2000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'A', 13, 'laatupoikkeama', 'kiintea_euromaara',  4000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'A', 14, 'laatupoikkeama', 'kiintea_euromaara',  3000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'A', 17, 'laatupoikkeama', 'kiintea_euromaara',  2000.00,          NULL,       1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2025 / B-ryhma / kiinteat summat (urakka ja lattupoikkeama)
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2025', 'B', 13, 'urakka',         'kiintea_euromaara',  8000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 14, 'urakka',         'kiintea_euromaara',  6000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 17, 'urakka',         'kiintea_euromaara',  4000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 10, 'urakka',         'kiintea_euromaara',  1000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 12, 'urakka',         'kiintea_euromaara', 10000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 11, 'urakka',         'kiintea_euromaara',  4000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 13, 'laatupoikkeama', 'kiintea_euromaara',  8000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 14, 'laatupoikkeama', 'kiintea_euromaara',  6000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 17, 'laatupoikkeama', 'kiintea_euromaara',  4000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 10, 'laatupoikkeama', 'kiintea_euromaara',  1000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 12, 'laatupoikkeama', 'kiintea_euromaara', 10000.00,          NULL,       1),
        ('teiden-hoito-mhu2025', 'B', 11, 'laatupoikkeama', 'kiintea_euromaara',  4000.00,          NULL,       1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2025 / C-ryhma / ohjetekstit (urakka ja lattupoikkeama)
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2025', 'C',  8, 'urakka',         'kiintea_euromaara',  2000.00,
            'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2025', 'C',  9, 'urakka',         'vapaa_ohjeteksti',   NULL,
            'Tilaajalla tehtäväkohtaisen sanktion perimisen lisäksi oikeus teettaa työ ulkopuolisella '
            'ja periä ko. kustannukset urakoitsijalta kaksinkertaisena. '
            'Jos tekemätön työ on sen luonteinen, ettei sitä viivästymisen vuoksi voida enää tehdä, '
            'tilaajalla on tehtäväkohtaisen sanktion perimisen lisäksi oikeus periä tekemätöntä työtä '
            'vastaava kustannusosuus urakoitsijalta kaksinkertaisena.', 1),
        ('teiden-hoito-mhu2025', 'C',  8, 'laatupoikkeama', 'kiintea_euromaara',  2000.00,
            'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2025', 'C',  9, 'laatupoikkeama', 'vapaa_ohjeteksti',   NULL,
            'Tilaajalla tehtäväkohtaisen sanktion perimisen lisäksi oikeus teettaa työ ulkopuolisella '
            'ja periä ko. kustannukset urakoitsijalta kaksinkertaisena. '
            'Jos tekemätön työ on sen luonteinen, ettei sitä viivästymisen vuoksi voida enää tehdä, '
            'tilaajalla on tehtäväkohtaisen sanktion perimisen lisäksi oikeus periä tekemätöntä työtä '
            'vastaava kustannusosuus urakoitsijalta kaksinkertaisena.', 1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2025 / standalone-lajit / vain urakka-konteksti
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2025', 'pohjavesisuolan_ylitys',     7, 'urakka', 'vapaa_ohjeteksti', NULL,
            '1 000 euroa / ylitystonni / pohjavesialueen ajorata-km '
            '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
        ('teiden-hoito-mhu2025', 'talvisuolan_ylitys',         7, 'urakka', 'vapaa_ohjeteksti', NULL,
            '5 % ylittävältä osalta sanktio: toteutunut suolan keskihinta x 1,1 '
            '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
        ('teiden-hoito-mhu2025', 'laskutus_yli_laskutusrajan', 0, 'urakka', 'vapaa_ohjeteksti', NULL,
            '20 % summasta, joka on laskutettu yli', 1),
        ('teiden-hoito-mhu2025', 'tenttikeskiarvo-sanktio',    0, 'urakka', 'vapaa_ohjeteksti', NULL,
            'Asiakirjan taulukon mukainen sanktio jokaiselta alkavalta kuukaudelta, '
            'kun pistekeskiarvo alittuu tarjouksessa annetusta pistekeskiarvosta.', 1),
        ('teiden-hoito-mhu2025', 'vaihtosanktio',              0, 'urakka', 'vapaa_ohjeteksti', NULL,
            '1 % tarjouksen mukaisesta tavoitehinnasta', 1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2026 / A-ryhma / kiinteat summat (urakka ja lattupoikkeama)
        -- Huom: A+18+lattupoikkeama on jo seedattu V1_1259:ssa (jarjestys 1 ja 2).
        --       ON CONFLICT DO NOTHING suojaa olemassaolevan datan.
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2026', 'A', 18, 'urakka',         'kiintea_euromaara',  6000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 19, 'urakka',         'kiintea_euromaara',  5000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 20, 'urakka',         'kiintea_euromaara',  4000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 17, 'urakka',         'kiintea_euromaara',  3000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 21, 'urakka',         'kiintea_euromaara',  2000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 18, 'laatupoikkeama', 'kiintea_euromaara',  6000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 19, 'laatupoikkeama', 'kiintea_euromaara',  5000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 20, 'laatupoikkeama', 'kiintea_euromaara',  4000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 17, 'laatupoikkeama', 'kiintea_euromaara',  3000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'A', 21, 'laatupoikkeama', 'kiintea_euromaara',  2000.00, NULL, 1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2026 / B-ryhma / kiinteat summat (urakka ja lattupoikkeama)
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2026', 'B', 18, 'urakka',         'kiintea_euromaara', 14000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 19, 'urakka',         'kiintea_euromaara', 11000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 20, 'urakka',         'kiintea_euromaara',  9000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 17, 'urakka',         'kiintea_euromaara',  6000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 21, 'urakka',         'kiintea_euromaara',  4000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 18, 'laatupoikkeama', 'kiintea_euromaara', 14000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 19, 'laatupoikkeama', 'kiintea_euromaara', 11000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 20, 'laatupoikkeama', 'kiintea_euromaara',  9000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 17, 'laatupoikkeama', 'kiintea_euromaara',  6000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'B', 21, 'laatupoikkeama', 'kiintea_euromaara',  4000.00, NULL, 1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2026 / standalone-lajit / vain urakka-konteksti
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2026', 'asiakirjamerkintojen_paikkansa_pitamattomyys', 0, 'urakka',
            'kiintea_euromaara', 20000.00, NULL, 1),
        ('teiden-hoito-mhu2026', 'muu_sopimuksen_vastainen_toiminta', 0, 'urakka',
            'kiintea_euromaara', 6000.00,  NULL, 1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2026 / C-ryhma / ohjetekstit (urakka ja lattupoikkeama)
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2026', 'C', 18, 'urakka',         'kiintea_euromaara', 4000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 19, 'urakka',         'kiintea_euromaara', 3000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 20, 'urakka',         'kiintea_euromaara', 2000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 17, 'urakka',         'kiintea_euromaara', 2000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 21, 'urakka',         'kiintea_euromaara', 1000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 18, 'laatupoikkeama', 'kiintea_euromaara', 4000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 19, 'laatupoikkeama', 'kiintea_euromaara', 3000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 20, 'laatupoikkeama', 'kiintea_euromaara', 2000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 17, 'laatupoikkeama', 'kiintea_euromaara', 2000.00, 'alkavalta viikolta', 1),
        ('teiden-hoito-mhu2026', 'C', 21, 'laatupoikkeama', 'kiintea_euromaara', 1000.00, 'alkavalta viikolta', 1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2026 / tyon_tekematta_jattaminen / vain urakka-konteksti
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2026', 'tyon_tekematta_jattaminen', 22, 'urakka', 'vapaa_ohjeteksti', NULL,
            'Käyttäjä kirjaa tiekm-määrän, Harja laskee sanktion. 200,00 € / tiekm.', 1),
        ('teiden-hoito-mhu2026', 'tyon_tekematta_jattaminen', 23, 'urakka', 'vapaa_ohjeteksti', NULL,
            'Kirjataan käsin.', 1),

        -- -----------------------------------------------------------------------
        -- teiden-hoito-mhu2026 / muut standalone-lajit / vain urakka-konteksti
        -- -----------------------------------------------------------------------
        ('teiden-hoito-mhu2026', 'pohjavesisuolan_ylitys',             0, 'urakka', 'vapaa_ohjeteksti', NULL,
            '1 000 euroa / ylitystonni / pohjavesialueen ajorata-km '
            '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
        ('teiden-hoito-mhu2026', 'talvisuolan_kokonaiskayton_ylitys',  0, 'urakka', 'vapaa_ohjeteksti', NULL,
            '5 % ylittävältä osalta sanktio: toteutunut suolan keskihinta x 1,1 '
            '(kohtuullistaminen mahdollista poikkeuksellisen lämpimänä talvena)', 1),
        ('teiden-hoito-mhu2026', 'laskutus_yli_laskutusrajan',         0, 'urakka', 'vapaa_ohjeteksti', NULL,
            'Sanktio on 20 % summasta, joka on laskutettu yli.', 1),
        ('teiden-hoito-mhu2026', 'laskutus_ilman_laskutuskelpoisuutta', 0, 'urakka', 'vapaa_ohjeteksti', NULL,
            'Sanktio on 20 % laskun summasta, joka ei ollut laskutuskelpoinen.', 1),
        ('teiden-hoito-mhu2026', 'vastuuhenkilon_tenttipistemaara_alentuminen', 0, 'urakka',
            'vapaa_ohjeteksti', NULL,
            'Asiakirjan taulukon mukainen sanktio jokaiselta alkavalta kuukaudelta, '
            'kun pistekeskiarvo alittuu tarjouksessa annetusta pistekeskiarvosta.', 1),
        ('teiden-hoito-mhu2026', 'vastuuhenkilon_vaihto', 0, 'urakka', 'vapaa_ohjeteksti', NULL,
            '1 % tarjouksen mukaisesta tavoitehinnasta.', 1)
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
