-- Luodaan tauluun toteutuneet_kustannukset kenttä, johon merkitään, että
-- mistä taulusta kyseinen rivi on siirretty, koska siirrettyjä tietoja voidaan muokata ja muokkausten
-- yhteydessä myös toteutuneita kustannuksia täytyy jälkikäteen pystyä muuttamaan.

-- Poistetaan taulusta kaikki data, jotta voidaan tehdä kaikki uudestaan puhtaalta pöydältä
DELETE
FROM toteutuneet_kustannukset;

-- Luo kolumni toteutuneet_kustannukset tauluun, johon tallennetaan tieto, että mistä taulusta ja mikä rivi
-- kyseiselle riville on tallennettu
ALTER TABLE toteutuneet_kustannukset
    ADD COLUMN rivin_tunnistin TEXT NOT NULL;

-- Siirretään kaikki data sinne uusiksi.
-- Siirrä kustannusarvoitu_tyo taulusta mikäli tehtäväryhmä: Erillishankinnat (W) tai tehtävät: Toimistotarvike- ja ICT-kulut, tiedotus, ja Hoitourakan työnjohto
INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                      sopimus_id,
                                      urakka_id, luotu, rivin_tunnistin)
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
  AND (k.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
    OR k.tehtava in (SELECT id
                     FROM toimenpidekoodi t
                     WHERE t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                        OR t.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
    );

-- Siirretään kaikki olemassaolevat rivit johto_ja_hallintakorvaus taulusta toteutuneet_kustannukset tauluun
do
$$
    DECLARE
        tehtavaryhma_id integer := (SELECT id
                                    FROM tehtavaryhma
                                    WHERE nimi = 'Johto- ja hallintokorvaus (J)');
    BEGIN

        INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma,
                                              toimenpideinstanssi,
                                              sopimus_id, urakka_id, luotu, rivin_tunnistin)
        SELECT j.vuosi,
               j.kuukausi,
               (j.tunnit * j.tuntipalkka) AS summa,
               'laskutettava-tyo'         AS tyyppi,
               null                       as tehtava,
               tehtavaryhma_id,
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
              date_trunc('month', current_date);
    END
$$;

-- Luodaan funktio, jonka update tai delete kustannusarvoitu_tyo trigger ajaa
CREATE OR REPLACE FUNCTION paivita_toteutuneet_kustannukset_kat() RETURNS trigger AS $$
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
    RAISE NOTICE 'Päivitettään mahdollinen toteutunut_kustannus :: md5hash % , id:  %',md5hash, toteutunut_id;
    RAISE NOTICE 'NEW: %, %', NEW.id, NEW;
    RAISE NOTICE 'OLD: %, %', OLD.id, OLD;

    IF (toteutunut_id IS NOT NULL) THEN
        -- Poista vanha
        RAISE NOTICE 'Poistetaan vanha :: md5hash %',md5hash;
        DELETE FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash;

        IF (TG_OP = 'UPDATE') THEN
            -- Luo uusi, jos on päivityshommia kutsuttu
            RAISE NOTICE 'Luodaan uusi :: md5hash %',md5hash;
            INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma,
                                                  toimenpideinstanssi, sopimus_id,
                                                  urakka_id, luotu, rivin_tunnistin)
            VALUES (NEW.vuosi,
                    NEW.kuukausi,
                    NEW.summa,
                    NEW.tyyppi,
                    NEW.tehtava,
                    NEW.tehtavaryhma,
                    NEW.toimenpideinstanssi,
                    NEW.sopimus,
                    (select s.urakka FROM sopimus s where s.id = NEW.sopimus),
                    NOW(),
                    MD5(concat(NEW.id, NEW.vuosi, NEW.kuukausi, NEW.summa, NEW.tyyppi, NEW.tehtava, NEW.tehtavaryhma,
                               NEW.toimenpideinstanssi, NEW.sopimus, NEW.luotu, NEW.luoja, NEW.muokattu,
                               NEW.muokkaaja)::TEXT));
        END IF;
    END IF;
    --
    IF (TG_OP = 'UPDATE') THEN
        RAISE NOTICE 'Palautetaan uusi: %', NEW;
        RETURN NEW;
    ELSE
        RAISE NOTICE 'Palautetaan vanha - koska delete: %', OLD;
        RETURN OLD;
    END IF;
END ;
$$ LANGUAGE plpgsql;

-- Luodaan funktio, jonka update tai delete johto_ja_hallintakorvaus trigger ajaa
CREATE OR REPLACE FUNCTION paivita_toteutuneet_kustannukset_jjh() RETURNS trigger AS
$$
DECLARE
    md5hash    TEXT;
    toteutunut_id INTEGER;
    uusihash TEXT;
BEGIN
    -- Jos toteutuneet_kustannukset taulussa on hash, joka vastaa vanhaa riviä,
    -- niin poista se ja luo uusi
    md5hash := MD5(concat(OLD.id, OLD."urakka-id", OLD."toimenkuva-id", OLD.tunnit, OLD.tuntipalkka,
                          OLD.luotu, OLD.luoja, OLD.muokattu, OLD.muokkaaja, OLD.vuosi, OLD.kuukausi,
                          OLD."ennen-urakkaa", OLD."osa-kuukaudesta")::TEXT);
    SELECT id FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash INTO toteutunut_id;

    RAISE NOTICE 'Päivitettään mahdollinen toteutunut_kustannus jjh taulun arvosta :: md5hash % , id:  %',md5hash, toteutunut_id;

    IF (toteutunut_id IS NOT NULL) THEN
        -- Poista vanha
        DELETE FROM toteutuneet_kustannukset WHERE rivin_tunnistin = md5hash;
        IF (TG_OP = 'UPDATE') THEN
            -- Luo uusi
            RAISE NOTICE 'Päivitetään toteutuneet_kustannukset summa: %', (NEW.tunnit * NEW.tuntipalkka);
            uusihash := MD5(concat(NEW.id, NEW."urakka-id", NEW."toimenkuva-id", NEW.tunnit, NEW.tuntipalkka,
                                   NEW.luotu, NEW.luoja, NEW.muokattu, NEW.muokkaaja, NEW.vuosi, NEW.kuukausi,
                                   NEW."ennen-urakkaa", NEW."osa-kuukaudesta")::TEXT);
            RAISE NOTICE 'Päivitetään toteutuneet_kustannukset summa: %. Uusi hash: %', (NEW.tunnit * NEW.tuntipalkka), uusihash;
            INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma,
                                                  toimenpideinstanssi, sopimus_id,
                                                  urakka_id, luotu, rivin_tunnistin)
            VALUES (NEW.vuosi,
                    NEW.kuukausi,
                    (NEW.tunnit * NEW.tuntipalkka),
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
                     limit 1),
                    NEW."urakka-id",
                    NOW(),
                    uusihash);
        END IF;
    END IF;
    --
    IF (TG_OP = 'UPDATE') THEN
        RAISE NOTICE 'Palautetaan uusi: %', NEW;
        RETURN NEW;
    ELSE
        RAISE NOTICE 'Palautetaan vanha - koska delete: %', OLD;
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Triggerit
CREATE TRIGGER tg_update_paivita_toteutuneet_kustannukset_kat
    AFTER UPDATE OR DELETE
    ON kustannusarvioitu_tyo
    FOR EACH ROW EXECUTE PROCEDURE paivita_toteutuneet_kustannukset_kat();

CREATE TRIGGER tg_update_paivita_toteutuneet_kustannukset_jjh
    AFTER UPDATE OR DELETE
    ON johto_ja_hallintokorvaus
    FOR EACH ROW EXECUTE PROCEDURE paivita_toteutuneet_kustannukset_jjh();
