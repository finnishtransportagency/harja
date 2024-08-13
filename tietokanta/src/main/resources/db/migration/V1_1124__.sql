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

    -- REM- ja REMO- toimenpiteissä massamenekki on aina 100 - massamenekki.
    -- REM-toimenpiteissä sekoitetaan nykyistä massaa uuteen kiviainekseen, ja lähtökohtaisesti niiden
    -- yhteenlaskettu summa on aina 100kg/m2. Tällöin vanhan murskatun asfaltin osuus on RC-prosentti.
    IF toimenpide IN ('REM', 'REMO') THEN
        IF paallystekerros.massamenekki < 100 THEN
            RETURN 100 - paallystekerros.massamenekki;
        ELSE
            -- Jos REM/REMO-toimenpiteen massamenekki on yli 100, ei voida laskea RC-prosenttia oikein.
            -- Tämän pitäisi aina olla viite siitä, että kirjauksessa on tapahtunut virhe.
            RETURN NULL;
        END IF;
    ELSE
        IF toimenpide IN ('KAR') THEN
            RETURN 100;
        END IF;
    END IF;

    SELECT koodi FROM pot2_mk_runkoainetyyppi WHERE nimi = 'Asfalttirouhe' INTO asfalttirouhe_koodi;

    SELECT *
    FROM pot2_mk_massan_runkoaine
    WHERE pot2_massa_id = paallystekerros.materiaali
      AND tyyppi = asfalttirouhe_koodi
    INTO asfalttirouhe_runkoaine;

    IF asfalttirouhe_runkoaine IS DISTINCT FROM NULL
    THEN
        RETURN asfalttirouhe_runkoaine.massaprosentti;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
