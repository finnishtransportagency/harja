-- Ennenkuin tehdään muutoksia kulu, kulu_kohdistus, kustannusarvioitu_tyo,
-- johto_ja_hallintokorvaus tai toteutuneet_kustannukset -tauluihin, niin otetaan niistä varmuuskopio lähes samalla nimellä
-- Aja nämä tuotantoon, ennen tuotantoonvientiä
--CREATE TABLE kulu_kopio AS TABLE kulu; -- n. 2 sekuntia
--CREATE TABLE kulu_kohdistus_kopio AS TABLE kulu_kohdistus; -- n. 0.5 sekuntia
--CREATE TABLE kustannusarvioitu_tyo_kopio AS TABLE kustannusarvioitu_tyo; -- n. 0.2 sekuntia
--CREATE TABLE johto_ja_hallintokorvaus_kopio AS TABLE johto_ja_hallintokorvaus; -- n. 0.3 sekuntia
--CREATE TABLE toteutuneet_kustannukset_kopio AS TABLE toteutuneet_kustannukset; -- n. 0.2 sekuntia


-- Poistetaan turhaksi jääneitä kolumneita kulu ja kulu_kohdistus tauluista
ALTER TABLE kulu
    DROP COLUMN IF EXISTS tyyppi;
-- Tyypin voi poistaa, koska kaikki on tyyppiä 'laskutettava'

-- Kululla voi olla monta kohdistusta ja niiden tyyppi on helpointa hallita kohdistuksessa itsessään
CREATE TYPE kohdistustyyppi AS ENUM ('rahavaraus', 'hankintakulu','muukulu', 'lisatyo', 'paatos');

-- Asetetaan defaultiksi useimmin käytössäoleva hankintakulu.
-- Lopulliset tyypit tulee, kun rahavarausten korjaava systeemi ajetaan kantaan
ALTER TABLE kulu_kohdistus
    ADD COLUMN IF NOT EXISTS tyyppi           kohdistustyyppi DEFAULT 'hankintakulu' NOT NULL,
    ADD COLUMN IF NOT EXISTS tavoitehintainen BOOLEAN         DEFAULT TRUE           NOT NULL,
    DROP COLUMN IF EXISTS suoritus_alku,
    DROP COLUMN IF EXISTS suoritus_loppu;
-- Suoritusajat voi poistaa, koska ne ovat aina samat kuin kulu.erapaiva

-- Päivitetään kulu_kohdistus taulun tyyppi lisatyoksi, jos maksueratyyppi on lisatyo
-- Kaikki lisätyöt, mitä tietokannassa on alunperin on myös ei tavoitehintaisia
UPDATE kulu_kohdistus
   SET tyyppi           = 'lisatyo',
       tavoitehintainen = FALSE
 WHERE maksueratyyppi = 'lisatyo';

--== Päivitetään kulu_kohdistus taulun paatokset ei tavoitehintaisiksi
DO
$$
    DECLARE
        tavoitepalkkioid      INT;
        tavoitehinnanylitysid INT;
        kattohinnanylitysid   INT;
    BEGIN

        tavoitepalkkioid := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio');
        tavoitehinnanylitysid := (SELECT id
                                    FROM tehtavaryhma
                                   WHERE
                                       nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä');
        kattohinnanylitysid := (SELECT id
                                  FROM tehtavaryhma
                                 WHERE nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä');

        -- Kaikki vuoden päättämisen kulut on ei tavoitehintaisia
        UPDATE kulu_kohdistus
           SET tyyppi           = 'paatos',
               tavoitehintainen = FALSE
         WHERE tehtavaryhma IN (tavoitepalkkioid, tavoitehinnanylitysid, kattohinnanylitysid);

    END
$$;


-- Haluttiin lisätä luoja toteutuneet_kustannukset tauluun, johon lisätään tavaraa pelkästään järjestelmän kautta
-- Pelkästään järjestelmä kutsuu tätä ajastetulla kyselyllä: siirra-budjetoidut-tyot-toteutumiin
-- Jotenka tässä korvattu funktiot tuolla luoja ID:llä, onko tämä tarpeellista ja antaako mitään arvoa, en tiedä, tässä tämä on kuitenkin


-- Lisää luoja kolumni toteutuneisiin kustannuksiin
ALTER TABLE toteutuneet_kustannukset
    ADD COLUMN luoja INTEGER REFERENCES kayttaja (id);

-- Aseta luoja integraatioksi
UPDATE toteutuneet_kustannukset
   SET luoja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');

-- Rahavaraus id:n lisäys ja populointi --
CREATE OR REPLACE FUNCTION populoi_rahavaraus_idt()
    RETURNS INTEGER AS
$$
DECLARE
    -- Rahavarausidt
    rv_vahingot_id            INT;
    rv_akilliset_id           INT;
    rv_tunneli_id             INT;
    rv_lupaukseen1_id         INT;
    rv_muut_tavoitehintaan_id INT;

    -- tehtavaidt
    t_tunneli_id              INT;
    t_lupaukseen1_id          INT;
    t_muut_tavoitehintaan_id  INT;

    -- tehtäväryhmäidt
    tr_lupaus1_id             INT;
    tr_muut_yllapito_id       INT;
    rivit_paivitetty          INTEGER := 0;
    puuttuva_rivi             RECORD;

BEGIN
    -- Haetaan rahavarausten id:t
    SELECT id INTO rv_akilliset_id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt';
    SELECT id INTO rv_vahingot_id FROM rahavaraus WHERE nimi = 'Vahinkojen korjaukset';
    SELECT id INTO rv_tunneli_id FROM rahavaraus WHERE nimi = 'Tunneleiden hoito';
    SELECT id INTO rv_lupaukseen1_id FROM rahavaraus WHERE nimi ILIKE '%Tilaajan rahavaraus kannustinjärjestelmään%';
    SELECT id
      INTO rv_muut_tavoitehintaan_id
      FROM rahavaraus
     WHERE nimi ILIKE '%Muut tavoitehintaan vaikuttavat rahavaraukset%';

    -- Haetaan tehtävien id:t
    SELECT id INTO t_tunneli_id FROM tehtava WHERE nimi ILIKE '%Tunneleiden hoito%';
    SELECT id INTO t_lupaukseen1_id FROM tehtava WHERE nimi ILIKE '%Tilaajan rahavaraus lupaukseen 1%';
    SELECT id INTO t_muut_tavoitehintaan_id FROM tehtava WHERE nimi ILIKE '%Muut tavoitehintaan%';

    -- Haetaan Tehtäväryhmien idt
    SELECT id INTO tr_lupaus1_id FROM tehtavaryhma WHERE nimi ILIKE 'Tilaajan rahavaraus (T3)';
    SELECT id INTO tr_muut_yllapito_id FROM tehtavaryhma WHERE nimi ILIKE '%Muut, MHU ylläpito (F)%';

    -- ~ ~ toteutuneet_kustannukset ~ ~ --

    -- Äkilliset hoitotyöt
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = rv_akilliset_id
     WHERE tyyppi = 'akillinen-hoitotyo'
       AND rv_akilliset_id IS NOT NULL;

    -- Vahinkojen korvaukset
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = rv_vahingot_id
     WHERE tyyppi = 'vahinkojen-korjaukset'
       AND rv_vahingot_id IS NOT NULL;

    -- ~ ~ kulu_kohdistus ~ ~ --
    -- Äkilliset hoitotyöt
    UPDATE kulu_kohdistus
       SET rahavaraus_id = rv_akilliset_id,
           tyyppi        = 'rahavaraus'
     WHERE maksueratyyppi = 'akillinen-hoitotyo'
       AND rv_akilliset_id IS NOT NULL;

    -- Maksuerätyyppi 'muu', luetaan laskutusyhteenvedeossa Vahinkojen korvauksena
    UPDATE kulu_kohdistus
       SET rahavaraus_id = rv_vahingot_id,
           tyyppi        = 'rahavaraus'
     WHERE maksueratyyppi = 'muu'
       AND rv_vahingot_id IS NOT NULL;

    --  Muut, MHU ylläpito (F) - Kulut rahavarauksiin -- Näitä ei ole. Kaikki 'muu' tyyppiset on vahingonkorvauksia

    -- Kun tehtäväryhmä on Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään (T3) - siitä tehdään kannustinjärjestelmä rahavaraus
    UPDATE kulu_kohdistus
       SET rahavaraus_id = rv_lupaukseen1_id,
           tyyppi        = 'rahavaraus'
     WHERE tehtavaryhma = tr_lupaus1_id
       AND rv_lupaukseen1_id IS NOT NULL;


    -- ~ ~ kustannusarvioitu_tyo ~ ~ --
    -- Äkilliset hoitotyöt
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_akilliset_id,
           osio          = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'akillinen-hoitotyo'
       AND rv_akilliset_id IS NOT NULL;

    -- Vahinkojen korvaukset
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_vahingot_id,
           osio          = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'vahinkojen-korjaukset'
       AND rv_vahingot_id IS NOT NULL;

    -- muut-rahavaraukset -- tunnelien hoito
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_tunneli_id,
           osio          = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'muut-rahavaraukset'
       AND tehtava = t_tunneli_id
       AND rv_tunneli_id IS NOT NULL;

    -- muut-rahavaraukset -- tehtävä: Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_lupaukseen1_id,
           osio          = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'muut-rahavaraukset'
       AND tehtava = t_lupaukseen1_id
       AND rv_lupaukseen1_id IS NOT NULL;

    -- muut-rahavaraukset -- tehtävä: Muut tavoitehintaan vaikuttavat rahavaraukset
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_muut_tavoitehintaan_id,
           osio          = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'muut-rahavaraukset'
       AND tehtava = t_muut_tavoitehintaan_id
       AND rv_muut_tavoitehintaan_id IS NOT NULL;

    -- Tehdään ja ajetaan funktio, joka päivittää tarvittavat rahavaraukset kustannusarvioitu_tyo taulun tehtävien perusteella
    FOR puuttuva_rivi IN SELECT DISTINCT ON (CONCAT(s.urakka, kt.rahavaraus_id)) CONCAT(s.urakka, kt.rahavaraus_id),
                                                                                 s.urakka AS urakka_id,
                                                                                 kt.rahavaraus_id,
                                                                                 ru.rahavaraus_id
                           FROM kustannusarvioitu_tyo kt
                                    JOIN sopimus s ON s.id = kt.sopimus
                                    LEFT JOIN rahavaraus_urakka ru
                                              ON ru.urakka_id = s.urakka AND ru.rahavaraus_id = kt.rahavaraus_id
                          WHERE ru.rahavaraus_id IS NULL
                            AND kt.rahavaraus_id IS NOT NULL
                            AND kt.summa IS NOT NULL
        LOOP
            INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
            VALUES (puuttuva_rivi.urakka_id, puuttuva_rivi.rahavaraus_id,
                    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
            RAISE NOTICE 'Lisätty rahavaraus % urakalle %', puuttuva_rivi.rahavaraus_id, puuttuva_rivi.urakka_id;
        END LOOP;


    -- Palauta pävittyneet rivit, debuggausta varten
    GET DIAGNOSTICS rivit_paivitetty = ROW_COUNT;
    RETURN rivit_paivitetty;
END;
$$ LANGUAGE plpgsql;

-- Ja tehdään päivitys samalla
SELECT populoi_rahavaraus_idt();

-- Kaikki urakat, joilla on "Muut tavoitehintaan vaikuttavat rahavaraukset" -rahavaraus kustannusarvioitu_tyo taulussa
-- ei saa enää tulevaisuudessa käyttää tuota rahavarausta. Näissä urakoissa on otettava käyttöön rahavaraukset
-- "Varalaskupaikat" ja "Pysäkkikatosten korjaaminen". Rahoja ei näiden välillä siirretä. Se on urakanvalvojan homma
-- Mutta alustetaan nuo tarvittavat rahavaraukset kuitenkin
DO
$$
    DECLARE
        urakat                  RECORD;
        muut_rahavaraus_id      INTEGER;
        pysakki_rahavaraus_id   INTEGER;
        varalasku_rahavaraus_id INTEGER;

    BEGIN

        -- Haetaan 'Tilaajan rahavaraus kannustinjärjestelmään' rahavarauksen id
        SELECT id INTO muut_rahavaraus_id FROM rahavaraus WHERE nimi = 'Muut tavoitehintaan vaikuttavat rahavaraukset';
        SELECT id INTO varalasku_rahavaraus_id FROM rahavaraus WHERE nimi = 'Varalaskupaikat';
        SELECT id INTO pysakki_rahavaraus_id FROM rahavaraus WHERE nimi = 'Pysäkkikatosten korjaaminen';

        FOR urakat IN SELECT DISTINCT s.urakka AS urakka_id, kt.rahavaraus_id
                        FROM kustannusarvioitu_tyo kt
                                 JOIN sopimus s ON s.id = kt.sopimus
                       WHERE kt.rahavaraus_id = muut_rahavaraus_id
            LOOP
                INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
                VALUES (urakat.urakka_id, pysakki_rahavaraus_id,
                        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

                INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
                VALUES (urakat.urakka_id, varalasku_rahavaraus_id,
                        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

            END LOOP;

    END
$$ LANGUAGE plpgsql;

-- Päivitetään kulu_kohdistus taulun tyyppi rahavaraukseksi, jos rahavaraus_id on asetettu
UPDATE kulu_kohdistus
   SET tyyppi = 'rahavaraus'
 WHERE rahavaraus_id IS NOT NULL;

-- Muutetaan 'Muut päällysteiden paikkaukseen liittyvät työt' tehtävän emo
-- 'Liikenneympäristön hoito' -> 'Päällystepaikkaukset'
-- Nyt hankintakulua kirjatessa kulu tulee oikean toimenpideinstanssin alle
-- sekä laskutusyhteenvedossa 'Päällysteiden paikkaukset' laariin

UPDATE tehtava
   SET emo       = (SELECT id FROM toimenpide WHERE koodi = '20107'),
       muokattu  = CURRENT_TIMESTAMP,
       muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
 WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt'
   AND emo = (SELECT id FROM toimenpide WHERE koodi = '23116');

-- '23116' 'Liikenneympäristön hoito'
-- '20107' 'Päällystepaikkaukset'


-- Tämän yllä olevan päivityksen myötä meidän pitää nyt päivittää kulu_kohdistus taulun toimenpideinstanssit,
-- eli kaikki vanhat kirjatut kulut tuolla vanhalla emolla sisältää nyt väärät toimenpideinstanssit.
--
-- Tässä tehdään nuo toimenpideinstanssi kytkökset oikein, jonka jälkeen kulut näytetään oikein sekä niitä pystytään muokata
--
-- Päivittää siis 'Liikenneympäristön hoito -> Päällysteiden paikkaukset'
-- -> Päällysteiden paikkaus (hoidon ylläpito)	Päällysteiden paikkaus, muut työt (Y8)

DO
$$
    DECLARE
        urakka_id             INTEGER;
        tehtavaryhma_id       INTEGER;
        tpi_paallystepaikkaus INTEGER;
        tpi_liikenneymparisto INTEGER;
    BEGIN
        RAISE NOTICE '***********************************************';
        RAISE NOTICE 'Ajetaan migraatio päällystyskytkös aika: %', CURRENT_TIMESTAMP;

        -- Hae tehtäväryhmä missä virhe
        SELECT id
          INTO tehtavaryhma_id
          FROM tehtavaryhma t
         WHERE t.nimi = 'Päällysteiden paikkaus, muut työt (Y8)';

        -- Looppaa kaikki teiden-hoito urakat
        FOR urakka_id IN
            SELECT id FROM urakka WHERE tyyppi = 'teiden-hoito'
            LOOP
                -- Etsi urakan toimenpideinstanssi päällystepaikkaukselle
                SELECT tpi.id
                  INTO tpi_paallystepaikkaus
                  FROM toimenpideinstanssi tpi
                           JOIN toimenpide tp ON tpi.toimenpide = tp.id
                 WHERE tp.koodi = '20107' -- '20107' 'Päällystepaikkaukset'
                   AND tpi.urakka = urakka_id;

                -- Etsi urakan toimenpideinstanssi liikenneympäristön hoidolle
                SELECT tpi.id
                  INTO tpi_liikenneymparisto
                  FROM toimenpideinstanssi tpi
                           JOIN toimenpide tp ON tpi.toimenpide = tp.id
                 WHERE tp.koodi = '23116' -- '23116' 'Liikenneympäristön hoito'
                   AND tpi.urakka = urakka_id;

                -- Katsotaan ensin että urakalla on molemmat instanssit, sekä tehtäväryhmä olemassa
                IF tpi_paallystepaikkaus IS NOT NULL
                    AND tpi_liikenneymparisto IS NOT NULL
                    AND tehtavaryhma_id IS NOT NULL THEN
                    -- Instanssit on löytynyt, päivitä se kulu_kohdistukseen mikäli kirjauksia tälle on
                    RAISE NOTICE 'Päivitetään uusi instanssi urakalle: % pp id: % ly id: % tehtäväryhmä_id: %',
                        urakka_id, tpi_paallystepaikkaus, tpi_liikenneymparisto, tehtavaryhma_id;

                    -- Päivitetään rivejä mikäli niitä on
                    UPDATE kulu_kohdistus
-- Aseta uusi toimenpideinstanssi 'Päällystepaikkaukset'
                       SET toimenpideinstanssi = tpi_paallystepaikkaus,
                           muokattu            = CURRENT_TIMESTAMP,
                           muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
-- Missä tehtäväryhmä on päällysteiden paikkaus
                     WHERE tehtavaryhma = tehtavaryhma_id
                       -- Missä urakan toimenpideinstanssi on vanha '23116' 'Liikenneympäristön hoito'
                       AND toimenpideinstanssi = (
                         tpi_liikenneymparisto
                         );
                ELSE
                    -- RAISE NOTICE 'Ei löytynyt tietoja urakalle: % tehtavaryhma: % - pp id: % ly id: %',
                    --    urakka_id, tehtavaryhma_id, tpi_paallystepaikkaus, tpi_liikenneymparisto;
                END IF;
            END LOOP;
    END
$$;

-- TL;DR
-- Korjataan 'Muut päällysteiden paikkaukseen liittyvät työt' tehtävän emo,
-- ja korjataan samalla instanssit kulu_kohdistus tauluun kirjatut kulut tälle tehtävälle

-- Päivitetään vielä maksuera taulu näille instansseille jotta lähtevät uudelleen sampoon
-- '23116' 'Liikenneympäristön hoito'
-- '20107' 'Päällystepaikkaukset'
UPDATE maksuera m
   SET likainen = TRUE
  FROM toimenpideinstanssi tpi
           JOIN toimenpide tp ON tpi.toimenpide = tp.id
           JOIN urakka u ON tpi.urakka = u.id
 WHERE m.toimenpideinstanssi = tpi.id
   AND tp.koodi IN ('23116', '20107');


-- Merkitään rahavarausosiot vahvistetuiksi, jos hankintakulut on vahvistettu ja jos Muut tavoitehintaan vaikuttavat rahavaraukset ei ole valittuna
DO
$$
    DECLARE
        rv_muut_tavoitehintaan_id INTEGER;
        rivi                      RECORD;
        rahavaraus_rivi           RECORD;
        alkuvuosi                 INTEGER;
        loppuvuosi                INTEGER;
        suunnitelmatila           RECORD;
    BEGIN
        -- Haetaan 'Muut tavoitehintaan vaikuttavat rahavaraukset' rahavarauksen id
        SELECT id
          INTO rv_muut_tavoitehintaan_id
          FROM rahavaraus
         WHERE nimi ILIKE '%Muut tavoitehintaan vaikuttavat rahavaraukset%';

        -- urakat ja vuodet, joiden hankintakustannukset on vahvistettu
        FOR rivi IN
            SELECT skt.hoitovuosi, skt.urakka, u.alkupvm, u.loppupvm, s.id AS sopimus
              FROM suunnittelu_kustannussuunnitelman_tila skt
                       JOIN urakka u ON skt.urakka = u.id
                       JOIN sopimus s ON skt.urakka = s.urakka
             WHERE skt.osio = 'hankintakustannukset'
               AND skt.vahvistettu IS TRUE
             ORDER BY skt.urakka, skt.hoitovuosi

            LOOP
                alkuvuosi = EXTRACT(YEAR FROM rivi.alkupvm) - 1 + rivi.hoitovuosi;
                loppuvuosi = EXTRACT(YEAR FROM rivi.alkupvm) + rivi.hoitovuosi;

                RAISE NOTICE '----------------------------------------------------------------';
                RAISE NOTICE 'Löydettiin vahvistetut hankintakustannukset urakalle: % hoitovuodelle: % alkuvuosi: %, loppuvuosi: %',
                    rivi.urakka, rivi.hoitovuosi, alkuvuosi, loppuvuosi;
                -- Onko urakalla jo rahavaraus 'Muut tavoitehintaan vaikuttavat rahavaraukset' valittuna
                SELECT *
                  INTO rahavaraus_rivi
                  FROM rahavaraus_urakka ru
                 WHERE ru.rahavaraus_id = rv_muut_tavoitehintaan_id
                   AND ru.urakka_id = rivi.urakka;

                RAISE NOTICE 'Rahavaraus_rivi: %', rahavaraus_rivi;

                -- Jos rahavarausta ei ole, niin voidaan vahvistaa osio
                CASE
                    WHEN rahavaraus_rivi IS NULL
                        THEN RAISE NOTICE 'Merkitään vahvistetuksi kustannusarvioitu_työ -tauluun urakalle % hoitovuodelle %, alkuvuosi: %, loppuvuosi: %', rivi.urakka, rivi.hoitovuosi, alkuvuosi, loppuvuosi;
                        -- Merkitään jokainen rahavarausrivi vahvistetuksi kustaannusarvioitu_tyo tauluun
                             UPDATE kustannusarvioitu_tyo
                                SET indeksikorjaus_vahvistettu = NOW()
                              WHERE sopimus = rivi.sopimus
                                AND osio = 'tavoitehintaiset-rahavaraukset'
                                AND (
                                  (vuosi = alkuvuosi AND kuukausi > 9)
                                      OR
                                  (vuosi = loppuvuosi AND kuukausi < 10));

                        -- Merkitään itse osio vahvistetuksi
                        -- Mutta varmistetaan, että onko sillä vielä tilaa olemassa
                             SELECT *
                               INTO suunnitelmatila
                               FROM suunnittelu_kustannussuunnitelman_tila
                              WHERE urakka = rivi.urakka
                                AND hoitovuosi = rivi.hoitovuosi
                                AND osio = 'tavoitehintaiset-rahavaraukset';

                             RAISE NOTICE 'Suunnitelmatila: %', suunnitelmatila;
                             CASE
                                 WHEN suunnitelmatila IS NULL
                                     THEN RAISE NOTICE 'Lisätään vahvistus suunnittelu_kustannussuunnitelman_tila urakka %, hoitovuosi: %, alkuvuosi: %, loppuvuosi: %',
                                              rivi.urakka, rivi.hoitovuosi, alkuvuosi, loppuvuosi;
                                          INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, hoitovuosi, osio, vahvistettu, luoja, luotu)
                                          VALUES (rivi.urakka, rivi.hoitovuosi, 'tavoitehintaiset-rahavaraukset', TRUE,
                                                  (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

                                 ELSE RAISE NOTICE 'Suunnitelmatila ei ole null, joten ajetaan päivitys';
                                      UPDATE suunnittelu_kustannussuunnitelman_tila
                                         SET vahvistettu = TRUE,
                                             muokkaaja   = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
                                             muokattu    = NOW()
                                       WHERE urakka = rivi.urakka
                                         AND hoitovuosi = rivi.hoitovuosi
                                         AND osio = 'tavoitehintaiset-rahavaraukset';
                                 END CASE;
                    ELSE RAISE NOTICE 'Ei vahvisteta urakalle % hoitovuodelle %, alkuvuosi: %, loppuvuosi: % , rahavarus_id: %, rahavaraus_urakka_id: %',
                        rivi.urakka, rivi.hoitovuosi, alkuvuosi, loppuvuosi, rv_muut_tavoitehintaan_id, rahavaraus_rivi.id;
                    END CASE;
            END LOOP;
    END
$$;
