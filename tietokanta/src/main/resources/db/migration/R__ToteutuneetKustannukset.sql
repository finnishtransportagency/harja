-- Toteutuneet kustannukste tauluun lisätään joka kuukausi kustannuksia.
-- Siirtoa hallitaan alla olevilla funktioilla


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

-- Päivitetään funktio, jonka update tai delete kustannusarvoitu_tyo trigger ajaa
CREATE OR REPLACE FUNCTION paivita_toteutuneet_kustannukset_kat() RETURNS trigger AS
$$
DECLARE
    md5hash       TEXT;
    toteutunut_id INTEGER;
BEGIN
    -- Jos toteutuneet_kustannukset taulussa on hash, joka vastaa vanhaa riviä,
    -- niin poista se ja luo uusi
    md5hash := MD5(concat(OLD.id, OLD.vuosi, OLD.kuukausi, OLD.summa, OLD.tyyppi, OLD.tehtava, OLD.tehtavaryhma,
                          OLD.toimenpideinstanssi, OLD.sopimus, OLD.luotu, OLD.luoja, OLD.muokattu,
                          OLD.muokkaaja)::TEXT);
    SELECT id FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash INTO toteutunut_id;

    IF (toteutunut_id IS NOT NULL) THEN
        -- Poista vanha
        DELETE FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash;

        IF (TG_OP = 'UPDATE') THEN
            -- Luo uusi, jos on päivityshommia kutsuttu
            INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu, tyyppi, tehtava, tehtavaryhma,
                                                  toimenpideinstanssi, sopimus_id,
                                                  urakka_id, luoja, luotu, rivin_tunnistin)
            VALUES (NEW.vuosi,
                    NEW.kuukausi,
                    NEW.summa,
                    NEW.summa_indeksikorjattu,
                    NEW.indeksikorjaus_vahvistettu,
                    NEW.tyyppi,
                    NEW.tehtava,
                    NEW.tehtavaryhma,
                    NEW.toimenpideinstanssi,
                    NEW.sopimus,
                    (SELECT s.urakka FROM sopimus s WHERE s.id = NEW.sopimus),
                    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
                    NOW(),
                    MD5(concat(NEW.id, NEW.vuosi, NEW.kuukausi, NEW.summa, NEW.tyyppi, NEW.tehtava, NEW.tehtavaryhma,
                               NEW.toimenpideinstanssi, NEW.sopimus, NEW.luotu, NEW.luoja, NEW.muokattu,
                               NEW.muokkaaja)::TEXT));
        END IF;
    END IF;
    --
    IF (TG_OP = 'UPDATE') THEN
        RETURN NEW;
    ELSE
        RETURN OLD;
    END IF;
END ;
$$ LANGUAGE plpgsql;
