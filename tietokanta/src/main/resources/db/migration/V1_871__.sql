-- toteutuneet_kustannukset tauluun ei laskettu mukaan "osa-kuukaudesta" arvoa,
-- kun johto_ja_hallintokorvaus taulusta siirrettiin sinne dataa.
-- Tässä tiedostossa olevat muutokset korjaavat tämän.
-- Eli muutetaan proceduurit, jotka laskevat datat, jotta data menee tulevaisuudessa oikein.
-- Samalla lasketaan siellä jo olemassa olevat asiat uudestaan ja korjataan summat


-- Dropataan ensin triggeri
DROP TRIGGER tg_update_paivita_toteutuneet_kustannukset_jjh ON johto_ja_hallintokorvaus;

-- Päivitetään funktio, jonka update tai delete johto_ja_hallintakorvaus trigger ajaa
CREATE OR REPLACE FUNCTION paivita_toteutuneet_kustannukset_jjh() RETURNS trigger AS
$$
DECLARE
    md5hash       TEXT;
    toteutunut_id INTEGER;
    uusihash      TEXT;
BEGIN
    -- Jos toteutuneet_kustannukset taulussa on hash, joka vastaa vanhaa riviä,
    -- niin poista se ja luo uusi
    md5hash := MD5(concat(OLD.id, OLD."urakka-id", OLD."toimenkuva-id", OLD.tunnit, OLD.tuntipalkka,
                          OLD.luotu, OLD.luoja, OLD.muokattu, OLD.muokkaaja, OLD.vuosi, OLD.kuukausi,
                          OLD."ennen-urakkaa", OLD."osa-kuukaudesta")::TEXT);
    SELECT id FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash INTO toteutunut_id;

    IF (toteutunut_id IS NOT NULL) THEN
        -- Poista vanha
        DELETE FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash;
        IF (TG_OP = 'UPDATE') THEN
            -- Luo uusi
            uusihash := MD5(concat(NEW.id, NEW."urakka-id", NEW."toimenkuva-id", NEW.tunnit, NEW.tuntipalkka,
                                   NEW.luotu, NEW.luoja, NEW.muokattu, NEW.muokkaaja, NEW.vuosi, NEW.kuukausi,
                                   NEW."ennen-urakkaa", NEW."osa-kuukaudesta")::TEXT);

            INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma,
                                                  toimenpideinstanssi, sopimus_id,
                                                  urakka_id, luotu, rivin_tunnistin)
            VALUES (NEW.vuosi,
                    NEW.kuukausi,
                    (NEW.tunnit * NEW.tuntipalkka * NEW."osa-kuukaudesta"),
                    'laskutettava-tyo',
                    null,
                    (SELECT id
                     FROM tehtavaryhma
                     WHERE nimi = 'Johto- ja hallintokorvaus (J)'),
                    (SELECT tpi.id AS id
                     FROM toimenpideinstanssi tpi
                              JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                              JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
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

-- Päivitetään funktio, jolla siirretään tiedot kustannusarvioitu_tyo ja johto_ja_hallintakorvaus tauluista toteutuneet_kustannukset tauluun
CREATE OR REPLACE FUNCTION siirra_budjetoidut_tyot_toteumiin() RETURNS VOID AS
$$
BEGIN
    -- Automaattisesti toteumaksi lasketaan tehtäväryhmä: Erillishankinnat (W)
    -- Ja tehtävät: Toimistotarvike- ja ICT-kulut, ja Hoitourakan työnjohto
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT k.vuosi,
           k.kuukausi,
           k.summa,
           k.tyyppi,
           k.tehtava,
           k.tehtavaryhma,
           k.toimenpideinstanssi,
           k.sopimus,
           (select s.urakka FROM sopimus s where s.id = k.sopimus) as "urakka-id",
           NOW(),
           MD5(concat(k.id, k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma,
                      k.toimenpideinstanssi, k.sopimus, k.luotu, k.luoja, k.muokattu, k.muokkaaja)::TEXT)
    FROM kustannusarvioitu_tyo k
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) <
          date_trunc('month', current_date)
      AND k."siirretty?" = false
      AND (k.tehtavaryhma = (SELECT id
                             FROM tehtavaryhma
                             WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
        OR k.tehtava in (SELECT id
                         FROM toimenpidekoodi t
                         WHERE t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                            OR t.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
        )
    ON CONFLICT DO NOTHING;

    -- Tästä taulusta (johto_ja_hallintokorvaus) siirretään kaikki rivit
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT j.vuosi,
           j.kuukausi,
           (j.tunnit * j.tuntipalkka * j."osa-kuukaudesta") AS summa,
           'laskutettava-tyo'                               AS tyyppi,
           null                                             as tehtava,
           (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)'),
           (SELECT tpi.id AS id
            FROM toimenpideinstanssi tpi
                     JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                     JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
                 maksuera m
            WHERE tpi.urakka = j."urakka-id"
              AND m.toimenpideinstanssi = tpi.id
              AND tpk2.koodi = '23150'),
           (SELECT id
            FROM sopimus s
            WHERE s.urakka = j."urakka-id"
              AND s.poistettu IS NOT TRUE
            ORDER BY s.loppupvm DESC
            limit 1),
           j."urakka-id",
           NOW(),
           MD5(concat(j.id, j."urakka-id", j."toimenkuva-id", j.tunnit, j.tuntipalkka,
                      j.luotu, j.luoja, j.muokattu, j.muokkaaja, j.vuosi, j.kuukausi,
                      j."ennen-urakkaa", j."osa-kuukaudesta")::TEXT)
    FROM johto_ja_hallintokorvaus j
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) <
          date_trunc('month', current_date)
      AND j."siirretty?" = false
    ON CONFLICT DO NOTHING;

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirrety
    UPDATE kustannusarvioitu_tyo k
    SET "siirretty?" = true
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) <
          date_trunc('month', current_date)
      AND k."siirretty?" = false
      AND (k.tehtavaryhma =
           (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
        OR k.tehtava in (SELECT id
                         FROM toimenpidekoodi t
                         WHERE t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                            OR t.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
        );

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirrety
    UPDATE johto_ja_hallintokorvaus j
    SET "siirretty?" = true
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) <
          date_trunc('month', current_date)
      AND j."siirretty?" = false;

END;
$$ LANGUAGE plpgsql;

-- Luodaan poistettu triggeri uudestaan
CREATE TRIGGER tg_update_paivita_toteutuneet_kustannukset_jjh
    AFTER UPDATE OR DELETE
    ON johto_ja_hallintokorvaus
    FOR EACH ROW
EXECUTE PROCEDURE paivita_toteutuneet_kustannukset_jjh();

-- Päivitetään jo olemassa olevat rivit toteutuneet_kustannukset tauluun
CREATE OR REPLACE FUNCTION temp_korjaa_kustannukset() RETURNS VOID AS
$$
DECLARE
    rivi RECORD;
BEGIN
    FOR rivi IN SELECT j.id as "jid", tk.id as "tkid"
                FROM toteutuneet_kustannukset tk,
                     johto_ja_hallintokorvaus j
                WHERE j."osa-kuukaudesta" != 1
                  AND j."siirretty?" = true
                  AND tk.rivin_tunnistin = MD5(concat(j.id, j."urakka-id", j."toimenkuva-id", j.tunnit, j.tuntipalkka,
                                                      j.luotu, j.luoja, j.muokattu, j.muokkaaja, j.vuosi, j.kuukausi,
                                                      j."ennen-urakkaa", j."osa-kuukaudesta")::TEXT)
        LOOP
            RAISE NOTICE 'Asetetaan jjh siirretty false id: %', rivi.jid;
            RAISE NOTICE 'Ja poistetaan tk rivi id: %', rivi.tkid;
            DELETE FROM toteutuneet_kustannukset tk WHERE tk.id = rivi.tkid;
            UPDATE johto_ja_hallintokorvaus SET "siirretty?" = FALSE WHERE id = rivi.jid;
        END LOOP;
END ;
$$ LANGUAGE plpgsql;

-- Ajetaan yllä tehty funktio, joka poistaa virheelliset rivit
select temp_korjaa_kustannukset();
-- Päivitetään data toteutuneet_kustanukset tauluun uudestaan.
select siirra_budjetoidut_tyot_toteumiin();