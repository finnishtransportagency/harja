CREATE OR REPLACE FUNCTION pot2_rc_prosentti(paallystekerros_id INT)
    RETURNS NUMERIC AS
$$
DECLARE
    paallystekerros         pot2_paallystekerros;
    toimenpide              TEXT;
    asfalttirouhe_koodi     INTEGER;
    asfalttirouhe_runkoaine pot2_mk_massan_runkoaine;
BEGIN
    SELECT * FROM pot2_paallystekerros WHERE id = paallystekerros_id LIMIT 1 INTO paallystekerros;
    SELECT lyhenne FROM pot2_mk_paallystekerros_toimenpide WHERE koodi = paallystekerros.toimenpide INTO toimenpide;
    SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Asfalttirouhe' INTO asfalttirouhe_koodi;
    SELECT * FROM pot2_mk_massan_runkoaine WHERE pot2_massa_id = paallystekerros.materiaali
                                         AND tyyppi = asfalttirouhe_koodi INTO asfalttirouhe_runkoaine;

    -- REM-toimenpiteissä sekoitetaan nykyistä massaa uuteen kiviainekseen, ja lähtökohtaisesti niiden
    -- yhteenlaskettu summa on aina 100kg/m2. Tällöin vanhan murskatun asfaltin osuus on RC-prosentti.
    -- Mikäli näiden toimenpiteiden runkoaineessakin on mukana asfalttirouhetta, pitää laskea koko toimenpiteen
    -- rc-prosentti ja sitä varten on käytössä uudistettu kaava.
    IF toimenpide IN ('REM','REMO') THEN
        IF paallystekerros.massamenekki < 100 THEN
            IF asfalttirouhe_runkoaine.massaprosentti IS DISTINCT FROM NULL THEN
                RETURN ROUND((100 - paallystekerros.massamenekki) + paallystekerros.massamenekki * (asfalttirouhe_runkoaine.massaprosentti / 100));
            ELSE
                RETURN ROUND(100 - paallystekerros.massamenekki);
            END IF;
        ELSE
            -- Jos REM-toimenpiteen massamenekki on yli 100, ei voida laskea RC-prosenttia oikein.
            -- Tämän pitäisi aina olla viite siitä, että kirjauksessa on tapahtunut virhe.
            RETURN NULL;
        END IF;
    ELSE
        IF toimenpide IN ('KAR') THEN
            RETURN 100;
        END IF;
    END IF;

    IF asfalttirouhe_runkoaine.massaprosentti IS DISTINCT FROM NULL THEN
        RETURN ROUND(asfalttirouhe_runkoaine.massaprosentti);
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION pot2_alusta_rc_prosentti(alusta_id INT)
    RETURNS NUMERIC AS
$$
DECLARE
    alusta                  pot2_alusta;
    toimenpide              TEXT;
    asfalttirouhe_koodi     INTEGER;
    asfalttirouhe_runkoaine pot2_mk_massan_runkoaine;
BEGIN
    SELECT * FROM pot2_alusta WHERE id = alusta_id LIMIT 1 INTO alusta;
    SELECT lyhenne FROM pot2_mk_alusta_toimenpide WHERE koodi = alusta.toimenpide INTO toimenpide;
    SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Asfalttirouhe' INTO asfalttirouhe_koodi;
    SELECT * FROM pot2_mk_massan_runkoaine WHERE pot2_massa_id = alusta.massa
                                         AND tyyppi = asfalttirouhe_koodi INTO asfalttirouhe_runkoaine;

    -- REM-toimenpiteissä sekoitetaan nykyistä massaa uuteen kiviainekseen, ja lähtökohtaisesti niiden
    -- yhteenlaskettu summa on aina 100kg/m2. Tällöin vanhan murskatun asfaltin osuus on RC-prosentti.
    -- Mikäli näiden toimenpiteiden runkoaineessakin on mukana asfalttirouhetta, pitää laskea koko toimenpiteen
    -- rc-prosentti ja sitä varten on käytössä uudistettu kaava. Jos alustan toimenpiteenä on REM-TAS, palautetaan
    -- koko toimenpiteen rc-prosentti.
    IF toimenpide IN ('REM-TAS') THEN
        IF alusta.massamenekki < 100 THEN
            IF asfalttirouhe_runkoaine.massaprosentti IS DISTINCT FROM NULL THEN
                RETURN ROUND((100 - alusta.massamenekki) + alusta.massamenekki * (asfalttirouhe_runkoaine.massaprosentti / 100));
            ELSE
                RETURN ROUND(100 - alusta.massamenekki);
            END IF;
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
