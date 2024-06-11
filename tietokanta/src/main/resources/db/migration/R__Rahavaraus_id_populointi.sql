-- Rahavaraus id:n lisäys ja populointi -- SELECT populoi_rahavaraus_idt(); 
-- Palauttaa päivitetyt rivit yhteenlaskettuna 
DROP FUNCTION IF EXISTS populoi_rahavaraus_idt();

CREATE OR REPLACE FUNCTION populoi_rahavaraus_idt()
RETURNS INTEGER AS $$
  DECLARE
    vahingot_id INT;
    akilliset_id INT;
    kannustin_id INT;
    rivit_paivitetty INTEGER := 0;
  BEGIN
    
    SELECT id INTO akilliset_id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO vahingot_id FROM rahavaraus WHERE nimi LIKE '%Vahinkojen korjaukset%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO kannustin_id FROM rahavaraus WHERE nimi LIKE '%Kannustinjärjestelmä%' ORDER BY id ASC LIMIT 1;

    ---------------------------------------------
    --------- toteutuneet_kustannukset ----------
    ---------------------------------------------
    -- Lisää rahavaraus_id sarakkeet, on olemassa jo parissa taulussa, mutta ei haittaa 
    ALTER TABLE kulu_kohdistus ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
    ALTER TABLE kustannusarvioitu_tyo ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
    ALTER TABLE toteutuneet_kustannukset ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);

    -- Äkilliset hoitotyöt 
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = akilliset_id
     WHERE tyyppi = 'akillinen-hoitotyo' 
       AND akilliset_id IS NOT NULL;

    -- Vahinkojen korjaukset
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = vahingot_id
     WHERE tyyppi = 'vahinkojen-korjaukset' 
       AND vahingot_id IS NOT NULL;

    ---------------------------------------------
    -------------- kulu_kohdistus ---------------
    ---------------------------------------------
    -- Kannustinjärjrestelmä (hae yksilöyvällä tunnisteella)
    UPDATE kulu_kohdistus
       SET rahavaraus_id = kannustin_id
     WHERE tehtavaryhma IN (
          SELECT t.id
            FROM tehtavaryhma t
           WHERE t.yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6'
    ) AND kannustin_id IS NOT NULL;

    -- Äkilliset hoitotyöt 
    UPDATE kulu_kohdistus
       SET rahavaraus_id = akilliset_id
     WHERE maksueratyyppi = 'akillinen-hoitotyo'
       AND akilliset_id IS NOT NULL;

    -- Maksuerätyyppi 'muu', luetaan laskutusyhteenvedeossa Vahinkojen korjauksena
    UPDATE kulu_kohdistus
       SET rahavaraus_id = vahingot_id
     WHERE maksueratyyppi = 'muu'
       AND vahingot_id IS NOT NULL;

    ---------------------------------------------
    ----------- kustannusarvioitu_tyo -----------
    ---------------------------------------------
    -- Äkilliset hoitotyöt 
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = akilliset_id
     WHERE tyyppi = 'akillinen-hoitotyo' 
       AND akilliset_id IS NOT NULL;

    -- Vahinkojen korjaukset
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = vahingot_id
     WHERE tyyppi = 'vahinkojen-korjaukset' 
       AND vahingot_id IS NOT NULL;

    -- Palauta pävittyneet rivit, debuggausta varten
    GET DIAGNOSTICS rivit_paivitetty = ROW_COUNT;
    RETURN rivit_paivitetty;
  END;
$$ LANGUAGE plpgsql;
