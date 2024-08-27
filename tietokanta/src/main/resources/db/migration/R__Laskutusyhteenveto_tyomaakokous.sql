-- Työmaakokousta varten räätälöityyn laskutusyhteenvetoon liittyvät tietokantahaut on pakattu
-- tässä yhden funktion alle.

-- Siivotaan ensin vanhat pois, niin uudet voi toimia
DROP FUNCTION IF EXISTS ly_raportti_tyomaakokous(DATE, DATE, DATE, DATE, INTEGER);
DROP TYPE IF EXISTS LY_RAPORTTI_TYOMAAKOKOUS_TULOS;

-- Ensin määritellään TYPE, joka on ikäänkuin se objekti/rivi, jonka funktio palauttaa.
-- Tähän on sisällytetty kaikki yksittäiset tulokset, jotta koodiin ei jää enää yhtään laskutehtävää tehtäväksi.
CREATE TYPE LY_RAPORTTI_TYOMAAKOKOUS_TULOS AS
(
    talvihoito_hoitokausi_yht             NUMERIC,
    talvihoito_val_aika_yht               NUMERIC,
    lyh_hoitokausi_yht                    NUMERIC,
    lyh_val_aika_yht                      NUMERIC,
    sora_hoitokausi_yht                   NUMERIC,
    sora_val_aika_yht                     NUMERIC,
    paallyste_hoitokausi_yht              NUMERIC,
    paallyste_val_aika_yht                NUMERIC,
    yllapito_hoitokausi_yht               NUMERIC,
    yllapito_val_aika_yht                 NUMERIC,
    korvausinv_hoitokausi_yht             NUMERIC,
    korvausinv_val_aika_yht               NUMERIC,
    hankinnat_hoitokausi_yht              NUMERIC,
    hankinnat_val_aika_yht                NUMERIC,
    johtojahallinto_hoitokausi_yht        NUMERIC,
    johtojahallinto_val_aika_yht          NUMERIC,
    erillishankinnat_hoitokausi_yht       NUMERIC,
    erillishankinnat_val_aika_yht         NUMERIC,
    hjpalkkio_hoitokausi_yht              NUMERIC,
    hjpalkkio_val_aika_yht                NUMERIC,
    hoidonjohto_hoitokausi_yht            NUMERIC,
    hoidonjohto_val_aika_yht              NUMERIC,
    tavhin_hoitokausi_yht                 NUMERIC,
    tavhin_val_aika_yht                   NUMERIC,
    hoitokauden_tavoitehinta              NUMERIC,
    hk_tavhintsiirto_ed_vuodelta          NUMERIC,
    budjettia_jaljella                    NUMERIC,
    lisatyo_talvihoito_hoitokausi_yht     NUMERIC,
    lisatyo_talvihoito_val_aika_yht       NUMERIC,
    lisatyo_lyh_hoitokausi_yht            NUMERIC,
    lisatyo_lyh_val_aika_yht              NUMERIC,
    lisatyo_sora_hoitokausi_yht           NUMERIC,
    lisatyo_sora_val_aika_yht             NUMERIC,
    lisatyo_paallyste_hoitokausi_yht      NUMERIC,
    lisatyo_paallyste_val_aika_yht        NUMERIC,
    lisatyo_yllapito_hoitokausi_yht       NUMERIC,
    lisatyo_yllapito_val_aika_yht         NUMERIC,
    lisatyo_korvausinv_hoitokausi_yht     NUMERIC,
    lisatyo_korvausinv_val_aika_yht       NUMERIC,
    lisatyo_hoidonjohto_hoitokausi_yht    NUMERIC,
    lisatyo_hoidonjohto_val_aika_yht      NUMERIC,
    lisatyot_hoitokausi_yht               NUMERIC,
    lisatyot_val_aika_yht                 NUMERIC,
    bonukset_hoitokausi_yht               NUMERIC,
    bonukset_val_aika_yht                 NUMERIC,
    sanktiot_hoitokausi_yht               NUMERIC,
    sanktiot_val_aika_yht                 NUMERIC,
    paatos_tavoitepalkkio_hoitokausi_yht  NUMERIC,
    paatos_tavoitepalkkio_val_aika_yht    NUMERIC,
    paatos_tavoiteh_ylitys_hoitokausi_yht NUMERIC,
    paatos_tavoiteh_ylitys_val_aika_yht   NUMERIC,
    paatos_kattoh_ylitys_hoitokausi_yht   NUMERIC,
    paatos_kattoh_ylitys_val_aika_yht     NUMERIC,
    muut_kustannukset_hoitokausi_yht      NUMERIC,
    muut_kustannukset_val_aika_yht        NUMERIC,
    yhteensa_kaikki_hoitokausi_yht        NUMERIC,
    yhteensa_kaikki_val_aika_yht          NUMERIC,
    perusluku                             NUMERIC,

    -- Rahavaraukset 
    rahavaraus_nimet                      TEXT[],
    hoitokausi_yht_array                  NUMERIC[],
    val_aika_yht_array                    NUMERIC[],
    kaikki_rahavaraukset_hoitokausi_yht   NUMERIC,
    kaikki_rahavaraukset_val_yht          NUMERIC,

    -- Muut kulut, tavoitehintaan vaikuttavat
    muut_kulut_hoitokausi                 NUMERIC,
    muut_kulut_val_aika                   NUMERIC,
    muut_kulut_hoitokausi_yht             NUMERIC,
    muut_kulut_val_aika_yht               NUMERIC,

    -- Ei tavoitehintaan vaikuttavat muut kulut 
    muut_kulut_ei_tavoite_hoitokausi      NUMERIC,
    muut_kulut_ei_tavoite_val_aika        NUMERIC,
    muut_kulut_ei_tavoite_hoitokausi_yht  NUMERIC,
    muut_kulut_ei_tavoite_val_aika_yht    NUMERIC

);

-- Tätä kutsummalla saadaan työmaakokouksen laskutusyhteenvetoon kaikki tarvittavat tiedot
CREATE OR REPLACE FUNCTION ly_raportti_tyomaakokous(hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE,
                                                    aikavali_loppupvm DATE, ur INTEGER)
    RETURNS SETOF LY_RAPORTTI_TYOMAAKOKOUS_TULOS
    LANGUAGE plpgsql AS
$$
DECLARE
    ---- Tavoitehintaan vaikuttavat toteutuneet kustannukset
    --- Hankinnat
    rivi                                  RECORD;

    -- Talvihoito
    talvihoito_rivi                       RECORD;
    talvihoito_tpi_id                     NUMERIC;
    talvihoito_hoitokausi_yht             NUMERIC;
    talvihoito_val_aika_yht               NUMERIC;

    -- Liikenneympäristön hoito
    lyh_tpi_id                            NUMERIC;
    lyh_hoitokausi_yht                    NUMERIC;
    lyh_val_aika_yht                      NUMERIC;
    lyh_rivi                              RECORD;

    -- Soratien hoito
    sora_tpi_id                           NUMERIC;
    sora_hoitokausi_yht                   NUMERIC;
    sora_val_aika_yht                     NUMERIC;
    sora_rivi                             RECORD;

    -- Päällystepaikkaukset
    paallyste_tpi_id                      NUMERIC;
    paallyste_hoitokausi_yht              NUMERIC;
    paallyste_val_aika_yht                NUMERIC;
    paallyste_rivi                        RECORD;

    -- MHU ylläpito
    yllapito_tpi_id                       NUMERIC;
    yllapito_hoitokausi_yht               NUMERIC;
    yllapito_val_aika_yht                 NUMERIC;
    yllapito_rivi                         RECORD;

    -- MHU korvausinvestointi
    korvausinv_tpi_id                     NUMERIC;
    korvausinv_hoitokausi_yht             NUMERIC;
    korvausinv_val_aika_yht               NUMERIC;
    korvausinv_rivi                       RECORD;

    -- Hankinnat yhteensä
    hankinnat_hoitokausi_yht              NUMERIC;
    hankinnat_val_aika_yht                NUMERIC;

    --- Hoidonjohto
    hoidonjohto_tpi_id                    NUMERIC;
    -- Johto ja hallintokorvaukset
    johtojahallinto_hoitokausi_yht        NUMERIC;
    johtojahallinto_val_aika_yht          NUMERIC;
    johtojahallinto_rivi                  RECORD;

    -- Erillishankinnat
    erillishankinnat_hoitokausi_yht       NUMERIC;
    erillishankinnat_val_aika_yht         NUMERIC;
    erillishankinnat_rivi                 RECORD;

    -- Hoidonjohtopalkkio
    hjpalkkio_hoitokausi_yht              NUMERIC;
    hjpalkkio_val_aika_yht                NUMERIC;
    hjpalkkio_rivi                        RECORD;

    -- Hoidonjohto yhteensä
    hoidonjohto_hoitokausi_yht            NUMERIC;
    hoidonjohto_val_aika_yht              NUMERIC;

    --- Äkilliset hoitotyöt ja vahinkojen korjaukset
    akilliset_ja_vahingot_rivi            RECORD;

    -- Rahavarausten ID:t
    akilliset_id                          INT;
    vahingot_id                           INT;
    kannustin_id                          INT;

    -- Tavoitehinnat yhteensä
    tavhin_hoitokausi_yht                 NUMERIC;
    tavhin_val_aika_yht                   NUMERIC;

    --- Lisätyöt
    -- Lisätyöt (talvihoito)
    lisatyo_talvihoito_rivi               RECORD;
    lisatyo_lyh_rivi                      RECORD;
    lisatyo_sora_rivi                     RECORD;
    lisatyo_paallyste_rivi                RECORD;
    lisatyo_yllapito_rivi                 RECORD;
    lisatyo_korvausinv_rivi               RECORD;
    lisatyo_hoidonjohto_rivi              RECORD;
    lisatyo_talvihoito_hoitokausi_yht     NUMERIC;
    lisatyo_talvihoito_val_aika_yht       NUMERIC;
    lisatyo_lyh_hoitokausi_yht            NUMERIC;
    lisatyo_lyh_val_aika_yht              NUMERIC;
    lisatyo_sora_hoitokausi_yht           NUMERIC;
    lisatyo_sora_val_aika_yht             NUMERIC;
    lisatyo_paallyste_hoitokausi_yht      NUMERIC;
    lisatyo_paallyste_val_aika_yht        NUMERIC;
    lisatyo_yllapito_hoitokausi_yht       NUMERIC;
    lisatyo_yllapito_val_aika_yht         NUMERIC;
    lisatyo_korvausinv_hoitokausi_yht     NUMERIC;
    lisatyo_korvausinv_val_aika_yht       NUMERIC;
    lisatyo_hoidonjohto_hoitokausi_yht    NUMERIC;
    lisatyo_hoidonjohto_val_aika_yht      NUMERIC;
    lisatyot_hoitokausi_yht               NUMERIC;
    lisatyot_val_aika_yht                 NUMERIC;

    --- Muut kustannukset
    bonukset_rivi                         RECORD;
    bonukset_hoitokausi_yht               NUMERIC;
    bonukset_val_aika_yht                 NUMERIC;
    sanktiot_rivi                         RECORD;
    sanktiot_hoitokausi_yht               NUMERIC;
    sanktiot_val_aika_yht                 NUMERIC;
    paatos_tavoitepalkkio_hoitokausi_yht  NUMERIC;
    paatos_tavoitepalkkio_val_aika_yht    NUMERIC;
    paatos_tavoiteh_ylitys_hoitokausi_yht NUMERIC;
    paatos_tavoiteh_ylitys_val_aika_yht   NUMERIC;
    paatos_kattoh_ylitys_hoitokausi_yht   NUMERIC;
    paatos_kattoh_ylitys_val_aika_yht     NUMERIC;
    paatos_rivi                           RECORD;
    muut_kustannukset_hoitokausi_yht      NUMERIC;
    muut_kustannukset_val_aika_yht        NUMERIC;
    yhteensa_kaikki_hoitokausi_yht        NUMERIC;
    yhteensa_kaikki_val_aika_yht          NUMERIC;

    -- Asetuksia
    hk_alkuvuosi                          NUMERIC;
    hk_alkukuukausi                       NUMERIC;
    perusluku                             NUMERIC; -- urakan indeksilaskennan perusluku (urakkasopimusta edeltävän vuoden syys-,loka, marraskuun keskiarvo)
    indeksi_vuosi                         INTEGER;
    indeksinimi                           VARCHAR; -- MAKU 2015
    sopimus_id                            INTEGER;
    hoitokauden_nro                       NUMERIC;
    hoitokauden_tavoitehinta              NUMERIC;
    hk_tavhintsiirto_ed_vuodelta          NUMERIC;
    budjettia_jaljella                    NUMERIC;
    urakan_tiedot                         RECORD;

    -- Rahavaraukset
    rahavaraus                            RECORD;
    rahavaraukset                         TEXT[];
    rahavaraus_nimet                      TEXT[]    := '{}';
    hoitokausi_yht_array                  NUMERIC[] := '{}';
    val_aika_yht_array                    NUMERIC[] := '{}';
    rv_val_aika_yht                       NUMERIC := 0;
    rv_hoitokausi_yht                     NUMERIC := 0;

    -- Lasketaan rahavaraukset yhteen ja lisätään ne tavoitehintaan 
    kaikki_rahavaraukset_val_yht          NUMERIC := 0.0;
    kaikki_rahavaraukset_hoitokausi_yht   NUMERIC := 0.0;

    -- Muut kulut, tavoitehintaan vaikuttavat
    muut_kulut_hoitokausi                 NUMERIC := 0.0;
    muut_kulut_val_aika                   NUMERIC := 0.0;
    muut_kulut_hoitokausi_yht             NUMERIC := 0.0;
    muut_kulut_val_aika_yht               NUMERIC := 0.0;

    -- Ei tavoitehintaan vaikuttavat muut kulut 
    muut_kulut_ei_tavoite_hoitokausi      NUMERIC := 0.0;
    muut_kulut_ei_tavoite_val_aika        NUMERIC := 0.0;
    muut_kulut_ei_tavoite_hoitokausi_yht  NUMERIC := 0.0;
    muut_kulut_ei_tavoite_val_aika_yht    NUMERIC := 0.0;

    -- Tulos 
    tulos                                 LY_RAPORTTI_TYOMAAKOKOUS_TULOS;

BEGIN

    perusluku := indeksilaskennan_perusluku(ur);
    hk_alkuvuosi := (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER);
    hk_alkukuukausi := (SELECT EXTRACT(MONTH FROM hk_alkupvm) :: INTEGER);
    indeksi_vuosi := hk_alkuvuosi; -- Joissakin indeksilaskennoissa voidaan käyttää hoitokauden edeltävää syyskuuta tai elokuuta indeksissä. TArkista tapauskohtaisesti
    indeksinimi := (SELECT indeksi FROM urakka u WHERE u.id = ur);
    sopimus_id := (SELECT id FROM sopimus WHERE urakka = ur AND paasopimus IS NULL);
    -- Haetaan urakan hoitokauden tavoitehinta
    select u.* from urakka u where u.id = ur into urakan_tiedot;
    RAISE NOTICE '*** Urakan tiedot: % ', urakan_tiedot;
    hoitokauden_nro := ((SELECT EXTRACT(YEAR from hk_alkupvm)) - (SELECT EXTRACT(YEAR from urakan_tiedot.alkupvm)) + 1);
    RAISE NOTICE '*** hoitokauden_nro: % ', hoitokauden_nro;
    -- Haetaan aina defaulttina indeksikorjattu luku, jos sitä ei ole, niin sitä ei tietenkään voi käyttää
    hoitokauden_tavoitehinta := (SELECT COALESCE(ut.tavoitehinta_indeksikorjattu, ut.tavoitehinta, 0) as tavoitehinta
                                 from urakka_tavoite ut
                                 where ut.hoitokausi = hoitokauden_nro
                                   and ut.urakka = ur);
    RAISE NOTICE '*** hoitokauden_tavoitehinta: % ', hoitokauden_tavoitehinta;
    hk_tavhintsiirto_ed_vuodelta := 0.0;
    hk_tavhintsiirto_ed_vuodelta := hk_tavhintsiirto_ed_vuodelta +
        (SELECT COALESCE(ut.tavoitehinta_siirretty_indeksikorjattu, ut.tavoitehinta_siirretty, 0) as siirretty
         from urakka_tavoite ut
         where ut.hoitokausi = hoitokauden_nro
           and ut.urakka = ur);
    RAISE NOTICE '*** hk_tavhintsiirto_ed_vuodelta: % ', hk_tavhintsiirto_ed_vuodelta;

    -- Kaikki kustannukset haetaan toimenpideinstanssien perusteella.
    -- Urakan toimenpideinstanssit saadaan, kun haetaan toimenpidekoodi taulusta oikealla koodilla olevat toimenpiteet (eli tason 3 asiat),
    -- jotka on linkitetty toimenpideinstanssiin
    -- Toimenpidekoodi taulun koodit ovat
    -- '23104' 'Talvihoito'
    -- '23116' 'Liikenneympäristön hoito'
    -- '23124' 'Sorateiden hoito'
    -- '20107' 'Päällystepaikkaukset'
    -- '20191' 'MHU Ylläpito'
    -- '14301' 'MHU Korvausinvestointi'
    -- '23151' 'Hoidon johto'

    -- Talvihoidon toimenpideinstanssin id
    select tpi.id
    from toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23104' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    into talvihoito_tpi_id;

    -- Liikenneymp. hoidon toimenpideinstanssin id
    select tpi.id
    from toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23116' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    into lyh_tpi_id;

    -- Sorateiden hoidon toimenpideinstanssin id
    select tpi.id
    from toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23124' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    into sora_tpi_id;

    -- Päällystepaikkaukset toimenpideinstanssin id
    select tpi.id
    from toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '20107' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    into paallyste_tpi_id;

    -- MHU ylläpidon toimenpideinstanssin id
    select tpi.id
    from toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '20191' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    into yllapito_tpi_id;

    -- Korvausinvestointien toimenpideinstanssin id
    select tpi.id
    from toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '14301' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    into korvausinv_tpi_id;

    select tpi.id
    from toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23151' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    into hoidonjohto_tpi_id;

    -- Alustetaan hankinta-arvoja
    talvihoito_hoitokausi_yht := 0.0;
    talvihoito_val_aika_yht := 0.0;
    talvihoito_hoitokausi_yht := 0.0;
    talvihoito_val_aika_yht := 0.0;
    lyh_hoitokausi_yht := 0.0;
    lyh_val_aika_yht := 0.0;
    sora_hoitokausi_yht := 0.0;
    sora_val_aika_yht := 0.0;
    paallyste_hoitokausi_yht := 0.0;
    paallyste_val_aika_yht := 0.0;
    yllapito_hoitokausi_yht := 0.0;
    yllapito_val_aika_yht := 0.0;
    korvausinv_hoitokausi_yht := 0.0;
    korvausinv_val_aika_yht := 0.0;

    -- Alustetaan lisätyöarvoja
    lisatyo_talvihoito_hoitokausi_yht := 0.0;
    lisatyo_talvihoito_val_aika_yht := 0.0;
    lisatyo_talvihoito_hoitokausi_yht := 0.0;
    lisatyo_talvihoito_val_aika_yht := 0.0;
    lisatyo_lyh_hoitokausi_yht := 0.0;
    lisatyo_lyh_val_aika_yht := 0.0;
    lisatyo_sora_hoitokausi_yht := 0.0;
    lisatyo_sora_val_aika_yht := 0.0;
    lisatyo_paallyste_hoitokausi_yht := 0.0;
    lisatyo_paallyste_val_aika_yht := 0.0;
    lisatyo_yllapito_hoitokausi_yht := 0.0;
    lisatyo_yllapito_val_aika_yht := 0.0;
    lisatyo_korvausinv_hoitokausi_yht := 0.0;
    lisatyo_korvausinv_val_aika_yht := 0.0;
    lisatyo_hoidonjohto_hoitokausi_yht := 0.0;
    lisatyo_hoidonjohto_val_aika_yht := 0.0;

    -- Rahavaraus kannustinjärjestelmä id, rahavaraus taulusta 
    -- Korvaa yksilöivän tunnisteen 0e78b556-74ee-437f-ac67-7a03381c64f6
    SELECT id INTO kannustin_id FROM rahavaraus WHERE nimi LIKE '%Kannustinjärjestelmä%' ORDER BY id ASC LIMIT 1;

    -- Hae rahavaraus id:t äkillisille hoitotöille ja vahingoille, uusi tietomalli korvaa vanhaa koodia jossa haetaan kulu_kohdistus maksuerätyypillä
    SELECT id INTO akilliset_id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO vahingot_id FROM rahavaraus WHERE nimi LIKE '%Vahinkojen korjaukset%' ORDER BY id ASC LIMIT 1;

    FOR rivi IN SELECT 
      summa         AS kht_summa, 
      l.erapaiva    AS erapaiva, 
      tpi.id        AS toimenpideinstanssi_id, 
      lk.maksueratyyppi, 
      lk.rahavaraus_id,
      tr.yksiloiva_tunniste
      FROM kulu l
        JOIN kulu_kohdistus lk ON lk.kulu = l.id
        JOIN toimenpideinstanssi tpi
            ON lk.toimenpideinstanssi = tpi.id 
           AND tpi.id IN (
               talvihoito_tpi_id, 
               lyh_tpi_id, 
               sora_tpi_id,
               paallyste_tpi_id, 
               yllapito_tpi_id,
               korvausinv_tpi_id, 
               hoidonjohto_tpi_id
            )
        LEFT JOIN tehtavaryhma tr ON lk.tehtavaryhma = tr.id
            -- Äkillisethoitotyöt ja vahingonkorvaukset niputetaan erikseen omiin laareihinsa, joten jätetään ne tässä pois
            WHERE ( lk.rahavaraus_id NOT IN (akilliset_id, vahingot_id) OR lk.rahavaraus_id IS NULL )
              AND lk.poistettu IS NOT TRUE
              AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
              -- Varmista että nämä ovat vain tavoitehintaisia kuluja

        LOOP

            RAISE NOTICE 'rivi: %', rivi;
            -- Kohdista talvihoitoon liittyvät rivit talvihoito_rivi:lle
            IF rivi.toimenpideinstanssi_id = talvihoito_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO talvihoito_rivi;

                RAISE NOTICE 'talvihoito_rivi: % ', talvihoito_rivi;
                RAISE NOTICE 'talvihoito_rivi.summa: %', talvihoito_rivi.summa;
            END IF;

            -- Kohdista talvihoitoon liittyvät lisätyö rivit lisatyo_talvihoito:lle
            IF rivi.toimenpideinstanssi_id = talvihoito_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_talvihoito_rivi;

                RAISE NOTICE 'lisatyo_talvihoito_rivi: % ', lisatyo_talvihoito_rivi;
                RAISE NOTICE 'lisatyo_talvihoito_rivi.summa: %', lisatyo_talvihoito_rivi.summa;
            END IF;

            -- Kohdista Liikenneympäristön hoitoon liittyvät rivit lyh_rivi:lle
            IF rivi.toimenpideinstanssi_id = lyh_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lyh_rivi;

                RAISE NOTICE 'lyh_rivi: % ', lyh_rivi;
                RAISE NOTICE 'lyh_rivi.summa: %', lyh_rivi.summa;
            END IF;

            -- Kohdista Liikenneympäristön hoitoon liittyvät lisätyörivit lisatyo_lyh_rivi:lle
            IF rivi.toimenpideinstanssi_id = lyh_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_lyh_rivi;

                RAISE NOTICE 'lisatyo_lyh_rivi: % ', lisatyo_lyh_rivi;
                RAISE NOTICE 'lisatyo_lyh_rivi.summa: %', lisatyo_lyh_rivi.summa;
            END IF;

            -- Kohdista Soratien hoitoon liittyvät rivit sora_rivi:lle
            IF rivi.toimenpideinstanssi_id = sora_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO sora_rivi;

                RAISE NOTICE 'sora_rivi: % ', sora_rivi;
                RAISE NOTICE 'sora_rivi.summa: %', sora_rivi.summa;
            END IF;

            -- Kohdista Soratien hoitoon liittyvät lisätyö rivit sora_rivi:lle
            IF rivi.toimenpideinstanssi_id = sora_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_sora_rivi;

                RAISE NOTICE 'lisatyo_sora_rivi: % ', lisatyo_sora_rivi;
                RAISE NOTICE 'lisatyo_sora_rivi.summa: %', lisatyo_sora_rivi.summa;
            END IF;

            -- Kohdista Päällysteiden paikkaukseen liittyvät rivit paallyste_rivi:lle
            IF rivi.toimenpideinstanssi_id = paallyste_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO paallyste_rivi;

                RAISE NOTICE 'paallyste_rivi: % ', paallyste_rivi;
                RAISE NOTICE 'paallyste_rivi.summa: %', paallyste_rivi.summa;
            END IF;

            -- Kohdista Päällysteiden paikkaukseen liittyvät lisätyö rivit paallyste_rivi:lle
            IF rivi.toimenpideinstanssi_id = paallyste_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_paallyste_rivi;

                RAISE NOTICE 'lisatyo_paallyste_rivi: % ', lisatyo_paallyste_rivi;
                RAISE NOTICE 'lisatyo_paallyste_rivi.summa: %', lisatyo_paallyste_rivi.summa;
            END IF;

            -- Kohdista MHU ylläpidon liittyvät rivit yllapito_rivi:lle
            -- Katso että rivi ei myöskään kuulu kannustinjärjestelmään (T3) (yksilöivätunniste='0e78b556-74ee-437f-ac67-7a03381c64f6') 
            -- Eli uudella rahavaraustietomallilla rahavaraus_id = kannustin_id
            IF rivi.toimenpideinstanssi_id = yllapito_tpi_id AND rivi.maksueratyyppi != 'lisatyo' AND
               (rivi.yksiloiva_tunniste IS NULL OR rivi.rahavaraus_id != kannustin_id) THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO yllapito_rivi;

                RAISE NOTICE 'yllapito_rivi: % ', yllapito_rivi;
                RAISE NOTICE 'yllapito_rivi.summa: %', yllapito_rivi.summa;
            END IF;

            -- Kohdista MHU ylläpidon liittyvät lisätyö rivit lisatyo_yllapito_rivi:lle
            -- Katso että ei kuulu kannustinjärjestelmään
            IF rivi.toimenpideinstanssi_id = yllapito_tpi_id AND rivi.maksueratyyppi = 'lisatyo' AND
               (rivi.yksiloiva_tunniste IS NULL OR rivi.rahavaraus_id != kannustin_id) THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_yllapito_rivi;

                RAISE NOTICE 'lisatyo_yllapito_rivi: % ', lisatyo_yllapito_rivi;
                RAISE NOTICE 'lisatyo_yllapito_rivi.summa: %', lisatyo_yllapito_rivi.summa;
            END IF;

            -- Kohdista MHU korvausinvestointeihin liittyvät rivit korvausinv_rivi:lle
            IF rivi.toimenpideinstanssi_id = korvausinv_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO korvausinv_rivi;

                RAISE NOTICE 'korvausinv_rivi: % ', korvausinv_rivi;
                RAISE NOTICE 'korvausinv_rivi.summa: %', korvausinv_rivi.summa;
            END IF;

            -- Kohdista MHU korvausinvestointeihin liittyvät lisätyö rivit lisatyo_korvausinv_rivi:lle
            IF rivi.toimenpideinstanssi_id = korvausinv_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_korvausinv_rivi;

                RAISE NOTICE 'lisatyo_korvausinv_rivi: % ', lisatyo_korvausinv_rivi;
                RAISE NOTICE 'lisatyo_korvausinv_rivi.summa: %', lisatyo_korvausinv_rivi.summa;
            END IF;

            -- Kohdista MHU Hoindojohto liittyvät lisätyö rivit lisatyo_hoidonjohto_rivi:lle
            IF rivi.toimenpideinstanssi_id = hoidonjohto_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_hoidonjohto_rivi;

                RAISE NOTICE 'lisatyo_hoidonjohto_rivi: % ', lisatyo_hoidonjohto_rivi;
                RAISE NOTICE 'lisatyo_hoidonjohto_rivi.summa: %', lisatyo_hoidonjohto_rivi.summa;
            END IF;

            RAISE NOTICE 'rivi.erapaiva: %', rivi.erapaiva;
            RAISE NOTICE 'aikavali_loppupvm: %', aikavali_loppupvm;

            IF rivi.erapaiva <= aikavali_loppupvm THEN

                -- Talvihoito Hoitokauden alusta
                IF rivi.toimenpideinstanssi_id = talvihoito_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN

                    talvihoito_hoitokausi_yht := talvihoito_hoitokausi_yht + COALESCE(talvihoito_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && talvihoito_tpi THEN: %', talvihoito_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        talvihoito_val_aika_yht := talvihoito_val_aika_yht + COALESCE(talvihoito_rivi.summa, 0.0);
                    END IF;
                END IF;

                IF rivi.toimenpideinstanssi_id = talvihoito_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN

                    lisatyo_talvihoito_hoitokausi_yht :=
                            lisatyo_talvihoito_hoitokausi_yht + COALESCE(lisatyo_talvihoito_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && talvihoito_tpi && lisatyo THEN: %', lisatyo_talvihoito_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lisatyo_talvihoito_val_aika_yht :=
                                lisatyo_talvihoito_val_aika_yht + COALESCE(lisatyo_talvihoito_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- Liikenneympäristön hoito Hoitokauden alusta
                IF rivi.toimenpideinstanssi_id = lyh_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN

                    lyh_hoitokausi_yht := lyh_hoitokausi_yht + COALESCE(lyh_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && lyh_tpi  THEN: %', lyh_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lyh_val_aika_yht := lyh_val_aika_yht + COALESCE(lyh_rivi.summa, 0.0);
                    END IF;
                END IF;

                IF rivi.toimenpideinstanssi_id = lyh_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN

                    lisatyo_lyh_hoitokausi_yht := lisatyo_lyh_hoitokausi_yht + COALESCE(lisatyo_lyh_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && lyh_tpi AND lisätyö THEN: %', lisatyo_lyh_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lisatyo_lyh_val_aika_yht := lisatyo_lyh_val_aika_yht + COALESCE(lisatyo_lyh_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- Soratien hoito Hoitokauden alusta
                IF rivi.toimenpideinstanssi_id = sora_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN

                    sora_hoitokausi_yht := sora_hoitokausi_yht + COALESCE(sora_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && sora_tpi  THEN: %', sora_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        sora_val_aika_yht := sora_val_aika_yht + COALESCE(sora_rivi.summa, 0.0);
                    END IF;
                END IF;

                IF rivi.toimenpideinstanssi_id = sora_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN

                    lisatyo_sora_hoitokausi_yht := lisatyo_sora_hoitokausi_yht + COALESCE(lisatyo_sora_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && sora_tpi AND lisätyö THEN: %', lisatyo_sora_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lisatyo_sora_val_aika_yht := lisatyo_sora_val_aika_yht + COALESCE(lisatyo_sora_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- Päällysteiden paikkaukset Hoitokauden alusta
                IF rivi.toimenpideinstanssi_id = paallyste_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN

                    paallyste_hoitokausi_yht := paallyste_hoitokausi_yht + COALESCE(paallyste_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && paallyste_tpi  THEN: %', paallyste_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        paallyste_val_aika_yht := paallyste_val_aika_yht + COALESCE(paallyste_rivi.summa, 0.0);
                    END IF;
                END IF;

                IF rivi.toimenpideinstanssi_id = paallyste_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN

                    lisatyo_paallyste_hoitokausi_yht :=
                      lisatyo_paallyste_hoitokausi_yht + COALESCE(lisatyo_paallyste_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && paallyste_tpi AND lisätyö THEN: %', lisatyo_paallyste_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lisatyo_paallyste_val_aika_yht :=
                                lisatyo_paallyste_val_aika_yht + COALESCE(lisatyo_paallyste_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- MHU ylläpidon kulut, jotka eivät ole lisätöitä, eivätkä kannustinjärjestelmä rahavarauksia
                IF rivi.toimenpideinstanssi_id = yllapito_tpi_id AND rivi.maksueratyyppi != 'lisatyo' AND
                   (rivi.yksiloiva_tunniste IS NULL OR rivi.rahavaraus_id != kannustin_id) THEN

                    yllapito_hoitokausi_yht := yllapito_hoitokausi_yht + COALESCE(yllapito_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && yllapito_tpi  THEN: %', yllapito_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        yllapito_val_aika_yht := yllapito_val_aika_yht + COALESCE(yllapito_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- MHU ylläpidon kulut, joka on lisätyö , mutta ei kohdistettu rahavaraus lupaukseen 1 / kannustinjärjestelmään (T3)
                IF rivi.toimenpideinstanssi_id = yllapito_tpi_id AND rivi.maksueratyyppi = 'lisatyo' AND
                   (rivi.yksiloiva_tunniste IS NULL OR rivi.rahavaraus_id != kannustin_id) THEN

                    lisatyo_yllapito_hoitokausi_yht :=
                            lisatyo_yllapito_hoitokausi_yht + COALESCE(lisatyo_yllapito_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && yllapito_tpi AND lisätyö THEN: %', lisatyo_yllapito_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lisatyo_yllapito_val_aika_yht :=
                                lisatyo_yllapito_val_aika_yht + COALESCE(lisatyo_yllapito_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- Korvausinvestointi Hoitokauden alusta
                IF rivi.toimenpideinstanssi_id = korvausinv_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN

                    korvausinv_hoitokausi_yht := korvausinv_hoitokausi_yht + COALESCE(korvausinv_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && korvausinv_tpi  THEN: %', korvausinv_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        korvausinv_val_aika_yht := korvausinv_val_aika_yht + COALESCE(korvausinv_rivi.summa, 0.0);
                    END IF;
                END IF;

                IF rivi.toimenpideinstanssi_id = korvausinv_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN

                    lisatyo_korvausinv_hoitokausi_yht :=
                            lisatyo_korvausinv_hoitokausi_yht + COALESCE(lisatyo_korvausinv_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && korvausinv_tpi AND lisätyö  THEN: %', lisatyo_korvausinv_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lisatyo_korvausinv_val_aika_yht :=
                                lisatyo_korvausinv_val_aika_yht + COALESCE(lisatyo_korvausinv_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- Hoidonjohdon lisätyöt. Hoidon johdon muut kulut haetaan alempana
                IF rivi.toimenpideinstanssi_id = hoidonjohto_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN
                
                    lisatyo_hoidonjohto_hoitokausi_yht :=
                            lisatyo_hoidonjohto_hoitokausi_yht + COALESCE(lisatyo_hoidonjohto_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && hoidonjohto_tpi AND lisätyö  THEN: %', lisatyo_hoidonjohto_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        lisatyo_hoidonjohto_val_aika_yht :=
                                lisatyo_hoidonjohto_val_aika_yht + COALESCE(lisatyo_hoidonjohto_rivi.summa, 0.0);
                    END IF;
                END IF;
            END IF;
        END LOOP;

    RAISE NOTICE 'talvihoito_hoitokausi_yht: %', talvihoito_hoitokausi_yht;
    RAISE NOTICE 'talvihoito_val_aika_yht: %', talvihoito_val_aika_yht;

    RAISE NOTICE 'lyh_hoitokausi_yht: %', lyh_hoitokausi_yht;
    RAISE NOTICE 'lyh_val_aika_yht: %', lyh_val_aika_yht;

    RAISE NOTICE 'sora_hoitokausi_yht: %', sora_hoitokausi_yht;
    RAISE NOTICE 'sora_val_aika_yht: %', sora_val_aika_yht;

    RAISE NOTICE 'paallyste_hoitokausi_yht: %', paallyste_hoitokausi_yht;
    RAISE NOTICE 'paallyste_val_aika_yht: %', paallyste_val_aika_yht;

    RAISE NOTICE 'yllapito_hoitokausi_yht: %', yllapito_hoitokausi_yht;
    RAISE NOTICE 'yllapito_val_aika_yht: %', yllapito_val_aika_yht;

    RAISE NOTICE 'korvausinv_hoitokausi_yht: %', korvausinv_hoitokausi_yht;
    RAISE NOTICE 'korvausinv_val_aika_yht: %', korvausinv_val_aika_yht;

    -- Laskeskellaan hankintoihin kuuluvat yhteen
    hankinnat_hoitokausi_yht := 0.0;
    hankinnat_hoitokausi_yht :=
            hankinnat_hoitokausi_yht + talvihoito_hoitokausi_yht + lyh_hoitokausi_yht + sora_hoitokausi_yht +
            paallyste_hoitokausi_yht + yllapito_hoitokausi_yht + korvausinv_hoitokausi_yht;
    hankinnat_val_aika_yht := 0.0;
    hankinnat_val_aika_yht :=
            hankinnat_val_aika_yht + talvihoito_val_aika_yht + lyh_val_aika_yht + sora_val_aika_yht +
            paallyste_val_aika_yht + yllapito_val_aika_yht + korvausinv_val_aika_yht;

    ----------------------------------------------------
    --- HANKINNAT PÄÄTTYY ------------------------------
    ----------------------------------------------------

    --- Hoidonjohto
    -- Johto- ja hallintokorvaukset
    -- HOIDON JOHTO, tpk 23150.

    -- MHU ja HJU Hoidon johto
    johtojahallinto_rivi :=
        (SELECT hoidon_johto_yhteenveto(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, '23150'::TEXT,
                                        hoidonjohto_tpi_id::INTEGER,
                                        ur::INTEGER,
                                        sopimus_id::INTEGER));
    johtojahallinto_hoitokausi_yht := 0.0;
    johtojahallinto_val_aika_yht := 0.0;
    johtojahallinto_hoitokausi_yht := johtojahallinto_hoitokausi_yht + johtojahallinto_rivi.johto_ja_hallinto_laskutettu;
    johtojahallinto_val_aika_yht := johtojahallinto_val_aika_yht + johtojahallinto_rivi.johto_ja_hallinto_laskutetaan;

    RAISE NOTICE 'johtojahallinto_hoitokausi_yht: %', johtojahallinto_hoitokausi_yht;
    RAISE NOTICE 'johtojahallinto_val_aika_yht: %', johtojahallinto_val_aika_yht;

    -- HOIDONJOHTO --  erillishankinnat
    erillishankinnat_rivi :=
        (SELECT hj_erillishankinnat(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, '23150'::TEXT,
                                    hoidonjohto_tpi_id::INTEGER, ur::INTEGER, sopimus_id::INTEGER));

    erillishankinnat_hoitokausi_yht := 0.0;
    erillishankinnat_val_aika_yht := 0.0;
    erillishankinnat_hoitokausi_yht := erillishankinnat_hoitokausi_yht + erillishankinnat_rivi.hj_erillishankinnat_laskutettu;
    erillishankinnat_val_aika_yht := erillishankinnat_val_aika_yht + erillishankinnat_rivi.hj_erillishankinnat_laskutetaan;

    RAISE NOTICE 'erillishankinnat_hoitokausi_yht: %', erillishankinnat_hoitokausi_yht;
    RAISE NOTICE 'erillishankinnat_val_aika_yht: %', erillishankinnat_val_aika_yht;

    -- HOIDONJOHTO --  HJ-Palkkio
    hjpalkkio_hoitokausi_yht := 0.0;
    hjpalkkio_val_aika_yht := 0.0;
    hjpalkkio_rivi :=
        (SELECT hj_palkkio(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, '23150'::TEXT, hoidonjohto_tpi_id::INTEGER,
                           ur::INTEGER, sopimus_id::INTEGER));
    hjpalkkio_hoitokausi_yht := hjpalkkio_hoitokausi_yht + hjpalkkio_rivi.hj_palkkio_laskutettu;
    hjpalkkio_val_aika_yht := hjpalkkio_val_aika_yht + hjpalkkio_rivi.hj_palkkio_laskutetaan;

    RAISE NOTICE 'hjpalkkio_hoitokausi_yht: %', hjpalkkio_hoitokausi_yht;
    RAISE NOTICE 'hjpalkkio_val_aika_yht: %', hjpalkkio_val_aika_yht;

    -- Hoidonjohto yhteensä
    hoidonjohto_hoitokausi_yht := 0.0;
    hoidonjohto_val_aika_yht := 0.0;
    hoidonjohto_hoitokausi_yht := hoidonjohto_hoitokausi_yht +
            johtojahallinto_hoitokausi_yht + erillishankinnat_hoitokausi_yht + hjpalkkio_hoitokausi_yht;
    hoidonjohto_val_aika_yht := hoidonjohto_val_aika_yht + johtojahallinto_val_aika_yht + erillishankinnat_val_aika_yht + hjpalkkio_val_aika_yht;


    -----------------------------------------------------------
    ------------------- Rahavaraukset -------------------------
    -----------------------------------------------------------

    FOR rahavaraus IN
        SELECT 
          rv.id, 
          rv.nimi 
        FROM rahavaraus rv 
        -- Näytetään vaan rahavaraukset mitkä urakalle asetettu (hallinta)
        JOIN rahavaraus_urakka rvu ON rv.id = rvu.rahavaraus_id  
        WHERE rvu.urakka_id = ur
        -- Sorttaa aakkosilla, nämä tulee tässä järjestyksessä käyttöliittymään asti
        ORDER BY rv.nimi
    LOOP
        -- Resetoi hoitokausi / laskutetaan 
        rv_val_aika_yht := 0;
        rv_hoitokausi_yht := 0;

        FOR rivi IN
            SELECT
                summa AS kht_summa, 
                l.erapaiva AS erapaiva, 
                lk.rahavaraus_id
            FROM kulu l
            JOIN kulu_kohdistus lk ON lk.kulu = l.id
            JOIN toimenpideinstanssi tpi 
                ON lk.toimenpideinstanssi = tpi.id 
                AND tpi.id IN (
                    lyh_tpi_id, 
                    sora_tpi_id, 
                    yllapito_tpi_id, 
                    paallyste_tpi_id, 
                    talvihoito_tpi_id, 
                    korvausinv_tpi_id
                )
            WHERE lk.rahavaraus_id = rahavaraus.id
                AND lk.poistettu IS NOT TRUE
                AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
        LOOP
            IF rivi.erapaiva <= aikavali_loppupvm THEN

                -- Rahavaraus X Hoitokausi yhteensä 
                rv_hoitokausi_yht := rv_hoitokausi_yht + COALESCE(rivi.kht_summa, 0.0);

                IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                    -- Rahavaraus X valittu kk yhteensä 
                    rv_val_aika_yht := rv_val_aika_yht + COALESCE(rivi.kht_summa, 0.0);
                END IF;
            END IF;
        END LOOP;

        -- Lisää arrayhyn kaikki rahavarausten tulokset, jotka parsitaan gridiin 
        rahavaraus_nimet := array_append(rahavaraus_nimet, rahavaraus.nimi);
        hoitokausi_yht_array := array_append(hoitokausi_yht_array, rv_hoitokausi_yht);
        val_aika_yht_array := array_append(val_aika_yht_array, rv_val_aika_yht);

    END LOOP;

    -- Laske rahavaraukset yhteen 
    -- Tällä voi siis korvata äkilliset, vahingot, kannustin muuttujat
    -- Rahavaraukset hoitokausi
    FOR i IN 1..array_length(hoitokausi_yht_array, 1) LOOP
        kaikki_rahavaraukset_hoitokausi_yht := kaikki_rahavaraukset_hoitokausi_yht + hoitokausi_yht_array[i];
    END LOOP;

    -- Rahavaraukset valittu kk
    FOR i IN 1..array_length(val_aika_yht_array, 1) LOOP
        kaikki_rahavaraukset_val_yht := kaikki_rahavaraukset_val_yht + val_aika_yht_array[i];
    END LOOP;

    ---------------------------------------------
    ---------------  Muut kulut   ---------------
    ---------------------------------------------
    FOR rivi IN
        SELECT
            summa, 
            l.erapaiva AS erapaiva,
            lk.tavoitehintainen AS tavoitehintainen 
        FROM kulu l
        JOIN kulu_kohdistus lk ON lk.kulu = l.id
        -- Etsi pelkästään muukulu tyyppiset  kirjaukset, toimenpideinstansseilla ei ole näissä väliä 
        WHERE lk.tyyppi = 'muukulu'
          AND lk.poistettu IS NOT TRUE
          AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
    LOOP
        IF rivi.erapaiva <= aikavali_loppupvm THEN
            --
            -- ~ Hoitokausi ~
            -- 
            IF rivi.tavoitehintainen THEN
                -- Tavoitehintainen Muut kulut Hoitokausi yhteensä
                muut_kulut_hoitokausi := muut_kulut_hoitokausi + COALESCE(rivi.summa, 0.0);
            ELSE
                -- Ei tavoitehintainen Muut kulut Hoitokausi yhteensä
                muut_kulut_ei_tavoite_hoitokausi := muut_kulut_ei_tavoite_hoitokausi + COALESCE(rivi.summa, 0.0);
            END IF;

            --
            -- ~ Valittu kk ~
            -- 
            IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                IF rivi.tavoitehintainen THEN
                    -- Tavoitehintainen Muut kulut valittu kk yhteensä
                    muut_kulut_val_aika := muut_kulut_val_aika + COALESCE(rivi.summa, 0.0);
                ELSE
                    -- Ei tavoitehintainen Muut kulut valittu kk yhteensä
                    muut_kulut_ei_tavoite_val_aika := muut_kulut_ei_tavoite_val_aika + COALESCE(rivi.summa, 0.0);
                END IF;
            END IF;
        END IF;
    END LOOP;

    -- Tavoitehintaiset Yhteensä-  arvot,  nämä on tekohetkellä aivan samat,
    -- mutta tehty kuitenkin, jos jatkossa tämän taulukon alle tulee lisää rivejä, niitä voi tähän niputtaa
    muut_kulut_hoitokausi_yht := muut_kulut_hoitokausi;
    muut_kulut_val_aika_yht := muut_kulut_val_aika;

    -- Ei tavoitehintaiset yhteensä-  arvot lasketaan bonusten ja sanktioiden jälkeen alempana
    
    ---------------------------------------------
    --------------- Tavoitehinta  ---------------
    ---------------------------------------------

    -- Laskeskellaan tavoitehintaan kuuluvat yhteen
    -- 2022-10-01 jälkeen alihankitabonus ei ole enää MHU ylläpitoon kuuluvana, vaan omana rivinään, niin iffitellään se tarvittaessa mukaan
    -- Tavoitehinta hoitokausi 
    tavhin_hoitokausi_yht := 0.0;
    tavhin_hoitokausi_yht := tavhin_hoitokausi_yht +
            talvihoito_hoitokausi_yht + lyh_hoitokausi_yht + sora_hoitokausi_yht +
            paallyste_hoitokausi_yht + yllapito_hoitokausi_yht + korvausinv_hoitokausi_yht +
            johtojahallinto_hoitokausi_yht + erillishankinnat_hoitokausi_yht + hjpalkkio_hoitokausi_yht;
    
    -- Tavoitehinta valittu kk 
    tavhin_val_aika_yht := 0.0;
    tavhin_val_aika_yht := tavhin_val_aika_yht +
            talvihoito_val_aika_yht + lyh_val_aika_yht + sora_val_aika_yht +
            paallyste_val_aika_yht + yllapito_val_aika_yht + korvausinv_val_aika_yht +
            johtojahallinto_val_aika_yht +
            erillishankinnat_val_aika_yht + hjpalkkio_val_aika_yht;

    -- Budjettia jäljellä
    budjettia_jaljella := 0.0;
    budjettia_jaljella := budjettia_jaljella + (hk_tavhintsiirto_ed_vuodelta + hoitokauden_tavoitehinta) - tavhin_hoitokausi_yht;

    ---------------------------------------------
    ---- Muut toteutuneet kustannukset  ---------
    ---------------------------------------------

    -- Lisätyöt yhteensä
    lisatyot_hoitokausi_yht := 0.0;
    lisatyot_val_aika_yht := 0.0;
    lisatyot_hoitokausi_yht :=
            lisatyot_hoitokausi_yht + lisatyo_talvihoito_hoitokausi_yht + lisatyo_lyh_hoitokausi_yht +
            lisatyo_sora_hoitokausi_yht + lisatyo_paallyste_hoitokausi_yht + lisatyo_yllapito_hoitokausi_yht +
            lisatyo_korvausinv_hoitokausi_yht + lisatyo_hoidonjohto_hoitokausi_yht;
    lisatyot_val_aika_yht :=
            lisatyot_val_aika_yht + lisatyo_talvihoito_val_aika_yht + lisatyo_lyh_val_aika_yht +
            lisatyo_sora_val_aika_yht + lisatyo_paallyste_val_aika_yht + lisatyo_yllapito_val_aika_yht +
            lisatyo_korvausinv_val_aika_yht + lisatyo_hoidonjohto_val_aika_yht;


    -----------------------------------------------------------------------------------------------------------
    ------------------- Bonukset, sanktiot ja päätöksen ylitykset ------------------------------------------------------------------
    -----------------------------------------------------------------------------------------------------------
    -- Haetaan bonukset erilliskustannustaulusta pelkästään, koska ylläpidolle ei näytetä tätä raporttia
    -- Sanktiosta haetaan perus sanktiot, koska ylläpidosta ei tarvitse välittää
    bonukset_hoitokausi_yht := 0.0;
    bonukset_val_aika_yht := 0.0;
    FOR bonukset_rivi IN SELECT ek.laskutuskuukausi                                 as laskutuskuukausi,
                                ek.rahasumma                                        as summa,
                                (SELECT korotettuna
                                 from erilliskustannuksen_indeksilaskenta(ek.laskutuskuukausi, ek.indeksin_nimi, ek.rahasumma,
                                                                          ek.urakka, ek.tyyppi,
                                                                          CASE
                                                                              WHEN u.tyyppi = 'teiden-hoito'::urakkatyyppi
                                                                                  THEN TRUE
                                                                              ELSE FALSE
                                                                              END)) AS summa_korotettuna
                         FROM erilliskustannus ek
                                  JOIN urakka u ON ek.urakka = u.id
                         WHERE ek.urakka = ur
                           -- MHU urakoille on olennaista, että bonukset on tallennettu 23150 koodilla olevalle toimenpideinstanssille
                           -- eli hoidon johdolle. Alueurakoilla tätä vaatimusta ei ole. Joten bonukset voivat kohdistua
                           -- vapaammin mille tahansa toimenpideinstanssille
                           AND (u.tyyppi = 'hoito' OR
                                (u.tyyppi = 'teiden-hoito' AND ek.toimenpideinstanssi = (SELECT tpi.id AS id
                                                                                         FROM toimenpideinstanssi tpi
                                                                                                  JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                                                                                                  JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id,
                                                                                              maksuera m
                                                                                         WHERE tpi.urakka = ur
                                                                                           AND m.toimenpideinstanssi = tpi.id
                                                                                           AND tpk2.koodi = '23150'
                                                                                         LIMIT 1)))
                           AND ek.laskutuskuukausi BETWEEN hk_alkupvm AND aikavali_loppupvm
                           AND ek.poistettu IS NOT TRUE
                           AND ek.tyyppi != 'muu'::erilliskustannustyyppi

        LOOP

            RAISE NOTICE 'bonukset_rivi: % ', bonukset_rivi;
            RAISE NOTICE 'bonukset_rivi.summa_korotettuna: %', bonukset_rivi.summa_korotettuna;

            IF bonukset_rivi.laskutuskuukausi <= aikavali_loppupvm THEN
                -- Hoitokauden alusta
                bonukset_hoitokausi_yht := bonukset_hoitokausi_yht + COALESCE(bonukset_rivi.summa_korotettuna, 0.0);
                RAISE NOTICE 'bonukset_rivi.laskutuskuukausi <= aikavali_loppupvm THEN: %', bonukset_hoitokausi_yht;

                IF bonukset_rivi.laskutuskuukausi >= aikavali_alkupvm AND
                   bonukset_rivi.laskutuskuukausi <= aikavali_loppupvm THEN
                    -- Laskutetaan nyt
                    bonukset_val_aika_yht := bonukset_val_aika_yht + COALESCE(bonukset_rivi.summa_korotettuna, 0.0);
                END IF;
            END IF;
        END LOOP;

    -- Sanktiot
    sanktiot_hoitokausi_yht := 0.0;
    sanktiot_val_aika_yht := 0.0;
    FOR sanktiot_rivi IN SELECT s.perintapvm                                      as pvm,
                                s.maara * -1                                      as summa,
                                (SELECT korotettuna
                                 FROM sanktion_indeksikorotus(s.perintapvm,
                                                              s.indeksi, s.maara,
                                                              ur,
                                                              s.sakkoryhma)) * -1 AS summa_korotettuna
                         FROM sanktio s
                                  JOIN toimenpideinstanssi tpi
                                       ON tpi.urakka = ur AND tpi.id = s.toimenpideinstanssi
                                  JOIN sanktiotyyppi st ON s.tyyppi = st.id
                         WHERE s.perintapvm BETWEEN hk_alkupvm AND aikavali_loppupvm
                           AND s.poistettu IS NOT TRUE
        LOOP
            RAISE NOTICE 'sanktiot_rivi: % ', sanktiot_rivi;
            RAISE NOTICE 'sanktiot_rivi.summa_korotettuna: %', sanktiot_rivi.summa_korotettuna;

            IF sanktiot_rivi.pvm <= aikavali_loppupvm THEN
                -- Hoitokauden alusta
                sanktiot_hoitokausi_yht := sanktiot_hoitokausi_yht + COALESCE(sanktiot_rivi.summa_korotettuna, 0.0);
                RAISE NOTICE 'sanktiot_rivi.pvm <= aikavali_loppupvm THEN: %', sanktiot_hoitokausi_yht;

                IF sanktiot_rivi.pvm >= aikavali_alkupvm AND
                   sanktiot_rivi.pvm <= aikavali_loppupvm THEN
                    -- Laskutetaan nyt
                    sanktiot_val_aika_yht := sanktiot_val_aika_yht + COALESCE(sanktiot_rivi.summa_korotettuna, 0.0);
                END IF;
            END IF;
        END LOOP;

    -- Päätöksen ylitykset
    paatos_tavoitepalkkio_hoitokausi_yht := 0.0;
    paatos_tavoitepalkkio_val_aika_yht := 0.0;
    paatos_tavoiteh_ylitys_hoitokausi_yht := 0.0;
    paatos_tavoiteh_ylitys_val_aika_yht := 0.0;
    paatos_kattoh_ylitys_hoitokausi_yht := 0.0;
    paatos_kattoh_ylitys_val_aika_yht := 0.0;
    FOR paatos_rivi IN SELECT summa AS summa, l.erapaiva AS erapaiva, tr.nimi as tehtavaryhma_nimi
                       FROM kulu l
                                JOIN kulu_kohdistus lk ON lk.kulu = l.id
                                JOIN toimenpideinstanssi tpi
                                     ON lk.toimenpideinstanssi = tpi.id AND tpi.id = hoidonjohto_tpi_id
                                JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma
                       WHERE lk.maksueratyyppi = 'kokonaishintainen'
                         AND lk.poistettu IS NOT TRUE
                         AND l.urakka = ur
                         AND lk.tehtavaryhma in
                             (select tr.id
                              from tehtavaryhma tr
                              where tr.nimi ilike 'Hoitovuoden päättäminen%') -- Harmillisesti joutuu käyttämään nimeä, koska tyyppejä ei ole
                         AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm

        LOOP

            RAISE NOTICE 'paatos_rivi: %', rivi;
            IF paatos_rivi.erapaiva <= aikavali_loppupvm AND
               paatos_rivi.tehtavaryhma_nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio' THEN
                -- Hoitokauden alusta
                paatos_tavoitepalkkio_hoitokausi_yht :=
                        paatos_tavoitepalkkio_hoitokausi_yht + COALESCE(paatos_rivi.summa, 0.0);
                RAISE NOTICE 'paatos_tavoitepalkkio_hoitokausi_yht: %', paatos_tavoitepalkkio_hoitokausi_yht;

                IF paatos_rivi.erapaiva >= aikavali_alkupvm AND
                   paatos_rivi.erapaiva <= aikavali_loppupvm THEN
                    -- Laskutetaan nyt
                    paatos_tavoitepalkkio_val_aika_yht :=
                            paatos_tavoitepalkkio_val_aika_yht + COALESCE(paatos_rivi.summa, 0.0);
                END IF;
            END IF;

            IF paatos_rivi.erapaiva <= aikavali_loppupvm AND
               paatos_rivi.tehtavaryhma_nimi =
               'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä' THEN
                -- Hoitokauden alusta
                paatos_tavoiteh_ylitys_hoitokausi_yht :=
                        paatos_tavoiteh_ylitys_hoitokausi_yht + COALESCE(paatos_rivi.summa, 0.0);
                RAISE NOTICE 'paatos_tavoiteh_ylitys_hoitokausi_yht: %', paatos_tavoiteh_ylitys_hoitokausi_yht;

                IF paatos_rivi.erapaiva >= aikavali_alkupvm AND
                   paatos_rivi.erapaiva <= aikavali_loppupvm THEN
                    -- Laskutetaan nyt
                    paatos_tavoiteh_ylitys_val_aika_yht :=
                            paatos_tavoiteh_ylitys_val_aika_yht + COALESCE(paatos_rivi.summa, 0.0);
                END IF;
            END IF;

            IF paatos_rivi.erapaiva <= aikavali_loppupvm AND
               paatos_rivi.tehtavaryhma_nimi =
               'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä' THEN
                -- Hoitokauden alusta
                paatos_kattoh_ylitys_hoitokausi_yht :=
                        paatos_kattoh_ylitys_hoitokausi_yht + COALESCE(paatos_rivi.summa, 0.0);
                RAISE NOTICE 'paatos_kattoh_ylitys_hoitokausi_yht: %', paatos_kattoh_ylitys_hoitokausi_yht;

                IF paatos_rivi.erapaiva >= aikavali_alkupvm AND
                   paatos_rivi.erapaiva <= aikavali_loppupvm THEN
                    -- Laskutetaan nyt
                    paatos_kattoh_ylitys_val_aika_yht :=
                            paatos_kattoh_ylitys_val_aika_yht + COALESCE(paatos_rivi.summa, 0.0);
                END IF;
            END IF;

            RAISE NOTICE 'paatos_rivi: % ', paatos_rivi;
            RAISE NOTICE 'paatos_rivi.summa: %', paatos_rivi.summa;
        end loop;

    -- Muut kulut yhteensä, ei tavoitehintaiset
    muut_kulut_ei_tavoite_hoitokausi_yht := bonukset_hoitokausi_yht + sanktiot_hoitokausi_yht + muut_kulut_ei_tavoite_hoitokausi;
    muut_kulut_ei_tavoite_val_aika_yht := bonukset_val_aika_yht + sanktiot_val_aika_yht  + muut_kulut_ei_tavoite_val_aika;

    -- Muut kustannukset yhteensä
    muut_kustannukset_hoitokausi_yht := 0.0;
    muut_kustannukset_val_aika_yht := 0.0;

    muut_kustannukset_hoitokausi_yht :=
            muut_kustannukset_hoitokausi_yht + lisatyot_hoitokausi_yht + bonukset_hoitokausi_yht + sanktiot_hoitokausi_yht +
            paatos_tavoitepalkkio_hoitokausi_yht + paatos_tavoiteh_ylitys_hoitokausi_yht +
            paatos_kattoh_ylitys_hoitokausi_yht + 
            -- Ei tavoitehintaiset
            muut_kulut_ei_tavoite_hoitokausi + 
            -- Tavoitehintaiset
            muut_kulut_hoitokausi_yht + 
            -- Rahavaraukset
            kaikki_rahavaraukset_hoitokausi_yht;
            
    muut_kustannukset_val_aika_yht :=
            muut_kustannukset_val_aika_yht + lisatyot_val_aika_yht + bonukset_val_aika_yht + sanktiot_val_aika_yht +
            paatos_tavoitepalkkio_val_aika_yht + paatos_tavoiteh_ylitys_val_aika_yht +
            paatos_kattoh_ylitys_val_aika_yht + 
            -- Ei tavoitehintaiset
            muut_kulut_ei_tavoite_val_aika + 
            -- Tavoitehintaiset
            muut_kulut_val_aika_yht  + 
            -- Rahavaraukset
            kaikki_rahavaraukset_val_yht;

    -- Kaikki yhteensä
    yhteensa_kaikki_hoitokausi_yht := 0.0;
    yhteensa_kaikki_val_aika_yht := 0.0;
    yhteensa_kaikki_hoitokausi_yht := yhteensa_kaikki_hoitokausi_yht + tavhin_hoitokausi_yht + muut_kustannukset_hoitokausi_yht;
    yhteensa_kaikki_val_aika_yht := yhteensa_kaikki_val_aika_yht + tavhin_val_aika_yht + muut_kustannukset_val_aika_yht;
    
    
    tulos := (
        -- Talvihoito
              talvihoito_hoitokausi_yht, talvihoito_val_aika_yht,
        -- Liikenne ymp. hoito
              lyh_hoitokausi_yht, lyh_val_aika_yht,
        -- Soratien hoito
              sora_hoitokausi_yht, sora_val_aika_yht,
        -- Päällysteidne paikkaus
              paallyste_hoitokausi_yht, paallyste_val_aika_yht,
        -- Ylläpito
              yllapito_hoitokausi_yht, yllapito_val_aika_yht,
        -- Korvausinvestointi
              korvausinv_hoitokausi_yht, korvausinv_val_aika_yht,
        -- Hankinnat yht.
              hankinnat_hoitokausi_yht, hankinnat_val_aika_yht,
        -- Johto- ja hallintokorvaukset
              johtojahallinto_hoitokausi_yht, johtojahallinto_val_aika_yht,
        -- Erillishankinnat
              erillishankinnat_hoitokausi_yht, erillishankinnat_val_aika_yht,
        -- Hoidonjohtopalkkio
              hjpalkkio_hoitokausi_yht, hjpalkkio_val_aika_yht,
        -- Hoidonjohto yhteensä
              hoidonjohto_hoitokausi_yht, hoidonjohto_val_aika_yht,
        -- Tavoitehinnat yht.
              tavhin_hoitokausi_yht, tavhin_val_aika_yht,
        -- Tavoitehinnan muodostus
              hoitokauden_tavoitehinta, hk_tavhintsiirto_ed_vuodelta,
              budjettia_jaljella,
        -- Lisätyöt
        -- Lisätyö talvihoito
              lisatyo_talvihoito_hoitokausi_yht, lisatyo_talvihoito_val_aika_yht,
        -- Lisätyö liikenneympäristön hoito
              lisatyo_lyh_hoitokausi_yht, lisatyo_lyh_val_aika_yht,
        -- Lisätyö sorateiden hoito
              lisatyo_sora_hoitokausi_yht, lisatyo_sora_val_aika_yht,
        -- Lisätyö päällysteiden paikkaus
              lisatyo_paallyste_hoitokausi_yht, lisatyo_paallyste_val_aika_yht,
        -- Lisätyö ylläpito
              lisatyo_yllapito_hoitokausi_yht, lisatyo_yllapito_val_aika_yht,
        -- Lisätyö korvausinvestoinnit
              lisatyo_korvausinv_hoitokausi_yht, lisatyo_korvausinv_val_aika_yht,
        -- Lisätyö hoidonjohto
              lisatyo_hoidonjohto_hoitokausi_yht, lisatyo_hoidonjohto_val_aika_yht,
        -- Lisätyöt yhteensä
              lisatyot_hoitokausi_yht, lisatyot_val_aika_yht,
        --- Muut kustannukset
        -- Bonukset
              bonukset_hoitokausi_yht, bonukset_val_aika_yht,
        -- Sanktiot
              sanktiot_hoitokausi_yht, sanktiot_val_aika_yht,
        -- Tavoitepalkkiot
              paatos_tavoitepalkkio_hoitokausi_yht, paatos_tavoitepalkkio_val_aika_yht,
        -- Tavoitehinnan ylitys
              paatos_tavoiteh_ylitys_hoitokausi_yht, paatos_tavoiteh_ylitys_val_aika_yht,
        -- Kattohinnan ylitys
              paatos_kattoh_ylitys_hoitokausi_yht, paatos_kattoh_ylitys_val_aika_yht,
        -- Muut kustannukset yhteensä
              muut_kustannukset_hoitokausi_yht, muut_kustannukset_val_aika_yht,
        -- Kaikki yhteensä
              yhteensa_kaikki_hoitokausi_yht, yhteensa_kaikki_val_aika_yht,
        -- Indeksilaskennan perusluku
              perusluku, 
        -- Urakan rahavaraukset ja arvot
              rahavaraus_nimet, hoitokausi_yht_array, val_aika_yht_array,
              kaikki_rahavaraukset_hoitokausi_yht, kaikki_rahavaraukset_val_yht,
        -- Muut kulut 
              -- Tavoitehintaan vaikuttavat 
              muut_kulut_hoitokausi, muut_kulut_val_aika, 
              muut_kulut_hoitokausi_yht, muut_kulut_val_aika_yht,
              -- Ei tavoitehintaiset 
              muut_kulut_ei_tavoite_hoitokausi, muut_kulut_ei_tavoite_val_aika,
              muut_kulut_ei_tavoite_hoitokausi_yht, muut_kulut_ei_tavoite_val_aika_yht
        );
    return next tulos;
END;
$$;
