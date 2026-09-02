-- Lisää tällä funktiolla valitulle lupaukselle lupausvastaukset
CREATE OR REPLACE FUNCTION lisaa_lupausvastaus(lupaus RECORD, paatoskk INT, urakka_id INT, hoitokauden_alkuvuosi INT, kayttaja_id INT)
    RETURNS VOID AS
$$
DECLARE
    vastausvuosi         INT;
    strkuukausi          TEXT;
    lupaus_vaihtoehto_id INT;
BEGIN

    -- Laske oikea vuosi: kuukaudet 10-12 kuuluvat hoitokauden alkuvuodelle,
    -- kuukaudet 1-9 kuuluvat seuraavalle vuodelle
    vastausvuosi := CASE
                        WHEN paatoskk >= 10 THEN hoitokauden_alkuvuosi
                        ELSE hoitokauden_alkuvuosi + 1
        END;

    strkuukausi := LPAD(CAST(paatoskk AS TEXT), 2, '0');

    RAISE NOTICE 'Kuukaudelle (alkuperäinen): %, vuosi: %', paatoskk, vastausvuosi;

    -- Lisätään valinta
    IF lupaus.lupaustyyppi = 'kysely' OR lupaus.lupaustyyppi = 'monivalinta' THEN
        -- HAe lupaus-vaihtoehto-id
        SELECT id
        FROM lupaus_vaihtoehto
        WHERE "lupaus-id" = lupaus.id
        order by id desc
        limit 1
        INTO lupaus_vaihtoehto_id;
    ELSE
        lupaus_vaihtoehto_id := NULL;
    end if;

    RAISE NOTICE 'Kuukaudelle (kaikki): %, vuosi: %', paatoskk, vastausvuosi;
    INSERT INTO lupaus_vastaus
    ("lupaus-id", "urakka-id", kuukausi, vuosi, paatos, vastaus,
     "lupaus-vaihtoehto-id", "veto-oikeutta-kaytetty", "veto-oikeus-aika",
     poistettu, muokkaaja, muokattu, luoja, luotu)
    VALUES (lupaus.id, urakka_id, paatoskk, vastausvuosi,
            true, true, lupaus_vaihtoehto_id, false, null, false, null, null, kayttaja_id, NOW())
    ON CONFLICT ("lupaus-id", "urakka-id", kuukausi, vuosi) DO NOTHING;

    -- Lisätään kustannusennusteet (2025 urakoilla)
    IF lupaus.lupaustyyppi = 'kustannusennuste' THEN
        INSERT INTO lupaus_kustannusennuste
        ("lupaus-id", "urakka-id", hoitovuosi, maarapaiva, ennustettu_tavoitehinta,
         ennustetut_kustannukset, syotetty_pvm, lasketut_pisteet, luoja, luotu)
        VALUES (lupaus.id, urakka_id, hoitokauden_alkuvuosi,
                TO_DATE(CONCAT(vastausvuosi, strkuukausi, '15'), 'YYYYMMDD'),
                40.0, 40.0, TO_DATE(CONCAT(vastausvuosi, strkuukausi, '15'), 'YYYYMMDD'), 3, kayttaja_id, NOW())
        ON CONFLICT ("lupaus-id", "urakka-id", maarapaiva) DO NOTHING;
    END IF;

END;
$$ LANGUAGE plpgsql;

-- Lisää tällä funktiolla valitulle urakalle vastaukset kaikkiin lupauksiin
CREATE OR REPLACE FUNCTION lisaa_urakan_lupaukset(urakka_id INT, hoitokauden_alkuvuosi INT, urakan_alkuvuosi INT,
                                                  kayttaja_id INT)
    RETURNS VOID AS
$$
DECLARE
    lupaus   RECORD;
    paatoskk INT;
BEGIN
    FOR lupaus IN
        SELECT id,
               "lupausryhma-id",
               lupaustyyppi,
               pisteet,
               "kirjaus-kkt" as kirjauskuukaudet,
               "paatos-kk"   as paatoskuukaudet
        FROM lupaus
        WHERE "urakan-alkuvuosi" = urakan_alkuvuosi
        ORDER BY jarjestys
        LOOP
            RAISE NOTICE 'Käsitellään lupaus: %', lupaus;

            -- Tarkista sisältääkö array nollan
            IF 0 = ANY (lupaus.paatoskuukaudet) THEN
                -- Jos nolla, käytä kaikkia kuukausia 1-12
                FOR paatoskk IN SELECT generate_series(1, 12) AS kk
                    LOOP
                        PERFORM lisaa_lupausvastaus(lupaus, paatoskk, urakka_id, hoitokauden_alkuvuosi, kayttaja_id);
                    END LOOP;
            ELSE
                -- Muuten käytä alkuperäisiä kuukausia
                FOR paatoskk IN SELECT UNNEST(lupaus.paatoskuukaudet) AS kk
                    LOOP
                        PERFORM lisaa_lupausvastaus(lupaus, paatoskk, urakka_id, hoitokauden_alkuvuosi, kayttaja_id);
                    END LOOP;
            END IF;
        END LOOP;
END;
$$ LANGUAGE plpgsql;
