-- HARJA-2638: Tuomo 3.8.2026 tarkentamat MHU-bonukset.
--
-- Tässä migraatiossa:
-- 1) päivitetään bonuslajien kanoniset nimet,
-- 2) tarkistetaan kaikkien kohdeurakoiden yksikäsitteisyys,
-- 3) lisätään liikennevahinkobonus MHU21-26-profiileihin,
-- 4) rajataan bonus täsmälleen kahteentoista kohdeurakkaan.
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

-- Liikennevahinkobonus on käytössä MHU21-26-urakoissa. MHU19-20 jäävät
-- edelleen muu-bonus-lajin piiriin.
WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
UPDATE bonus_laji bl
   SET nimi = 'Bonus liikennevahinkojen aiheuttajien selvittämisestä',
       kuvaus = 'MHU21-26-urakoille urakkakohtaisesti rajattu bonus liikennevahinkojen aiheuttajien selvittämisestä',
       muokkaaja = i.id,
       muokattu = CURRENT_TIMESTAMP
  FROM integraatio i
 WHERE bl.koodi = 'liikennevahinkojen_aiheuttajien_selvitysbonus'
   AND (bl.nimi IS DISTINCT FROM 'Bonus liikennevahinkojen aiheuttajien selvittämisestä'
        OR bl.kuvaus IS DISTINCT FROM 'MHU21-26-urakoille urakkakohtaisesti rajattu bonus liikennevahinkojen aiheuttajien selvittämisestä');

-- 2. Varmista kohdeurakat ennen profiili- tai kirjauspäivityksiä.
-- Urakka tunnistetaan lyhyen nimen, alkupäivän ja urakkatyypin yhdistelmällä.
-- Puuttuva tai useaan urakkaan osuva tunniste keskeyttää koko migraation, jotta
-- osittainen tai väärään urakkaan kohdistuva sallittujen urakoiden rajaus
-- ei pääse tuotantoon.
DO $$
DECLARE
  puuttuvat TEXT;
  moniosumaiset TEXT;
BEGIN
  -- Lista on HARJA-2638:n mukainen kohdejoukko. Vuosiosa ei yksin riitä
  -- tunnisteeksi, koska samannimisiä urakoita voi olla eri ajanjaksoilla.
  WITH kohdeurakat (urakka_lyhyt_nimi, urakka_alkupvm) AS (
    VALUES
      ('Nummi 21', DATE '2021-10-01'),
      ('Raasepori 21', DATE '2021-10-01'),
      ('Heinola 22', DATE '2022-10-01'),
      ('Lahti 22', DATE '2022-10-01'),
      ('Hyvinkää 23', DATE '2023-10-01'),
      ('Hämeenlinna 23', DATE '2023-10-01'),
      ('Espoo 24', DATE '2024-10-01'),
      ('Vantaa 24', DATE '2024-10-01'),
      ('Mäntsälä 25', DATE '2025-10-01'),
      ('Porvoo 25', DATE '2025-10-01'),
      ('Nummi 26', DATE '2026-10-01'),
      ('Raasepori 26', DATE '2026-10-01')
  ),
  osumat AS (
    -- Laske jokaiselle odotetulle tunnisteelle osumien määrä:
    -- nolla = puuttuva urakka, yli yksi = epäyksikäsitteinen urakka.
    SELECT k.urakka_lyhyt_nimi,
           k.urakka_alkupvm,
           COUNT(u.id) AS osumien_maara
      FROM kohdeurakat k
           LEFT JOIN urakka u
                     ON u.lyhyt_nimi = k.urakka_lyhyt_nimi
                    AND u.alkupvm = k.urakka_alkupvm
                    AND u.tyyppi = 'teiden-hoito'
     GROUP BY k.urakka_lyhyt_nimi, k.urakka_alkupvm
  )
  SELECT string_agg(format('%s (%s)', urakka_lyhyt_nimi, urakka_alkupvm), ', ')
    FILTER (WHERE osumien_maara = 0),
         string_agg(format('%s (%s), osumia %s', urakka_lyhyt_nimi, urakka_alkupvm, osumien_maara), ', ')
           FILTER (WHERE osumien_maara > 1)
    INTO puuttuvat, moniosumaiset
    FROM osumat;

  IF moniosumaiset IS NOT NULL THEN
    -- Väärä rajaus olisi tietomallissa vaarallisempi kuin migraation
    -- pysähtyminen, joten moniosumaisuus käsitellään kovana virheenä.
    RAISE EXCEPTION 'Urakkakohtaisen liikennevahinkobonuksen kohteet eivät ole yksikäsitteisiä: %',
      moniosumaiset;
  END IF;

  IF puuttuvat IS NOT NULL THEN
    -- Profiilirivi ilman liitoksia tarkoittaa kaikkia profiilin urakoita.
    -- Siksi puuttuva kohdeurakka ei saa muuttua hiljaisesti avoimeksi rajaukseksi.
    RAISE EXCEPTION 'Urakkakohtaisen liikennevahinkobonuksen kohdeurakoita puuttuu: %',
      puuttuvat;
  END IF;
END;
$$;

-- 3. Lisää liikennevahinkobonus profiileihin.
-- Rivi koskee hoidon johdon T2-koodia 23150. MHU26-rivi on jo olemassa
-- aiemmassa migraatiossa, mutta sama idempotentti ehto varmistaa kaikkien
-- kolmen profiilin tavoitetilan.
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
         2),
        -- MHU26: olemassa olevan rivin tavoitejärjestys.
        ('teiden-hoito-bonus-mhu2026',
         'liikennevahinkojen_aiheuttajien_selvitysbonus',
         't2-koodi',
         '23150',
         4)
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
 WHERE NOT EXISTS (
           SELECT 1
             FROM bonus_profiili_rivi bpr
            WHERE bpr.bonus_profiili_id = bp.id
              AND bpr.bonus_laji_id = bl.id
              AND bpr.toimenpiderajauksen_tyyppi = pr.toimenpiderajauksen_tyyppi
              AND bpr.toimenpide_t2_koodi IS NOT DISTINCT FROM pr.toimenpide_t2_koodi
       );

-- 4. Luo profiilirivien urakkakohtaiset sallittujen urakoiden liitokset.
-- Jokainen VALUES-rivi vastaa yhtä HARJA-2638:ssa nimettyä urakkaa.
-- Liitos haetaan samalla nimellä, alkupäivällä ja tyypillä kuin esikyselyssä.
-- Kaikki rivit käyttävät samaa liikennevahinkobonusta ja T2-koodia 23150,
-- joten niitä ei toisteta VALUES-listassa. NOT EXISTS tekee uusinta-ajosta
-- turvallisen.
WITH urakkarajaukset (profiili_nimi,
                      urakka_alkupvm,
                      urakka_lyhyt_nimi) AS (
    VALUES
        -- MHU21-24
        ('teiden-hoito-bonus-2021-2024',
         DATE '2021-10-01',
         'Nummi 21'),
        ('teiden-hoito-bonus-2021-2024',
         DATE '2021-10-01',
         'Raasepori 21'),
        ('teiden-hoito-bonus-2021-2024',
         DATE '2022-10-01',
         'Heinola 22'),
        ('teiden-hoito-bonus-2021-2024',
         DATE '2022-10-01',
         'Lahti 22'),
        ('teiden-hoito-bonus-2021-2024',
         DATE '2023-10-01',
         'Hyvinkää 23'),
        ('teiden-hoito-bonus-2021-2024',
         DATE '2023-10-01',
         'Hämeenlinna 23'),
        ('teiden-hoito-bonus-2021-2024',
         DATE '2024-10-01',
         'Espoo 24'),
        ('teiden-hoito-bonus-2021-2024',
         DATE '2024-10-01',
         'Vantaa 24'),
        -- MHU25
        ('teiden-hoito-bonus-mhu2025',
         DATE '2025-10-01',
         'Mäntsälä 25'),
        ('teiden-hoito-bonus-mhu2025',
         DATE '2025-10-01',
         'Porvoo 25'),
        -- MHU26
        ('teiden-hoito-bonus-mhu2026',
         DATE '2026-10-01',
         'Nummi 26'),
        ('teiden-hoito-bonus-mhu2026',
         DATE '2026-10-01',
         'Raasepori 26')
),
integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
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
  FROM urakkarajaukset ur
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
        AND u.tyyppi = 'teiden-hoito'
        AND u.alkupvm = ur.urakka_alkupvm
       CROSS JOIN integraatio i
 WHERE NOT EXISTS (
           SELECT 1
             FROM bonus_profiili_rivi_urakka olemassa
            WHERE olemassa.bonus_profiili_rivi_id = bpr.id
              AND olemassa.urakka_id = u.id
       );
