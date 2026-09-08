-- HARJA-2638: Tuomo 3.8.2026 tarkentamat MHU-bonukset.
--
-- Tässä migraatiossa:
-- 1) päivitetään bonuslajien kanoniset nimet,
-- 2) muodostetaan ja tarkistetaan kohdeurakoiden yhteinen lista,
-- 3) lisätään liikennevahinkobonus MHU21-25-profiileihin, jos kohdeurakat löytyvät, ja
-- 4) rajataan bonus täsmälleen kymmeneen kohdeurakkaan.
--
-- Historiallisten muu-bonus-kirjausten muuttaminen ei kuulu tähän migraatioon,
-- vaan tehdään erillisessä tiketissä.
--
-- Enum-arvo lisätään erillisessä V1_1291__.sql-migraatiossa, koska uutta
-- PostgreSQL-enum-arvoa voidaan käyttää vasta kyseisen migraation commitin jälkeen.

-- 1. Päivitä masterdatan näyttönimet ja kuvaus.
-- IS DISTINCT FROM tekee päivityksestä uusinta-ajossa idempotentin eikä muuta
-- auditointikenttiä, jos masterdata on jo oikeassa muodossa.
WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
UPDATE bonus_laji bl
   SET nimi = 'Bonus alihankintasopimusten maksuehdoista',
       muokkaaja = i.id,
       muokattu = CURRENT_TIMESTAMP
  FROM integraatio i
 WHERE bl.koodi = 'alihankintabonus'
   AND bl.nimi IS DISTINCT FROM 'Bonus alihankintasopimusten maksuehdoista';

-- Liikennevahinkobonus on tässä migraatiossa käytössä MHU21-25-urakoissa.
-- MHU26-urakoiden liitokset lisätään erillisessä migraatiossa.
WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
UPDATE bonus_laji bl
   SET nimi = 'Bonus liikennevahinkojen aiheuttajien selvittämisestä',
     kuvaus = 'MHU21-25-urakoille urakkakohtaisesti rajattu bonus liikennevahinkojen aiheuttajien selvittämisestä',
       muokkaaja = i.id,
       muokattu = CURRENT_TIMESTAMP
  FROM integraatio i
 WHERE bl.koodi = 'liikennevahinkojen_aiheuttajien_selvitysbonus'
   AND (bl.nimi IS DISTINCT FROM 'Bonus liikennevahinkojen aiheuttajien selvittämisestä'
        OR bl.kuvaus IS DISTINCT FROM 'MHU21-26-urakoille urakkakohtaisesti rajattu bonus liikennevahinkojen aiheuttajien selvittämisestä');

-- 2. Muodosta yksi tilapäinen kohdeurakoiden lähde.
-- Sama lista käytetään sekä esikyselyssä että profiilirivien liitoksissa.
-- ON COMMIT DROP varmistaa, ettei migraatiosta jää pysyvää apurakennetta.
CREATE TEMPORARY TABLE harja_2638_bonusin_urakkarajaukset (
    profiili_nimi       TEXT NOT NULL,
    urakka_lyhyt_nimi   TEXT NOT NULL,
    urakka_alkupvm     DATE NOT NULL,
    urakkatyyppi        urakkatyyppi NOT NULL,
    PRIMARY KEY (profiili_nimi, urakka_lyhyt_nimi, urakka_alkupvm)
) ON COMMIT DROP;

INSERT INTO harja_2638_bonusin_urakkarajaukset (profiili_nimi,
                                                 urakka_lyhyt_nimi,
                                                 urakka_alkupvm,
                                                 urakkatyyppi)
VALUES
    -- MHU21-24
    ('teiden-hoito-bonus-2021-2024', 'Nummi 21', DATE '2021-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-2021-2024', 'Raasepori 21', DATE '2021-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-2021-2024', 'Heinola 22', DATE '2022-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-2021-2024', 'Lahti 22', DATE '2022-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-2021-2024', 'Hyvinkää 23', DATE '2023-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-2021-2024', 'Hämeenlinna 23', DATE '2023-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-2021-2024', 'Espoo 24', DATE '2024-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-2021-2024', 'Vantaa 24', DATE '2024-10-01', 'teiden-hoito'),
    -- MHU25
    ('teiden-hoito-bonus-mhu2025', 'Mäntsälä 25', DATE '2025-10-01', 'teiden-hoito'),
    ('teiden-hoito-bonus-mhu2025', 'Porvoo 25', DATE '2025-10-01', 'teiden-hoito');

-- 3. Varmista kohdeurakat ennen profiili- tai liitosmuutoksia.
-- Urakka tunnistetaan lyhyen nimen, alkupäivän ja tyypin yhdistelmällä.
-- Tyhjässä skeemassa urakkadata ladataan vasta migraatioiden jälkeen.
-- Puuttuva tai useaan urakkaan osuva tunniste ohittaa konfiguraation tässä ympäristössä.
DO $$
DECLARE
  puuttuvat TEXT;
  moniosumaiset TEXT;
BEGIN
  IF EXISTS (SELECT 1 FROM urakka) THEN
    -- Laske jokaiselle profiilin ja urakan yhdistelmälle osumien määrä:
    -- nolla = puuttuva urakka, yli yksi = epäyksikäsitteinen urakka.
    WITH osumat AS (
      SELECT r.profiili_nimi,
             r.urakka_lyhyt_nimi,
             r.urakka_alkupvm,
             COUNT(u.id) AS osumien_maara
        FROM harja_2638_bonusin_urakkarajaukset r
             LEFT JOIN urakka u
                       ON u.lyhyt_nimi = r.urakka_lyhyt_nimi
                      AND u.alkupvm = r.urakka_alkupvm
                      AND u.tyyppi = r.urakkatyyppi
       GROUP BY r.profiili_nimi, r.urakka_lyhyt_nimi, r.urakka_alkupvm
    )
    SELECT string_agg(format('%s (%s)', urakka_lyhyt_nimi, urakka_alkupvm), ', ')
      FILTER (WHERE osumien_maara = 0),
           string_agg(format('%s (%s), osumia %s', urakka_lyhyt_nimi, urakka_alkupvm, osumien_maara), ', ')
             FILTER (WHERE osumien_maara > 1)
      INTO puuttuvat, moniosumaiset
      FROM osumat;

    IF moniosumaiset IS NOT NULL OR puuttuvat IS NOT NULL THEN
      -- Profiilirivi ilman liitoksia tarkoittaa kaikkia profiilin urakoita.
      -- Siksi puutteellisessa ympäristössä koko konfiguraatio jätetään luomatta.
      RAISE NOTICE 'Urakkakohtainen liikennevahinkobonus ohitetaan. Puuttuvat: %, useita osumia: %',
        COALESCE(puuttuvat, '-'),
        COALESCE(moniosumaiset, '-');
    END IF;
  END IF;
END;
$$;

-- 4. Lisää liikennevahinkobonus MHU21-25-profiileihin.
-- Rivi koskee hoidon johdon T2-koodia 23150. MHU26-rivi on jo olemassa
-- aiemmassa migraatiossa ja käsitellään erillisessä migraatiossa.
WITH profiilirivit (profiili_nimi,
                    bonus_koodi,
                    toimenpiderajauksen_tyyppi,
                    toimenpide_t2_koodi,
                    jarjestys) AS (
    VALUES
        -- MHU21-24: uusi rivi nykyisten bonusrivien jälkeen.
        ('teiden-hoito-bonus-2021-2024',
         'liikennevahinkojen_aiheuttajien_selvitysbonus',
         't2-koodi',
         '23150',
         3),
        -- MHU25: uusi rivi asiakastyytyväisyysbonuksen jälkeen.
        ('teiden-hoito-bonus-mhu2025',
         'liikennevahinkojen_aiheuttajien_selvitysbonus',
         't2-koodi',
         '23150',
         2)
),
      kohteet_ok AS (
          SELECT 1 AS ok
         FROM (
          SELECT r.profiili_nimi,
              r.urakka_lyhyt_nimi,
              r.urakka_alkupvm,
              COUNT(u.id) AS osumien_maara
            FROM harja_2638_bonusin_urakkarajaukset r
              LEFT JOIN urakka u
                  ON u.lyhyt_nimi = r.urakka_lyhyt_nimi
                 AND u.alkupvm = r.urakka_alkupvm
                 AND u.tyyppi = r.urakkatyyppi
           GROUP BY r.profiili_nimi, r.urakka_lyhyt_nimi, r.urakka_alkupvm
         ) osumat
        HAVING COUNT(*) = COUNT(*) FILTER (WHERE osumien_maara = 1)
      ),
integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO bonus_profiili_rivi (bonus_profiili_id,
                                 bonus_laji_id,
                                 toimenpiderajauksen_tyyppi,
                                 toimenpide_t2_koodi,
                                 jarjestys,
                                 aktiivinen,
                                 luoja,
                                 luotu,
                                 muokkaaja,
                                 muokattu)
SELECT bp.id,
       bl.id,
       pr.toimenpiderajauksen_tyyppi,
       pr.toimenpide_t2_koodi,
       pr.jarjestys,
       TRUE,
       i.id,
       CURRENT_TIMESTAMP,
       i.id,
       CURRENT_TIMESTAMP
  FROM profiilirivit pr
       JOIN bonus_profiili bp
         ON bp.nimi = pr.profiili_nimi
       JOIN bonus_laji bl
         ON bl.koodi = pr.bonus_koodi
       CROSS JOIN integraatio i
       CROSS JOIN kohteet_ok
 WHERE NOT EXISTS (
           SELECT 1
             FROM bonus_profiili_rivi bpr
            WHERE bpr.bonus_profiili_id = bp.id
              AND bpr.bonus_laji_id = bl.id
              AND bpr.toimenpiderajauksen_tyyppi = pr.toimenpiderajauksen_tyyppi
              AND bpr.toimenpide_t2_koodi IS NOT DISTINCT FROM pr.toimenpide_t2_koodi
       );

-- 5. Luo profiilirivien urakkakohtaiset sallittujen urakoiden liitokset.
-- Liitos haetaan samasta lähteestä kuin esikyselyn urakat.
-- Kaikki rivit käyttävät samaa liikennevahinkobonusta ja T2-koodia 23150.
-- NOT EXISTS tekee uusinta-ajosta turvallisen.
WITH integraatio AS (
    SELECT id
      FROM kayttaja
  WHERE kayttajanimi = 'Integraatio'
),
kohteet_ok AS (
    SELECT 1 AS ok
   FROM (
    SELECT r.profiili_nimi,
        r.urakka_lyhyt_nimi,
        r.urakka_alkupvm,
        COUNT(u.id) AS osumien_maara
      FROM harja_2638_bonusin_urakkarajaukset r
        LEFT JOIN urakka u
            ON u.lyhyt_nimi = r.urakka_lyhyt_nimi
           AND u.alkupvm = r.urakka_alkupvm
           AND u.tyyppi = r.urakkatyyppi
     GROUP BY r.profiili_nimi, r.urakka_lyhyt_nimi, r.urakka_alkupvm
   ) osumat
  HAVING COUNT(*) = COUNT(*) FILTER (WHERE osumien_maara = 1)
)
INSERT INTO bonus_profiili_rivi_urakka (bonus_profiili_rivi_id,
                                        urakka_id,
                                        luoja,
                                        luotu,
                                        muokkaaja,
                                        muokattu)
SELECT bpr.id,
       u.id,
       i.id,
       CURRENT_TIMESTAMP,
       i.id,
       CURRENT_TIMESTAMP
  FROM harja_2638_bonusin_urakkarajaukset ur
       JOIN bonus_profiili bp
         ON bp.nimi = ur.profiili_nimi
       JOIN bonus_laji bl
         ON bl.koodi = 'liikennevahinkojen_aiheuttajien_selvitysbonus'
       JOIN bonus_profiili_rivi bpr
         ON bpr.bonus_profiili_id = bp.id
        AND bpr.bonus_laji_id = bl.id
        AND bpr.toimenpiderajauksen_tyyppi = 't2-koodi'
        AND bpr.toimenpide_t2_koodi = '23150'
       JOIN urakka u
         ON u.lyhyt_nimi = ur.urakka_lyhyt_nimi
        AND u.tyyppi = ur.urakkatyyppi
        AND u.alkupvm = ur.urakka_alkupvm
       CROSS JOIN integraatio i
       CROSS JOIN kohteet_ok
 WHERE NOT EXISTS (
           SELECT 1
             FROM bonus_profiili_rivi_urakka olemassa
            WHERE olemassa.bonus_profiili_rivi_id = bpr.id
              AND olemassa.urakka_id = u.id
       );
