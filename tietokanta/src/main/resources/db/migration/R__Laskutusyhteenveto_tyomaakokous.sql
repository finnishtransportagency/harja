-- Työmaakokousta varten räätälöityyn laskutusyhteenvetoon liittyvät tietokantahaut on pakattu
-- tässä yhden funktion alle.

-- Siivotaan ensin vanhat pois, niin uudet voi toimia
DROP FUNCTION IF EXISTS ly_raportti_tyomaakokous(DATE, DATE, DATE, DATE, INTEGER);
-- Poistetaan myös funktio ilman parametreja
DROP FUNCTION IF EXISTS ly_raportti_tyomaakokous;
DROP TYPE IF EXISTS LY_RAPORTTI_TYOMAAKOKOUS_TULOS;

-- Ensin määritellään TYPE, joka on ikäänkuin se objekti/rivi, jonka funktio palauttaa.
-- Tähän on sisällytetty kaikki yksittäiset tulokset, jotta koodiin ei jää enää yhtään laskutehtävää tehtäväksi.
CREATE TYPE LY_RAPORTTI_TYOMAAKOKOUS_TULOS AS
(
    talvihoito_hoitokausi_yht                        NUMERIC,
    talvihoito_val_aika_yht                          NUMERIC,
    lyh_hoitokausi_yht                               NUMERIC,
    lyh_val_aika_yht                                 NUMERIC,
    sora_hoitokausi_yht                              NUMERIC,
    sora_val_aika_yht                                NUMERIC,
    paallyste_hoitokausi_yht                         NUMERIC,
    paallyste_val_aika_yht                           NUMERIC,
    yllapito_hoitokausi_yht                          NUMERIC,
    yllapito_val_aika_yht                            NUMERIC,
    korvausinv_hoitokausi_yht                        NUMERIC,
    korvausinv_val_aika_yht                          NUMERIC,
    hankinnat_hoitokausi_yht                         NUMERIC,
    hankinnat_val_aika_yht                           NUMERIC,
    johtojahallinto_hoitokausi_yht                   NUMERIC,
    johtojahallinto_val_aika_yht                     NUMERIC,
    erillishankinnat_hoitokausi_yht                  NUMERIC,
    erillishankinnat_val_aika_yht                    NUMERIC,
    hjpalkkio_hoitokausi_yht                         NUMERIC,
    hjpalkkio_val_aika_yht                           NUMERIC,
    hoidonjohto_hoitokausi_yht                       NUMERIC,
    hoidonjohto_val_aika_yht                         NUMERIC,

    -- Muutokset 
    muutostyo_val_aika_yht                           NUMERIC,
    muutostyo_hoitokausi_yht                         NUMERIC,
    muutos_erillis_hoitokausi_yht                    NUMERIC,
    muutos_erillis_val_aika_yht                      NUMERIC,
    jjh_muutos_hoitokausi_yht                        NUMERIC,
    jjh_muutos_val_aika_yht                          NUMERIC,

    hankinnat_ja_hoidon_hk_yht                       NUMERIC,
    hankinnat_ja_hoidon_val_yht                      NUMERIC,
    tavhin_hoitokausi_yht                            NUMERIC,
    tavhin_val_aika_yht                              NUMERIC,
    hoitovuoden_alun_indkorj_tavoitehinta            NUMERIC,
    hoitokauden_tavoitehinta                         NUMERIC,
    tavoitehinta_on_oikaistu                         BOOLEAN,
    tavoitehinta_oikaisu_summa                       NUMERIC,
    -- Valikatselmuksesta siirretyt kulut edelliseltä vuodelta
    hk_valikatselmus_siirrot_ed_vuodelta             NUMERIC,
    budjettia_jaljella                               NUMERIC,
    lisatyo_talvihoito_hoitokausi_yht                NUMERIC,
    lisatyo_talvihoito_val_aika_yht                  NUMERIC,
    lisatyo_lyh_hoitokausi_yht                       NUMERIC,
    lisatyo_lyh_val_aika_yht                         NUMERIC,
    lisatyo_sora_hoitokausi_yht                      NUMERIC,
    lisatyo_sora_val_aika_yht                        NUMERIC,
    lisatyo_paallyste_hoitokausi_yht                 NUMERIC,
    lisatyo_paallyste_val_aika_yht                   NUMERIC,
    lisatyo_yllapito_hoitokausi_yht                  NUMERIC,
    lisatyo_yllapito_val_aika_yht                    NUMERIC,
    lisatyo_korvausinv_hoitokausi_yht                NUMERIC,
    lisatyo_korvausinv_val_aika_yht                  NUMERIC,
    lisatyo_hoidonjohto_hoitokausi_yht               NUMERIC,
    lisatyo_hoidonjohto_val_aika_yht                 NUMERIC,
    lisatyot_hoitokausi_yht                          NUMERIC,
    lisatyot_val_aika_yht                            NUMERIC,
    bonukset_hoitokausi_yht                          NUMERIC,
    bonukset_val_aika_yht                            NUMERIC,
    sanktiot_hoitokausi_yht                          NUMERIC,
    sanktiot_val_aika_yht                            NUMERIC,
    paatos_tavoitepalkkio_hoitokausi_yht             NUMERIC,
    paatos_tavoitepalkkio_val_aika_yht               NUMERIC,
    paatos_tavoiteh_ylitys_hoitokausi_yht            NUMERIC,
    paatos_tavoiteh_ylitys_val_aika_yht              NUMERIC,
    paatos_kattoh_ylitys_hoitokausi_yht              NUMERIC,
    paatos_kattoh_ylitys_val_aika_yht                NUMERIC,
    paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht NUMERIC,
    paatos_hoidonjohtopalkkion_muutos_val_aika_yht   NUMERIC,
    muut_kustannukset_hoitokausi_yht                 NUMERIC,
    muut_kustannukset_val_aika_yht                   NUMERIC,
    yhteensa_kaikki_hoitokausi_yht                   NUMERIC,
    yhteensa_kaikki_val_aika_yht                     NUMERIC,
    perusluku                                        NUMERIC,

    -- Rahavaraukset 
    rahavaraus_nimet                                 TEXT[],
    hoitokausi_yht_array                             NUMERIC[],
    val_aika_yht_array                               NUMERIC[],
    kaikki_rahavaraukset_hoitokausi_yht              NUMERIC,
    kaikki_rahavaraukset_val_yht                     NUMERIC,

    -- Muut kulut, tavoitehintaan vaikuttavat
    muut_kulut_hoitokausi                            NUMERIC,
    muut_kulut_val_aika                              NUMERIC,
    muut_kulut_hoitokausi_yht                        NUMERIC,
    muut_kulut_val_aika_yht                          NUMERIC,

    -- Ei tavoitehintaan vaikuttavat muut kulut 
    muut_kulut_ei_tavoite_hoitokausi                 NUMERIC,
    muut_kulut_ei_tavoite_val_aika                   NUMERIC,
    muut_kulut_ei_tavoite_hoitokausi_yht             NUMERIC,
    muut_kulut_ei_tavoite_val_aika_yht               NUMERIC,

    -- Laskutusraja
    laskutusraja_yht                                 NUMERIC,
    laskutusrajaan_jaljella                          NUMERIC,
    onko_laskutusraja_kaytossa                       BOOLEAN,
    onko_laskutusraja_ylittynyt                      BOOLEAN,
    laskutusraja_laskutettavaa_yht                   NUMERIC,
    laskutusraja_laskutettavaa_val_aika              NUMERIC,
    laskutusrajan_ylittynyt_yht                      NUMERIC,
    laskutusrajan_ylittynyt_val_aika                 NUMERIC,
    laskutettavaa_kaikki_yht                         NUMERIC,
    laskutettavaa_kaikki_val_aika                    NUMERIC,

    -- Pysyvät muutokset (mhu_muutos-taulusta)
    pysyvat_muutokset_hoitokausi_yht                 NUMERIC,
    pysyvat_muutokset_val_aika_yht                   NUMERIC,
    pysyvat_muutokset_ed_hoitokausi                  NUMERIC

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

    -- Muutokset 
    muutostyo_hoitokausi_yht              NUMERIC;
    muutostyo_val_aika_yht                NUMERIC;
    muutos_erillis_hoitokausi_yht         NUMERIC;
    muutos_erillis_val_aika_yht           NUMERIC;
    jjh_muutos_hoitokausi_yht             NUMERIC;
    jjh_muutos_val_aika_yht               NUMERIC;
    muutokset_erillis_rivi                RECORD;
    rivi_on_erillis_muutos                BOOLEAN;
    muutokset_jjh_rivi                    RECORD;
    rivi_on_jjh_muutos                    BOOLEAN;


    -- Hankinnat ja hoidonjohto yhteensä
    hankinnat_ja_hoidon_hk_yht            NUMERIC;
    hankinnat_ja_hoidon_val_yht           NUMERIC;

    -- Tavoitehinnat yhteensä
    tavhin_hoitokausi_yht                 NUMERIC; -- Tarkoittaa kertyneitä kustannuksia, joitka kuuluvat tavoitehintaan.
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
    paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht   NUMERIC;
    paatos_hoidonjohtopalkkion_muutos_val_aika_yht     NUMERIC;
    paatos_rivi                           RECORD;
    muut_kustannukset_hoitokausi_yht      NUMERIC;
    muut_kustannukset_val_aika_yht        NUMERIC;
    yhteensa_kaikki_hoitokausi_yht        NUMERIC;
    yhteensa_kaikki_val_aika_yht          NUMERIC;

    -- Asetuksia
    urakan_alkuvuosi                      NUMERIC;
    hk_alkuvuosi                          NUMERIC;
    hk_loppuvuosi                         NUMERIC;
    hk_alkukuukausi                       NUMERIC;
    perusluku                             NUMERIC; -- urakan indeksilaskennan perusluku (urakkasopimusta edeltävän vuoden syys-,loka, marraskuun keskiarvo)
    indeksi_vuosi                         INTEGER;
    indeksinimi                           VARCHAR; -- MAKU 2015
    sopimus_id                            INTEGER;
    hoitokauden_nro                       NUMERIC;
    hoitokauden_vuosi                     NUMERIC; -- Käytetään kun loopataan valitut hoitovuodet aikavälistä
    hoitovuoden_alun_indkorj_tavoitehinta NUMERIC;
    tavoitehinta_oikaisu_summa            NUMERIC;
    hoitokauden_tavoitehinta              NUMERIC; -- Tällä tarkoitetaan hoitokauden alun tavoitehintaa. Ole tarkkana, että milloin tähän lisätään oikaisut tai muut muutokset
    tavoitehinta_on_oikaistu              BOOLEAN;
    -- Valikatselmuksesta siirretyt kulut edelliseltä vuodelta
    hk_valikatselmus_siirrot_ed_vuodelta  NUMERIC;
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

    -- Pysyvät muutokset
    pysyvat_muutokset_hoitokausi_yht      NUMERIC := 0.0;
    pysyvat_muutokset_val_aika_yht        NUMERIC := 0.0;
    pysyvat_muutokset_ed_hoitokausi       NUMERIC := 0.0;

    -- Laskutusraja
    laskutusraja_yht                      NUMERIC;
    laskutusrajaan_jaljella               NUMERIC;
    onko_laskutusraja_kaytossa            BOOLEAN;
    onko_laskutusraja_ylittynyt           BOOLEAN;
    -- josta laskutettavaa (sisältyy laskutusrajaan)
    laskutusraja_laskutettavaa_yht        NUMERIC;
    laskutusraja_laskutettavaa_val_aika   NUMERIC;
    -- josta laskutusrajan ylittäviä kustannuksia
    laskutusrajan_ylittynyt_yht           NUMERIC;
    laskutusrajan_ylittynyt_val_aika      NUMERIC;
    -- yhteenveto 
    laskutettavaa_kaikki_yht              NUMERIC;
    laskutettavaa_kaikki_val_aika         NUMERIC;

    tulos                                 LY_RAPORTTI_TYOMAAKOKOUS_TULOS;

BEGIN

    perusluku := indeksilaskennan_perusluku(ur);
    hk_alkuvuosi := (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER);
    hk_alkukuukausi := (SELECT EXTRACT(MONTH FROM hk_alkupvm) :: INTEGER);
    hk_loppuvuosi := (SELECT EXTRACT(YEAR FROM hk_loppupvm) :: INTEGER);

    -- Jos 10.1 tai alle valittuna, tarkoittaa että ei haluta laskea mukaan seuraavaa hoitokautta 
    IF EXTRACT(MONTH FROM hk_loppupvm) <= 9 
    OR (EXTRACT(MONTH FROM hk_loppupvm) = 10 AND EXTRACT(DAY FROM hk_loppupvm) = 1) THEN
        hk_loppuvuosi := hk_loppuvuosi - 1; 
    END IF;

    indeksi_vuosi := hk_alkuvuosi; -- Joissakin indeksilaskennoissa voidaan käyttää hoitokauden edeltävää syyskuuta tai elokuuta indeksissä. TArkista tapauskohtaisesti
    indeksinimi := (SELECT indeksi FROM urakka u WHERE u.id = ur);
    sopimus_id := (SELECT id FROM sopimus WHERE urakka = ur AND paasopimus IS NULL);
    SELECT u.id, u.alkupvm, u.nimi  FROM urakka u WHERE u.id = ur INTO urakan_tiedot;
    RAISE NOTICE '*** Urakan tiedot: % ', urakan_tiedot;

    -------------------------
    -- Valitun aikavälin hoitokausien tavoitehinnat
    -- Esim. urakan alkuvuosi 2019 ja aikavälinä 2019-2024 (5 hoitokautta), summaa kaikkien hoitokausien tavoitehinnat yhteen
    -------------------------
    hoitokauden_tavoitehinta := 0;
    hoitovuoden_alun_indkorj_tavoitehinta := 0;
    tavoitehinta_oikaisu_summa := 0;
    urakan_alkuvuosi := (SELECT EXTRACT(YEAR FROM urakan_tiedot.alkupvm) :: INTEGER);

    -- Laske valittujen hoitokausien tavoitehinnat yhteen
    FOR hoitokauden_vuosi IN hk_alkuvuosi..hk_loppuvuosi
    LOOP
        hoitokauden_nro := hoitokauden_vuosi - urakan_alkuvuosi + 1;

        IF hoitokauden_nro >= (hk_alkuvuosi - urakan_alkuvuosi + 1) 
        AND hoitokauden_nro <= (hk_loppuvuosi - urakan_alkuvuosi + 1) THEN

            RAISE NOTICE 'Lasketaan tavoitehinta hoitokauden_vuosi: %, hoitokauden_nro: %', hoitokauden_vuosi, hoitokauden_nro;

            hoitovuoden_alun_indkorj_tavoitehinta :=
                COALESCE(
                    ( SELECT SUM(COALESCE(ut.tavoitehinta_indeksikorjattu, ut.tavoitehinta, 0))
                      FROM urakka_tavoite ut
                      WHERE ut.hoitokausi = hoitokauden_nro
                        AND ut.urakka = ur), 0
                ); -- Hoitovuoden alun indeksikorjattu tavoitehinta
            RAISE NOTICE 'Lasketaan hoitovuoden_alun_indkorj_tavoitehinta: %', hoitovuoden_alun_indkorj_tavoitehinta;
            hoitokauden_tavoitehinta := hoitokauden_tavoitehinta + hoitovuoden_alun_indkorj_tavoitehinta;

            -- Onko tavoitehintaa oikaistu 
            IF EXISTS (
              SELECT 1 
              FROM tavoitehinnan_oikaisu to2 
              WHERE to2."urakka-id" = ur 
                AND to2."hoitokauden-alkuvuosi" = hk_alkuvuosi 
                AND to2.poistettu = false
            ) THEN
              tavoitehinta_on_oikaistu := true;

              tavoitehinta_oikaisu_summa := COALESCE(
                  (SELECT SUM(to2.summa)
                   FROM tavoitehinnan_oikaisu to2
                   WHERE to2."urakka-id" = ur
                     AND to2."hoitokauden-alkuvuosi" = hk_alkuvuosi
                     AND to2.poistettu = false), 0);

              -- Lisää oikaistu määrä tavoitehintaan, oli sitten miinusta tai plussaa
              -- Tässä vaiheessa tämä ei ole enää hoitokauden alun tavoitehinta, vaan vanhoille -24 ja ennen urakoille, hoitokauden lopun tavoitehinta
              hoitokauden_tavoitehinta := hoitokauden_tavoitehinta + tavoitehinta_oikaisu_summa;

            ELSE 
              tavoitehinta_on_oikaistu := false;
            END IF;
        END IF;
    END LOOP;

    RAISE NOTICE '***TOTAL hoitokauden_tavoitehinta: %', hoitokauden_tavoitehinta;
    -------------------------

    -- Välikatselmuksesta voi siirtyä seuravaalle vuodelle maksettavia kuluja Kattohinnan ylityksestä tai kulujen vähennyksiä
    -- Tavoitehinnan alittamisesta.
    -- Tässä summataan siirretyt kulut yhteen ja ne otetaan huomioon jäljelläolevassa budjetissa alempana
    hk_valikatselmus_siirrot_ed_vuodelta := 0.0;
    hk_valikatselmus_siirrot_ed_vuodelta := hk_valikatselmus_siirrot_ed_vuodelta +
    (SELECT COALESCE(SUM(x.siirto), 0)
    FROM (SELECT COALESCE(SUM(pta.siirron_maara) * -1, 0) as siirto
          FROM paatos_tavoitehinta_alitus pta
          WHERE pta.urakkaid = ur
            AND pta.hoitokauden_alkuvuosi = (hk_alkuvuosi - 1)::INTEGER -- Haetaan edellisen vuoden päätöksestä
            AND pta.siirron_maara != 0
            AND pta.poistettu = FALSE
          UNION ALL
          SELECT COALESCE(SUM(pk.siirrettava_maara), 0) as siirto
          FROM paatos_kattohinta pk
          WHERE pk.urakkaid = ur
            AND pk.hoitokauden_alkuvuosi = (hk_alkuvuosi - 1)::INTEGER -- Haetaan edellisen vuoden päätöksestä
            AND pk.siirrettava_maara != 0
            AND pk.poistettu = FALSE) as x);

    RAISE NOTICE '*** hk_valikatselmus_siirrot_ed_vuodelta: % ', hk_valikatselmus_siirrot_ed_vuodelta;

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
    SELECT tpi.id
    FROM toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23104' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    INTO talvihoito_tpi_id;

    -- Liikenneymp. hoidon toimenpideinstanssin id
    SELECT tpi.id
    FROM toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23116' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    INTO lyh_tpi_id;

    -- Sorateiden hoidon toimenpideinstanssin id
    SELECT tpi.id
    FROM toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23124' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    INTO sora_tpi_id;

    -- Päällystepaikkaukset toimenpideinstanssin id
    SELECT tpi.id
    FROM toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '20107' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    INTO paallyste_tpi_id;

    -- MHU ylläpidon toimenpideinstanssin id
    SELECT tpi.id
    FROM toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '20191' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    INTO yllapito_tpi_id;

    -- Korvausinvestointien toimenpideinstanssin id
    SELECT tpi.id
    FROM toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '14301' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    INTO korvausinv_tpi_id;

    SELECT tpi.id
    FROM toimenpideinstanssi tpi
             JOIN toimenpide tpk on tpk.id = tpi.toimenpide AND tpk.koodi = '23151' AND tpk.taso = 3
    WHERE tpi.urakka = ur
    INTO hoidonjohto_tpi_id;

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
    muutos_erillis_hoitokausi_yht := 0.0;
    muutos_erillis_val_aika_yht := 0.0;
    jjh_muutos_hoitokausi_yht := 0.0;
    jjh_muutos_val_aika_yht := 0.0;
    muutostyo_val_aika_yht := 0.0;
    muutostyo_hoitokausi_yht := 0.0;

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

    FOR rivi IN SELECT
      summa         AS kht_summa, 
      l.erapaiva    AS erapaiva, 
      tpi.id        AS toimenpideinstanssi_id, 
      lk.maksueratyyppi, 
      lk.rahavaraus_id,
      tr.yksiloiva_tunniste,
      lk.tavoitehintainen,
      lk.tyyppi AS tyyppi
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
            WHERE lk.rahavaraus_id IS NULL -- Ei oteta tässä mukaan rahavarauksia, niihin kohdistetut kulut lasketaan erikseen
              AND lk.poistettu IS NOT TRUE
              AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
              AND lk.tyyppi != 'muukulu'

        LOOP

            -- Alusta hankitojen muuttujat, on tehtävä tässä muuten tulee virhettä
            SELECT NULL::numeric AS summa INTO muutokset_erillis_rivi;
            SELECT NULL::numeric AS summa INTO muutokset_jjh_rivi;
            SELECT NULL::numeric AS summa INTO talvihoito_rivi;
            SELECT NULL::numeric AS summa INTO lisatyo_talvihoito_rivi;
            SELECT NULL::numeric AS summa INTO lyh_rivi;
            SELECT NULL::numeric AS summa INTO lisatyo_lyh_rivi;
            SELECT NULL::numeric AS summa INTO sora_rivi;
            SELECT NULL::numeric AS summa INTO lisatyo_sora_rivi;
            SELECT NULL::numeric AS summa INTO paallyste_rivi;
            SELECT NULL::numeric AS summa INTO lisatyo_paallyste_rivi;
            SELECT NULL::numeric AS summa INTO yllapito_rivi;
            SELECT NULL::numeric AS summa INTO lisatyo_yllapito_rivi;
            SELECT NULL::numeric AS summa INTO korvausinv_rivi;
            SELECT NULL::numeric AS summa INTO lisatyo_korvausinv_rivi;
            SELECT NULL::numeric AS summa INTO lisatyo_hoidonjohto_rivi;

            rivi_on_erillis_muutos := rivi.tyyppi = 'erillisrahoitettu-muutos';
            rivi_on_jjh_muutos := rivi.tyyppi = 'jjh-muutos';

            RAISE NOTICE 'rivi: %', rivi;

            -- Kohdista talvihoitoon liittyvät rivit talvihoito_rivi:lle
            IF rivi.tavoitehintainen IS TRUE -- Talvihoito hankinnat ovat tavoitehintaisia
            AND rivi.toimenpideinstanssi_id = talvihoito_tpi_id 
            AND rivi.maksueratyyppi != 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO talvihoito_rivi;

                RAISE NOTICE 'talvihoito_rivi: % ', talvihoito_rivi;
                RAISE NOTICE 'talvihoito_rivi.summa: %', talvihoito_rivi.summa;
            END IF;

            -- Kohdista talvihoitoon liittyvät lisätyö rivit lisatyo_talvihoito:lle
            IF rivi.tavoitehintainen IS FALSE -- Lisätyöt eivät ole tavoitehintaisia 
            AND rivi.toimenpideinstanssi_id = talvihoito_tpi_id 
            AND rivi.maksueratyyppi = 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_talvihoito_rivi;

                RAISE NOTICE 'lisatyo_talvihoito_rivi: % ', lisatyo_talvihoito_rivi;
                RAISE NOTICE 'lisatyo_talvihoito_rivi.summa: %', lisatyo_talvihoito_rivi.summa;
            END IF;

            -- Kohdista Liikenneympäristön hoitoon liittyvät rivit lyh_rivi:lle
            IF rivi.tavoitehintainen IS TRUE -- Liikenneympäristön hoitoon liittyvät rivit ovat tavoitehintaisia
            AND rivi.toimenpideinstanssi_id = lyh_tpi_id 
            AND rivi.maksueratyyppi != 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lyh_rivi;

                RAISE NOTICE 'lyh_rivi: % ', lyh_rivi;
                RAISE NOTICE 'lyh_rivi.summa: %', lyh_rivi.summa;
            END IF;

            -- Kohdista Liikenneympäristön hoitoon liittyvät lisätyörivit lisatyo_lyh_rivi:lle
            IF rivi.tavoitehintainen IS FALSE -- Lisätyöt eivät ole tavoitehintaisia 
            AND rivi.toimenpideinstanssi_id = lyh_tpi_id 
            AND rivi.maksueratyyppi = 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_lyh_rivi;

                RAISE NOTICE 'lisatyo_lyh_rivi: % ', lisatyo_lyh_rivi;
                RAISE NOTICE 'lisatyo_lyh_rivi.summa: %', lisatyo_lyh_rivi.summa;
            END IF;

            -- Kohdista Soratien hoitoon liittyvät rivit sora_rivi:lle
            IF rivi.tavoitehintainen IS TRUE -- Soratien hoitoon liittyvät rivit ovat tavoitehintaisia
            AND rivi.toimenpideinstanssi_id = sora_tpi_id 
            AND rivi.maksueratyyppi != 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO sora_rivi;

                RAISE NOTICE 'sora_rivi: % ', sora_rivi;
                RAISE NOTICE 'sora_rivi.summa: %', sora_rivi.summa;
            END IF;

            -- Kohdista Soratien hoitoon liittyvät lisätyö rivit sora_rivi:lle
            IF rivi.tavoitehintainen IS FALSE -- Lisätyöt eivät ole tavoitehintaisia 
            AND rivi.toimenpideinstanssi_id = sora_tpi_id 
            AND rivi.maksueratyyppi = 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_sora_rivi;

                RAISE NOTICE 'lisatyo_sora_rivi: % ', lisatyo_sora_rivi;
                RAISE NOTICE 'lisatyo_sora_rivi.summa: %', lisatyo_sora_rivi.summa;
            END IF;

            -- Kohdista Päällysteiden paikkaukseen liittyvät rivit paallyste_rivi:lle
            IF rivi.tavoitehintainen IS TRUE -- Päällysteiden paikkaukseen liittyvät rivit ovat tavoitehintaisia
            AND rivi.toimenpideinstanssi_id = paallyste_tpi_id 
            AND rivi.maksueratyyppi != 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO paallyste_rivi;

                RAISE NOTICE 'paallyste_rivi: % ', paallyste_rivi;
                RAISE NOTICE 'paallyste_rivi.summa: %', paallyste_rivi.summa;
            END IF;

            -- Kohdista Päällysteiden paikkaukseen liittyvät lisätyö rivit paallyste_rivi:lle
            IF rivi.tavoitehintainen IS FALSE -- Lisätyöt eivät ole tavoitehintaisia 
            AND rivi.toimenpideinstanssi_id = paallyste_tpi_id 
            AND rivi.maksueratyyppi = 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_paallyste_rivi;

                RAISE NOTICE 'lisatyo_paallyste_rivi: % ', lisatyo_paallyste_rivi;
                RAISE NOTICE 'lisatyo_paallyste_rivi.summa: %', lisatyo_paallyste_rivi.summa;
            END IF;

            -- Kohdista MHU ylläpidon liittyvät rivit yllapito_rivi:lle
            IF rivi.tavoitehintainen IS TRUE
            AND rivi.toimenpideinstanssi_id = yllapito_tpi_id 
            AND rivi.maksueratyyppi != 'lisatyo' 
            AND rivi.rahavaraus_id IS NULL 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO yllapito_rivi;

                RAISE NOTICE 'yllapito_rivi: % ', yllapito_rivi;
                RAISE NOTICE 'yllapito_rivi.summa: %', yllapito_rivi.summa;
            END IF;

            -- Kohdista MHU ylläpidon liittyvät lisätyö rivit lisatyo_yllapito_rivi:lle
            IF rivi.tavoitehintainen IS FALSE -- Lisätyöt eivät ole tavoitehintaisia 
            AND rivi.toimenpideinstanssi_id = yllapito_tpi_id 
            AND rivi.maksueratyyppi = 'lisatyo' 
            AND rivi.rahavaraus_id IS NULL 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_yllapito_rivi;

                RAISE NOTICE 'lisatyo_yllapito_rivi: % ', lisatyo_yllapito_rivi;
                RAISE NOTICE 'lisatyo_yllapito_rivi.summa: %', lisatyo_yllapito_rivi.summa;
            END IF;

            -- Kohdista MHU korvausinvestointeihin liittyvät rivit korvausinv_rivi:lle
            IF rivi.tavoitehintainen IS TRUE
            AND rivi.toimenpideinstanssi_id = korvausinv_tpi_id 
            AND rivi.maksueratyyppi != 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO korvausinv_rivi;

                RAISE NOTICE 'korvausinv_rivi: % ', korvausinv_rivi;
                RAISE NOTICE 'korvausinv_rivi.summa: %', korvausinv_rivi.summa;
            END IF;

            -- Kohdista MHU korvausinvestointeihin liittyvät lisätyö rivit lisatyo_korvausinv_rivi:lle
            IF rivi.tavoitehintainen IS FALSE -- Lisätyöt eivät ole tavoitehintaisia 
            AND rivi.toimenpideinstanssi_id = korvausinv_tpi_id 
            AND rivi.maksueratyyppi = 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_korvausinv_rivi;

                RAISE NOTICE 'lisatyo_korvausinv_rivi: % ', lisatyo_korvausinv_rivi;
                RAISE NOTICE 'lisatyo_korvausinv_rivi.summa: %', lisatyo_korvausinv_rivi.summa;
            END IF;

            -- Kohdista MHU Hoidonjohto liittyvät lisätyö rivit lisatyo_hoidonjohto_rivi:lle
            IF rivi.tavoitehintainen IS FALSE -- Lisätyöt eivät ole tavoitehintaisia 
            AND rivi.toimenpideinstanssi_id = hoidonjohto_tpi_id 
            AND rivi.maksueratyyppi = 'lisatyo' 
            AND NOT rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO lisatyo_hoidonjohto_rivi;

                RAISE NOTICE 'lisatyo_hoidonjohto_rivi: % ', lisatyo_hoidonjohto_rivi;
                RAISE NOTICE 'lisatyo_hoidonjohto_rivi.summa: %', lisatyo_hoidonjohto_rivi.summa;
            END IF;

            
            -----------------------------------------------------
            -- Muutokset 
            IF rivi.tavoitehintainen IS TRUE 
            AND rivi_on_jjh_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO muutokset_jjh_rivi;

                RAISE NOTICE 'muutokset_jjh_rivi: % ', muutokset_jjh_rivi;
                RAISE NOTICE 'muutokset_jjh_rivi.summa: %', muutokset_jjh_rivi.summa;
            END IF;

            IF rivi.tavoitehintainen IS TRUE 
            AND rivi_on_erillis_muutos THEN
                SELECT rivi.kht_summa AS summa,
                       rivi.kht_summa AS korotettuna,
                       0::NUMERIC     AS korotus
                INTO muutokset_erillis_rivi;

                RAISE NOTICE 'muutokset_erillis_rivi: % ', muutokset_erillis_rivi;
                RAISE NOTICE 'muutokset_erillis_rivi.summa: %', muutokset_erillis_rivi.summa;
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

                -- MHU ylläpidon kulut, jotka eivät ole lisätöitä
                IF rivi.toimenpideinstanssi_id = yllapito_tpi_id AND rivi.maksueratyyppi != 'lisatyo' THEN

                    yllapito_hoitokausi_yht := yllapito_hoitokausi_yht + COALESCE(yllapito_rivi.summa, 0.0);
                    RAISE NOTICE 'rivi.erapaiva <= aikavali_loppupvm && yllapito_tpi  THEN: %', yllapito_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        yllapito_val_aika_yht := yllapito_val_aika_yht + COALESCE(yllapito_rivi.summa, 0.0);
                    END IF;
                END IF;

                -- MHU ylläpidon kulut, joka on lisätyö
                IF rivi.toimenpideinstanssi_id = yllapito_tpi_id AND rivi.maksueratyyppi = 'lisatyo' THEN

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

                -- Muutokset 
                IF rivi_on_erillis_muutos THEN
                    muutos_erillis_hoitokausi_yht := muutos_erillis_hoitokausi_yht + COALESCE(muutokset_erillis_rivi.summa, 0.0);
                    RAISE NOTICE 'muutos_erillis_hoitokausi_yht: %', muutos_erillis_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        muutos_erillis_val_aika_yht := muutos_erillis_val_aika_yht + COALESCE(muutokset_erillis_rivi.summa, 0.0);
                    END IF;
                END IF;


                IF rivi_on_jjh_muutos THEN
                    jjh_muutos_hoitokausi_yht := jjh_muutos_hoitokausi_yht + COALESCE(muutokset_jjh_rivi.summa, 0.0);
                    RAISE NOTICE 'jjh_muutos_hoitokausi_yht: %', jjh_muutos_hoitokausi_yht;

                    IF rivi.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm THEN
                        -- Laskutetaan nyt
                        jjh_muutos_val_aika_yht := jjh_muutos_val_aika_yht + COALESCE(muutokset_jjh_rivi.summa, 0.0);
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
                                        sopimus_id::INTEGER, FALSE));
    johtojahallinto_hoitokausi_yht := 0.0;
    johtojahallinto_val_aika_yht := 0.0;
    johtojahallinto_hoitokausi_yht := johtojahallinto_hoitokausi_yht + johtojahallinto_rivi.johto_ja_hallinto_laskutettu;
    johtojahallinto_val_aika_yht := johtojahallinto_val_aika_yht + johtojahallinto_rivi.johto_ja_hallinto_laskutetaan;

    RAISE NOTICE 'johtojahallinto_hoitokausi_yht: %', johtojahallinto_hoitokausi_yht;
    RAISE NOTICE 'johtojahallinto_val_aika_yht: %', johtojahallinto_val_aika_yht;

    -- HOIDONJOHTO --  erillishankinnat
    erillishankinnat_rivi :=
        (SELECT hj_erillishankinnat(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, '23150'::TEXT,
                                    hoidonjohto_tpi_id::INTEGER, ur::INTEGER));

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

    -- Hankinnat ja Hoidonjohto yhteensä 
    hankinnat_ja_hoidon_hk_yht := 0.0;
    hankinnat_ja_hoidon_val_yht := 0.0;

    hankinnat_ja_hoidon_val_yht := hankinnat_val_aika_yht + hoidonjohto_val_aika_yht;
    hankinnat_ja_hoidon_hk_yht := hankinnat_hoitokausi_yht + hoidonjohto_hoitokausi_yht;

    -----------------------------------------------------------
    -- Muutokset 
    muutostyo_val_aika_yht := muutos_erillis_val_aika_yht + jjh_muutos_val_aika_yht;
    muutostyo_hoitokausi_yht := muutos_erillis_hoitokausi_yht + jjh_muutos_hoitokausi_yht;

    -----------------------------------------------------------
    ------------------- Rahavaraukset -------------------------
    -----------------------------------------------------------

    FOR rahavaraus IN
        SELECT 
          rv.id, 
          COALESCE(rvu.urakkakohtainen_nimi, rv.nimi) AS nimi
        FROM rahavaraus rv 
        -- Näytetään vaan rahavaraukset mitkä urakalle asetettu (hallinta)
        JOIN rahavaraus_urakka rvu ON rv.id = rvu.rahavaraus_id  
        WHERE rvu.urakka_id = ur
        -- Sorttaa järjestysnumerolla, nämä tulee tässä järjestyksessä käyttöliittymään asti
        ORDER BY rv.jarjestys
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
                AND lk.tavoitehintainen IS TRUE
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

    RAISE NOTICE 'kaikki_rahavaraukset_hoitokausi_yht: %', kaikki_rahavaraukset_hoitokausi_yht;
    RAISE NOTICE 'kaikki_rahavaraukset_val_yht: %',kaikki_rahavaraukset_val_yht;

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
        LEFT JOIN tehtavaryhma tr ON lk.tehtavaryhma = tr.id
        -- Etsi pelkästään muukulu tyyppiset  kirjaukset, toimenpideinstansseilla ei ole näissä väliä 
        -- Tavoitehintaiset kuuluu tehtäväryhmälle, ei tavoitehintaiset kuuluu toimenpiteelle, mutta työmaakokouksessa ei tarvitse niputtaa
        WHERE lk.tyyppi = 'muukulu'
          AND lk.poistettu IS NOT TRUE
          AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
          AND l.urakka = ur
          -- J - Johto- ja hallintokorvaus huomioidaan myös muukulu-tyyppiseksi kirjattuna laskutusyhteenvedon Hoidon johto-osion Johto- ja hallintokorvaus-rivillä, joten karsitaan pois tässä.
          -- W - Erillishankinnat, myös omana rivinään, ei lasketa niitä tähän 
          AND ((tr.yksiloiva_tunniste IS NOT NULL 
                AND tr.yksiloiva_tunniste NOT IN ('a6614475-1950-4a61-82c6-fda0fd19bb54', '37d3752c-9951-47ad-a463-c1704cf22f4c')) 
              OR tr.yksiloiva_tunniste IS NULL)
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
    muut_kulut_hoitokausi_yht := muut_kulut_hoitokausi
        -- Otetaan mukaan muihin tavoitehintaisiin kuluihin myös kulujen siirrot edelliselta vuodelta
        -- Käsitellään siirrot kuitenkin omana rivinään laskutusyhteenvedossa, jotta ne erottuvat selkeästi muista kuluista
        + hk_valikatselmus_siirrot_ed_vuodelta;
    muut_kulut_val_aika_yht := muut_kulut_val_aika;

    -- Ei tavoitehintaiset yhteensä-  arvot lasketaan bonusten ja sanktioiden jälkeen alempana

    ---------------------------------------------------------------------------------
    ------------------- Pysyvät muutokset (mhu_muutos-taulusta) ---------------------
    ---------------------------------------------------------------------------------
    -- Haetaan muutokset mhu_muutos-taulusta ja jjh--muutokset kuluista
    -- Nämä ovat tavoitehintaan vaikuttavia muutoksia (pysyva, muutostyo, johto-ja-hallintokorvaus)

    pysyvat_muutokset_hoitokausi_yht := 0.0;
    pysyvat_muutokset_val_aika_yht := 0.0;
    pysyvat_muutokset_ed_hoitokausi := 0.0;

    -- Aktiiviset pysyvät muutokset ja muutostyöt valitun hoitokauden alusta
    SELECT COALESCE(SUM(mmk.summa), 0)
    INTO pysyvat_muutokset_hoitokausi_yht
    FROM mhu_muutos mm
             JOIN mhu_muutos_kustannusvaikutus mmk ON mmk.muutos = mm.id
    WHERE mm.urakka = ur
      AND mm.tyyppi IN ('pysyva'::MHU_MUUTOSTYYPPI, 'muutostyo'::MHU_MUUTOSTYYPPI)
      AND mm.poistettu IS FALSE
      AND mmk.hoitokauden_alkuvuosi = hk_alkuvuosi
      AND mm.voimassa_alkaen BETWEEN hk_alkupvm AND aikavali_loppupvm;

    -- -- Aktiiviset pysyvät muutokset ja muutokset valitulle aikajaksolle
    SELECT COALESCE(SUM(mmk.summa), 0)
    INTO pysyvat_muutokset_val_aika_yht
    FROM mhu_muutos mm
             JOIN mhu_muutos_kustannusvaikutus mmk ON mmk.muutos = mm.id
    WHERE mm.urakka = ur
      AND mm.tyyppi IN ('pysyva'::MHU_MUUTOSTYYPPI, 'muutostyo'::MHU_MUUTOSTYYPPI)
      AND mm.poistettu IS FALSE
      AND mmk.hoitokauden_alkuvuosi = hk_alkuvuosi
      AND mm.voimassa_alkaen BETWEEN aikavali_alkupvm AND aikavali_loppupvm;

    -- Edellisillä hoitokausilla merkityt pysyvät muutokset.
    -- Menneet pysyvät muutokset täytyy indeksikorjata, kun ne haetaan.
    SELECT indeksikorjaa((SELECT COALESCE(SUM(mmk.summa), 0)
    FROM mhu_muutos mm
             JOIN mhu_muutos_kustannusvaikutus mmk ON mmk.muutos = mm.id
    WHERE mm.urakka = ur
      AND mm.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI
      AND mm.voimassa_alkaen < (SELECT TO_DATE(hk_alkuvuosi || '-10-01', 'YYYY-MM-DD'))
      AND mmk.hoitokauden_alkuvuosi = hk_alkuvuosi
      AND mm.poistettu IS FALSE)::NUMERIC, hk_alkuvuosi::INT, 10::INT, ur::INT) INTO pysyvat_muutokset_ed_hoitokausi;

    RAISE NOTICE 'Pysyvät muutokset hoitokausi yhteensä: %', pysyvat_muutokset_hoitokausi_yht;
    RAISE NOTICE 'Pysyvät muutokset valittu aika yhteensä: %', pysyvat_muutokset_val_aika_yht;
    RAISE NOTICE 'Pysyvät muutokset edellisiltä hoitokausilta: %', pysyvat_muutokset_ed_hoitokausi;
    RAISE NOTICE 'muutostyo_hoitokausi_yht: %', muutostyo_hoitokausi_yht;

    ---------------------------------------------------------------------------------
    --------------- Tavoitehintaan vaikuttavat kustannukset yhteensä  ---------------
    ---------------------------------------------------------------------------------

    -- Laskeskellaan tavoitehintaan kuuluvat yhteen
    -- 2022-10-01 jälkeen alihankitabonus ei ole enää MHU ylläpitoon kuuluvana, vaan omana rivinään, niin iffitellään se tarvittaessa mukaan
    -- Tavoitehinta hoitokausi
    -- Tästä johdetaan myös "Tavoitehintaan vaikuttavat kustannukset yhteensä" -rivi Työmaakokous raporttiin. Nimet ovat siis ristiriitaisia.
    tavhin_hoitokausi_yht := 0.0;
    tavhin_hoitokausi_yht := tavhin_hoitokausi_yht +
            talvihoito_hoitokausi_yht + 
            lyh_hoitokausi_yht + 
            sora_hoitokausi_yht +
            paallyste_hoitokausi_yht + 
            yllapito_hoitokausi_yht + 
            korvausinv_hoitokausi_yht +
            johtojahallinto_hoitokausi_yht + 
            erillishankinnat_hoitokausi_yht + 
            hjpalkkio_hoitokausi_yht + 
            muutostyo_hoitokausi_yht +
            kaikki_rahavaraukset_hoitokausi_yht + 
            muut_kulut_hoitokausi_yht;
    
    -- Tavoitehinta valittu kk
    -- Nykyään tällä ei ole mitään tekemistä tavoitehinnan kanssa. Vaan tässä lasketaan yhteen kaikki kulut
    -- Näitä nimiä voisi joskus koittaa korjata.
    tavhin_val_aika_yht := 0.0;
    tavhin_val_aika_yht := tavhin_val_aika_yht + 
            talvihoito_val_aika_yht + 
            lyh_val_aika_yht + 
            sora_val_aika_yht +
            paallyste_val_aika_yht + 
            yllapito_val_aika_yht + 
            korvausinv_val_aika_yht +
            johtojahallinto_val_aika_yht +
            erillishankinnat_val_aika_yht + 
            hjpalkkio_val_aika_yht + 
            muutostyo_val_aika_yht +
            kaikki_rahavaraukset_val_yht + 
            muut_kulut_val_aika_yht +
            pysyvat_muutokset_val_aika_yht;

    -- Budjettia jäljellä
    budjettia_jaljella := 0.0;
    budjettia_jaljella := (budjettia_jaljella + hoitovuoden_alun_indkorj_tavoitehinta + -- Hoitokauden alun indeksikorjattu tavoitehinta saadaan suoraan tietokannasta
                           tavoitehinta_oikaisu_summa + -- Vanhemmilla urakoilla on oikaisuja
                           pysyvat_muutokset_hoitokausi_yht + muutostyo_hoitokausi_yht) -- Uudemmilla urakoilla on muutokset
                          - tavhin_hoitokausi_yht;

    RAISE NOTICE 'budjettia_jaljella: %', budjettia_jaljella;


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
                                 FROM erilliskustannuksen_indeksilaskenta(ek.laskutuskuukausi, ek.indeksin_nimi, ek.rahasumma,
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
    paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht := 0.0;
    paatos_hoidonjohtopalkkion_muutos_val_aika_yht := 0.0;
    FOR paatos_rivi IN SELECT summa AS summa, l.erapaiva AS erapaiva, tr.nimi as tehtavaryhma_nimi
                       FROM kulu l
                                JOIN kulu_kohdistus lk ON lk.kulu = l.id
                                JOIN toimenpideinstanssi tpi
                                     ON lk.toimenpideinstanssi = tpi.id AND tpi.id = hoidonjohto_tpi_id
                                JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma
                       WHERE lk.maksueratyyppi = 'kokonaishintainen'
                         AND lk.poistettu IS NOT TRUE
                         AND lk.tavoitehintainen = FALSE
                         AND l.urakka = ur
                         AND lk.tehtavaryhma in
                             (SELECT tr.id
                              FROM tehtavaryhma tr
                              WHERE tr.nimi ilike 'Hoitovuoden päättäminen%' OR tr.nimi = 'G - Hoidonjohtopalkkio')  -- Harmillisesti joutuu käyttämään nimeä, koska tyyppejä ei ole
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

            IF paatos_rivi.erapaiva <= aikavali_loppupvm AND
               paatos_rivi.tehtavaryhma_nimi =
               'G - Hoidonjohtopalkkio' THEN
                -- Hoitokauden alusta
                paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht :=
                    paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht + COALESCE(paatos_rivi.summa, 0.0);
                RAISE NOTICE 'paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht: %', paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht;

                IF paatos_rivi.erapaiva >= aikavali_alkupvm AND
                   paatos_rivi.erapaiva <= aikavali_loppupvm THEN
                    -- Laskutetaan nyt
                    paatos_hoidonjohtopalkkion_muutos_val_aika_yht :=
                        paatos_hoidonjohtopalkkion_muutos_val_aika_yht + COALESCE(paatos_rivi.summa, 0.0);
                END IF;
            END IF;

            RAISE NOTICE 'paatos_rivi: % ', paatos_rivi;
            RAISE NOTICE 'paatos_rivi.summa: %', paatos_rivi.summa;
        end loop;

    -- Muut kulut yhteensä, ei tavoitehintaiset
    muut_kulut_ei_tavoite_hoitokausi_yht := bonukset_hoitokausi_yht + 
                                            sanktiot_hoitokausi_yht + 
                                            muut_kulut_ei_tavoite_hoitokausi + 
                                            paatos_tavoitepalkkio_hoitokausi_yht + 
                                            paatos_tavoiteh_ylitys_hoitokausi_yht + 
                                            paatos_kattoh_ylitys_hoitokausi_yht +
                                            paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht;

    muut_kulut_ei_tavoite_val_aika_yht := bonukset_val_aika_yht + 
                                          sanktiot_val_aika_yht + 
                                          muut_kulut_ei_tavoite_val_aika +
                                          paatos_tavoitepalkkio_val_aika_yht + 
                                          paatos_tavoiteh_ylitys_val_aika_yht +
                                          paatos_kattoh_ylitys_val_aika_yht +
                                          paatos_hoidonjohtopalkkion_muutos_val_aika_yht;

    -- Tavoitehinnan ulkopuoliset kustannukset yhteensä
    muut_kustannukset_hoitokausi_yht := 0.0;
    muut_kustannukset_val_aika_yht := 0.0;

    muut_kustannukset_hoitokausi_yht :=
            muut_kustannukset_hoitokausi_yht + lisatyot_hoitokausi_yht + bonukset_hoitokausi_yht + sanktiot_hoitokausi_yht +
            paatos_tavoitepalkkio_hoitokausi_yht + paatos_tavoiteh_ylitys_hoitokausi_yht +
            paatos_kattoh_ylitys_hoitokausi_yht + paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht +
            -- Ei tavoitehintaiset muut kulut
            muut_kulut_ei_tavoite_hoitokausi;
            
    muut_kustannukset_val_aika_yht :=
            muut_kustannukset_val_aika_yht + lisatyot_val_aika_yht + bonukset_val_aika_yht + sanktiot_val_aika_yht +
            paatos_tavoitepalkkio_val_aika_yht + paatos_tavoiteh_ylitys_val_aika_yht +
            paatos_kattoh_ylitys_val_aika_yht + paatos_hoidonjohtopalkkion_muutos_val_aika_yht +
            -- Ei tavoitehintaiset muut kulut
            muut_kulut_ei_tavoite_val_aika;
    
    
    ---------------------------------
    --------- Laskutusraja ----------
    ---------------------------------
    
    laskutusraja_yht := 0.0;
    laskutusrajan_ylittynyt_yht := 0.0;
    laskutusraja_laskutettavaa_yht := 0.0;
    laskutusraja_laskutettavaa_val_aika := 0.0;

    laskutettavaa_kaikki_yht := 0.0;
    laskutettavaa_kaikki_val_aika := 0.0;

    -- Haetaan laskutusraja jos urakka on alkanut 2025 tai jälkeen
    IF urakan_alkuvuosi >= 2025 THEN
        RAISE NOTICE 'Haetaan laskutusraja urakan alkamisvuoden perusteella: %', urakan_alkuvuosi;
        SELECT laskutusraja_kaytossa FROM urakka_parametrit WHERE urakkaid = ur INTO onko_laskutusraja_kaytossa;
    ELSE
        onko_laskutusraja_kaytossa := FALSE;
    END IF;
    
    SELECT laskutusraja 
      FROM urakka_tavoite 
     WHERE urakka = ur 
       AND hoitokausi = (hk_alkuvuosi - urakan_alkuvuosi + 1) 
      INTO laskutusraja_yht;

    IF onko_laskutusraja_kaytossa THEN
        ------------------------------------------------------
        -- "josta laskutettavaa" valittu kk 
        IF tavhin_val_aika_yht >= laskutusraja_yht THEN
            laskutusraja_laskutettavaa_val_aika := laskutusraja_yht;
            
            -- Ylityksen määrä valittu kk
            laskutusrajan_ylittynyt_val_aika := tavhin_val_aika_yht - laskutusraja_yht;
        ELSE
            laskutusraja_laskutettavaa_val_aika := tavhin_val_aika_yht;
            -- Hoitokausi yht on tähän kuuhun asti olevat kulut
            laskutusrajan_ylittynyt_val_aika := greatest(tavhin_hoitokausi_yht - laskutusraja_yht, 0);
        END IF;

        ------------------------------------------------------
        -- "josta laskutettavaa" hoitokausi yht 
        IF tavhin_hoitokausi_yht >= laskutusraja_yht THEN
            laskutusraja_laskutettavaa_yht := laskutusraja_yht;
            
            -- Ylityksen määrä yhteensä
            laskutusrajan_ylittynyt_yht := tavhin_hoitokausi_yht - laskutusraja_yht;
        ELSE
            laskutusraja_laskutettavaa_yht := tavhin_hoitokausi_yht;
        END IF;

        laskutusraja_yht := greatest(laskutusraja_yht, 0.0);
        laskutusrajaan_jaljella := greatest(0.0, laskutusraja_yht - tavhin_hoitokausi_yht); 
        onko_laskutusraja_ylittynyt := (laskutusrajan_ylittynyt_val_aika > 0.0 OR laskutusrajan_ylittynyt_yht > 0.0);

        laskutettavaa_kaikki_yht := laskutusraja_laskutettavaa_yht + muut_kustannukset_hoitokausi_yht;
        laskutettavaa_kaikki_val_aika := laskutusraja_laskutettavaa_val_aika + muut_kustannukset_val_aika_yht;
    END IF;

    
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
        -- Muutokset 
              muutostyo_val_aika_yht,  muutostyo_hoitokausi_yht,
              muutos_erillis_hoitokausi_yht,  muutos_erillis_val_aika_yht,
              jjh_muutos_hoitokausi_yht, jjh_muutos_val_aika_yht,
        -- Hankinnat ja Hoidonjohto yhteensä
              hankinnat_ja_hoidon_hk_yht, hankinnat_ja_hoidon_val_yht,
        -- Tavoitehinnat yht.
              tavhin_hoitokausi_yht, tavhin_val_aika_yht,
        -- Tavoitehinnan muodostus
              hoitovuoden_alun_indkorj_tavoitehinta,
              hoitokauden_tavoitehinta,
              tavoitehinta_on_oikaistu,
              tavoitehinta_oikaisu_summa,
              hk_valikatselmus_siirrot_ed_vuodelta,
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
        -- Hoidonjohtopalkkion muutos
              paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht, paatos_hoidonjohtopalkkion_muutos_val_aika_yht,
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
              muut_kulut_ei_tavoite_hoitokausi_yht, muut_kulut_ei_tavoite_val_aika_yht,
        -- Laskutusraja
              laskutusraja_yht, laskutusrajaan_jaljella,
              onko_laskutusraja_kaytossa, onko_laskutusraja_ylittynyt,
              laskutusraja_laskutettavaa_yht, laskutusraja_laskutettavaa_val_aika,
              laskutusrajan_ylittynyt_yht, laskutusrajan_ylittynyt_val_aika,
              laskutettavaa_kaikki_yht, laskutettavaa_kaikki_val_aika,
        -- Pysyvät muutokset
              pysyvat_muutokset_hoitokausi_yht,
              pysyvat_muutokset_val_aika_yht,
              pysyvat_muutokset_ed_hoitokausi

        );
    return next tulos;
END;
$$;
