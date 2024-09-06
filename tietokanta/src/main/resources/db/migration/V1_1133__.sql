-- Poistetaan turhaksi jääneitä kolumneita kulu ja kulu_kohdistus tauluista
ALTER TABLE kulu
    DROP COLUMN IF EXISTS tyyppi;
-- Tyypin voi poistaa, koska kaikki on tyyppiä 'laskutettava'

-- Poistetaan turhaksi jäänyt laskutustyyppi
DROP TYPE IF EXISTS laskutyyppi;

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

-- Päivitetään kulu_kohdistus taulun tyyppi rahavaraukseksi, jos rahavaraus_id on asetettu
UPDATE kulu_kohdistus
   SET tyyppi = 'rahavaraus'
 WHERE rahavaraus_id IS NOT NULL;

-- Päivitetään kulu_kohdistus taulun tyyppi lisatyoksi, jos maksueratyyppi on lisatyo
-- Kaikki lisätyöt, mitä tietokannassa on alunperin on myös ei tavoitehintaisia
UPDATE kulu_kohdistus
   SET tyyppi           = 'lisatyo',
       tavoitehintainen = FALSE
 WHERE kulu_kohdistus.maksueratyyppi = 'lisatyo';

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


-- Lisätään uusi suunnittelu_osio kustannusten suunnitteluun
ALTER TYPE SUUNNITTELU_OSIO ADD VALUE 'tavoitehintaiset-rahavaraukset';

-- Haluttiin lisätä luoja toteutuneet_kustannukset tauluun, johon lisätään tavaraa pelkästään järjestelmän kautta
-- Pelkästään järjestelmä kutsuu tätä ajastetulla kyselyllä: siirra-budjetoidut-tyot-toteutumiin
-- Jotenka tässä korvattu funktiot tuolla luoja ID:llä, onko tämä tarpeellista ja antaako mitään arvoa, en tiedä, tässä tämä on kuitenkin


-- Lisää luoja kolumni toteutuneisiin kustannuksiin
ALTER TABLE toteutuneet_kustannukset
    ADD COLUMN luoja INTEGER REFERENCES kayttaja (id);

-- Aseta luoja integraatioksi
UPDATE toteutuneet_kustannukset
   SET luoja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');

-- Korvaa olemassa olevat funktiot joissa luoja id läsnä
-- Ref ( 1018.sql )
CREATE OR REPLACE FUNCTION siirra_budjetoidut_tyot_toteumiin(pvm DATE) RETURNS VOID AS
$$
BEGIN
    -- Automaattisesti toteumaksi lasketaan tehtäväryhmä: Erillishankinnat (W)
    -- Ja tehtävät: Toimistotarvike- ja ICT-kulut, Hoitourakan työnjohto sekä Hoidonjohtopalkkio
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu,
                                          tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luoja, luotu, rivin_tunnistin)
    SELECT k.vuosi,
           k.kuukausi,
           k.summa,
           k.summa_indeksikorjattu,
           k.indeksikorjaus_vahvistettu,
           k.tyyppi,
           k.tehtava,
           k.tehtavaryhma,
           k.toimenpideinstanssi,
           k.sopimus,
           (SELECT s.urakka FROM sopimus s WHERE s.id = k.sopimus) AS "urakka-id",
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
           NOW(),
           MD5(CONCAT(k.id, k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma,
                      k.toimenpideinstanssi, k.sopimus, k.luotu, k.luoja, k.muokattu, k.muokkaaja)::TEXT)
      FROM kustannusarvioitu_tyo k
-- Siirretään vain edellisen kuukauden jutut. Ei siis kuluvan kuukauden hommia
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
       -- Siirretään vain ne, joita ei ole vielä siirretty
       AND k."siirretty?" = FALSE
       -- Siirretään vain tietyn tehtäväryhmän tehtäviä tai yksilöityjä tehtäviä
       AND (k.tehtavaryhma = (SELECT id
                                FROM tehtavaryhma
                               WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
         OR k.tehtava IN (SELECT id
                            FROM tehtava t
                           WHERE t.yksiloiva_tunniste IN
                                 ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                  'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                  '53647ad8-0632-4dd3-8302-8dfae09908c8'))) -- Hoidonjohtopalkkio
        ON CONFLICT DO NOTHING;

    -- Tästä taulusta (johto_ja_hallintokorvaus) siirretään kaikki rivit
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu,
                                          tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luoja, luotu, rivin_tunnistin)
    SELECT j.vuosi,
           j.kuukausi,
           (j.tunnit * j.tuntipalkka * j."osa-kuukaudesta")                 AS summa,
           (j.tunnit * j.tuntipalkka_indeksikorjattu * j."osa-kuukaudesta") AS summa_indeksikorjattu,
           j.indeksikorjaus_vahvistettu                                     AS indeksikorjaus_vahvistettu,
           'laskutettava-tyo'                                               AS tyyppi,
           NULL                                                             AS tehtava,
           (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)'),
           (SELECT tpi.id AS id
              FROM toimenpideinstanssi tpi
                       JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                       JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id,
                   maksuera m
             WHERE tpi.urakka = j."urakka-id"
               AND m.toimenpideinstanssi = tpi.id
               AND tpk2.koodi = '23150'),
           (SELECT id
              FROM sopimus s
             WHERE s.urakka = j."urakka-id"
               AND s.poistettu IS NOT TRUE
             ORDER BY s.loppupvm DESC
             LIMIT 1),
           j."urakka-id",
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
           NOW(),
           MD5(CONCAT(j.id, j."urakka-id", j."toimenkuva-id", j.tunnit, j.tuntipalkka,
                      j.luotu, j.luoja, j.muokattu, j.muokkaaja, j.vuosi, j.kuukausi,
                      j."ennen-urakkaa", j."osa-kuukaudesta")::TEXT)
      FROM johto_ja_hallintokorvaus j
-- Siirretään vain edellisen kuukauden jutut
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
       -- Ja ne, joita ei ole vielä siirretty
       AND j."siirretty?" = FALSE
        ON CONFLICT DO NOTHING;

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirretty
    UPDATE kustannusarvioitu_tyo k
       SET "siirretty?" = TRUE
-- Päivitetään vain edellisen kuukauden jutut
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
       -- Ja vain ne, joita ei ole aiemin päivitetty siirretyksi
       AND k."siirretty?" = FALSE
       -- Päivitetään vain tietyn tehtäväryhmän tehtäviä tai yksilöityjä tehtäviä
       AND (k.tehtavaryhma =
            (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
         OR
            k.tehtava IN (SELECT id
                            FROM tehtava t
                           WHERE t.yksiloiva_tunniste IN
                                 ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                  'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                  '53647ad8-0632-4dd3-8302-8dfae09908c8')));
    -- Hoidonjohtopalkkio

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirrety
    UPDATE johto_ja_hallintokorvaus j
       SET "siirretty?" = TRUE
-- Päivitetään vain edellisen kuukauden jutut
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
       -- Ja vain ne, joita ei ole aiemmin päivitetty siirrettäväksi
       AND j."siirretty?" = FALSE;

    -- Merkitään hoidon johdon maksuerä likaiseksi kaikissa voimassaolevissa MH-urakoissa
    UPDATE maksuera
       SET likainen = TRUE,
           muokattu = CURRENT_TIMESTAMP
     WHERE toimenpideinstanssi IN
           (SELECT tpi.id
              FROM toimenpideinstanssi tpi
                       JOIN toimenpide tpk
                            ON tpi.toimenpide = tpk.id AND tpk.koodi = '23151' -- 'MHU ja HJU Hoidon johto'
                       JOIN urakka u ON tpi.urakka = u.id AND u.tyyppi = 'teiden-hoito'
                  -- Maksueriä voi lähettää Sampoon vielä 3 kk urakan päättymisen jälkeen.
                  AND (u.loppupvm + INTERVAL '1 month' * 3) > pvm);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION siirra_budjetoidut_tyot_toteumiin_uudestaan(pvm DATE) RETURNS VOID AS
$$
BEGIN
    RAISE NOTICE 'Ennen ekaa inserttiä';
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu,
                                          tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luoja, luotu, rivin_tunnistin)
    SELECT k.vuosi,
           k.kuukausi,
           k.summa,
           k.summa_indeksikorjattu                                 AS si,
           k.indeksikorjaus_vahvistettu,
           k.tyyppi,
           k.tehtava,
           k.tehtavaryhma,
           k.toimenpideinstanssi,
           k.sopimus,
           (SELECT s.urakka FROM sopimus s WHERE s.id = k.sopimus) AS "urakka-id",
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
           NOW(),
           MD5(CONCAT(k.id, k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma,
                      k.toimenpideinstanssi, k.sopimus, k.luotu, k.luoja, k.muokattu, k.muokkaaja)::TEXT)
      FROM kustannusarvioitu_tyo k
-- Siirretään edelleen vain kaikki menneet (alkaen edellisestä kuukaudesta)
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
       -- Nyt ei kiinnitetä huomiota siihen, onko rivi jo siirretty
       -- AND k."siirretty?" = false
       -- Siirretään vain tietyn tehtäväryhmän tehtäviä tai yksilöityjä tehtäviä
       -- Automaattisesti toteumaksi lasketaan tehtäväryhmä: Erillishankinnat (W)
       -- Ja tehtävät: Toimistotarvike- ja ICT-kulut, Hoitourakan työnjohto sekä Hoidonjohtopalkkio
       AND (k.tehtavaryhma = (SELECT id
                                FROM tehtavaryhma
                               WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
         OR k.tehtava IN (SELECT id
                            FROM tehtava t
                           WHERE t.yksiloiva_tunniste IN
                                 ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                  'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                  '53647ad8-0632-4dd3-8302-8dfae09908c8'))) -- Hoidonjohtopalkkio
        ON CONFLICT (rivin_tunnistin)
            DO UPDATE SET summa_indeksikorjattu      = EXCLUDED.summa_indeksikorjattu,
                          indeksikorjaus_vahvistettu = EXCLUDED.indeksikorjaus_vahvistettu,
                          muokattu                   = NOW();

    RAISE NOTICE 'Ennen tokaa inserttiä';

    -- Tästä taulusta (johto_ja_hallintokorvaus) siirretään kaikki rivit
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu,
                                          tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luoja, luotu, rivin_tunnistin)
    SELECT j.vuosi,
           j.kuukausi,
           (j.tunnit * j.tuntipalkka * j."osa-kuukaudesta")                 AS summa,
           (j.tunnit * j.tuntipalkka_indeksikorjattu * j."osa-kuukaudesta") AS summa_indeksikorjattu,
           j.indeksikorjaus_vahvistettu                                     AS indeksikorjaus_vahvistettu,
           'laskutettava-tyo'                                               AS tyyppi,
           NULL                                                             AS tehtava,
           (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)'),
           (SELECT tpi.id AS id
              FROM toimenpideinstanssi tpi
                       JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                       JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id,
                   maksuera m
             WHERE tpi.urakka = j."urakka-id"
               AND m.toimenpideinstanssi = tpi.id
               AND tpk2.koodi = '23150'),
           (SELECT id
              FROM sopimus s
             WHERE s.urakka = j."urakka-id"
               AND s.poistettu IS NOT TRUE
             ORDER BY s.loppupvm DESC
             LIMIT 1),
           j."urakka-id",
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
           NOW(),
           MD5(CONCAT(j.id, j."urakka-id", j."toimenkuva-id", j.tunnit, j.tuntipalkka,
                      j.luotu, j.luoja, j.muokattu, j.muokkaaja, j.vuosi, j.kuukausi,
                      j."ennen-urakkaa", j."osa-kuukaudesta")::TEXT)
      FROM johto_ja_hallintokorvaus j
-- Siirretään menneet
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
-- Eikä kiinnitetä huomiota siihen, onko ne jo siirretty vai ei
--AND j."siirretty?" = false
        ON CONFLICT (rivin_tunnistin)
            DO UPDATE SET summa_indeksikorjattu      = EXCLUDED.summa_indeksikorjattu,
                          indeksikorjaus_vahvistettu = EXCLUDED.indeksikorjaus_vahvistettu,
                          muokattu                   = NOW();

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirretty
    UPDATE kustannusarvioitu_tyo k
       SET "siirretty?" = TRUE
-- Päivitetään vain edellisen kuukauden jutut
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
       -- Ja vain ne, joita ei ole aiemin päivitetty siirretyksi
       AND k."siirretty?" = FALSE
       -- Päivitetään vain tietyn tehtäväryhmän tehtäviä tai yksilöityjä tehtäviä
       AND (k.tehtavaryhma =
            (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
         OR
            k.tehtava IN (SELECT id
                            FROM tehtava t
                           WHERE t.yksiloiva_tunniste IN
                                 ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                  'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                  '53647ad8-0632-4dd3-8302-8dfae09908c8')));
    -- Hoidonjohtopalkkio

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirrety
    UPDATE johto_ja_hallintokorvaus j
       SET "siirretty?" = TRUE
-- Päivitetään vain edellisen kuukauden jutut
     WHERE (SELECT (DATE_TRUNC('MONTH', FORMAT('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < DATE_TRUNC('month', pvm)
       -- Ja vain ne, joita ei ole aiemmin päivitetty siirrettäväksi
       AND j."siirretty?" = FALSE;

    -- Merkitään hoidon johdon maksuerä likaiseksi kaikissa voimassaolevissa MH-urakoissa
    UPDATE maksuera
       SET likainen = TRUE,
           muokattu = CURRENT_TIMESTAMP
     WHERE toimenpideinstanssi IN
           (SELECT tpi.id
              FROM toimenpideinstanssi tpi
                       JOIN toimenpide tpk
                            ON tpi.toimenpide = tpk.id AND tpk.koodi = '23151' -- 'MHU ja HJU Hoidon johto'
                       JOIN urakka u ON tpi.urakka = u.id AND u.tyyppi = 'teiden-hoito'
                  -- Maksueriä voi lähettää Sampoon vielä 3 kk urakan päättymisen jälkeen.
                  AND (u.loppupvm + INTERVAL '1 month' * 3) > pvm);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION paivita_toteutuneet_kustannukset_jjh() RETURNS TRIGGER AS
$$
DECLARE
    md5hash       TEXT;
    toteutunut_id INTEGER;
    uusihash      TEXT;
BEGIN
    -- Jos toteutuneet_kustannukset taulussa on hash, joka vastaa vanhaa riviä,
    -- niin poista se ja luo uusi
    md5hash := MD5(CONCAT(OLD.id, OLD."urakka-id", OLD."toimenkuva-id", OLD.tunnit, OLD.tuntipalkka,
                          OLD.luotu, OLD.luoja, OLD.muokattu, OLD.muokkaaja, OLD.vuosi, OLD.kuukausi,
                          OLD."ennen-urakkaa", OLD."osa-kuukaudesta")::TEXT);
    SELECT id FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash INTO toteutunut_id;

    IF (toteutunut_id IS NOT NULL) THEN
        -- Poista vanha
        DELETE FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash;
        IF (TG_OP = 'UPDATE') THEN
            -- Luo uusi
            uusihash := MD5(CONCAT(NEW.id, NEW."urakka-id", NEW."toimenkuva-id", NEW.tunnit, NEW.tuntipalkka,
                                   NEW.luotu, NEW.luoja, NEW.muokattu, NEW.muokkaaja, NEW.vuosi, NEW.kuukausi,
                                   NEW."ennen-urakkaa", NEW."osa-kuukaudesta")::TEXT);

            INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu,
                                                  indeksikorjaus_vahvistettu, tyyppi, tehtava, tehtavaryhma,
                                                  toimenpideinstanssi, sopimus_id,
                                                  urakka_id, luoja, luotu, rivin_tunnistin)
            VALUES (NEW.vuosi,
                    NEW.kuukausi,
                    (NEW.tunnit * NEW.tuntipalkka * NEW."osa-kuukaudesta"),
                    (NEW.tunnit * NEW.tuntipalkka_indeksikorjattu * NEW."osa-kuukaudesta"),
                    NEW.indeksikorjaus_vahvistettu,
                    'laskutettava-tyo',
                    NULL,
                    (SELECT id
                       FROM tehtavaryhma
                      WHERE nimi = 'Johto- ja hallintokorvaus (J)'),
                    (SELECT tpi.id AS id
                       FROM toimenpideinstanssi tpi
                                JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                                JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id,
                            maksuera m
                      WHERE tpi.urakka = NEW."urakka-id"
                        AND m.toimenpideinstanssi = tpi.id
                        AND tpk2.koodi = '23150'),
                    (SELECT id
                       FROM sopimus s
                      WHERE s.urakka = NEW."urakka-id"
                        AND s.poistettu IS NOT TRUE
                      ORDER BY s.loppupvm DESC
                      LIMIT 1),
                    NEW."urakka-id",
                    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
                    NOW(),
                    uusihash);
        END IF;
    END IF;
    --
    IF (TG_OP = 'UPDATE') THEN
        RETURN NEW;
    ELSE
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;


-- Rahavaraus id:n lisäys ja populointi --
-- Palauttaa päivitetyt rivit yhteenlaskettuna
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
    SELECT id INTO rv_akilliset_id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt' ORDER BY id ASC LIMIT 1;
    SELECT id INTO rv_vahingot_id FROM rahavaraus WHERE nimi = 'Vahinkojen korjaukset' ORDER BY id ASC LIMIT 1;
    SELECT id INTO rv_tunneli_id FROM rahavaraus WHERE nimi = 'Tunneleiden hoito' ORDER BY id ASC LIMIT 1;
    SELECT id
      INTO rv_lupaukseen1_id
      FROM rahavaraus
     WHERE nimi ILIKE '%Tilaajan rahavaraus kannustinjärjestelmään%'
     ORDER BY id ASC
     LIMIT 1;
    SELECT id
      INTO rv_muut_tavoitehintaan_id
      FROM rahavaraus
     WHERE nimi ILIKE '%Muut tavoitehintaan vaikuttavat rahavaraukset%'
     ORDER BY id ASC
     LIMIT 1;

    -- Haetaan tehtävien id:t
    SELECT id INTO t_tunneli_id FROM tehtava WHERE nimi ILIKE '%Tunneleiden hoito%' ORDER BY id ASC LIMIT 1;
    SELECT id
      INTO t_lupaukseen1_id
      FROM tehtava
     WHERE nimi ILIKE '%Tilaajan rahavaraus lupaukseen 1%'
     ORDER BY id ASC
     LIMIT 1;
    SELECT id
      INTO t_muut_tavoitehintaan_id
      FROM tehtava
     WHERE nimi ILIKE '%Muut tavoitehintaan%'
     ORDER BY id ASC
     LIMIT 1;

    -- Haetaan Tehtäväryhmien idt
    SELECT id
      INTO tr_lupaus1_id
      FROM tehtavaryhma
     WHERE nimi ILIKE '%Tilaajan rahavaraus lupaukseen 1%'
     ORDER BY id ASC
     LIMIT 1;
    SELECT id
      INTO tr_muut_yllapito_id
      FROM tehtavaryhma
     WHERE nimi ILIKE '%Muut, MHU ylläpito (F)%'
     ORDER BY id ASC
     LIMIT 1;

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
-- -> Päällysteiden paikkaus (hoidon ylläpito)	Päällysteiden paikkaus, muut työt (Y)

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
         WHERE t.nimi = 'Päällysteiden paikkaus, muut työt (Y)';

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
   SET likainen = true
  FROM toimenpideinstanssi tpi
  JOIN toimenpide tp ON tpi.toimenpide = tp.id
  JOIN urakka u ON tpi.urakka = u.id
 WHERE m.toimenpideinstanssi = tpi.id
   AND tp.koodi IN('23116', '20107');
