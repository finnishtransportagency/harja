-- Rahavaraus id:n lisäys ja populointi -- SELECT populoi_rahavaraus_idt(); 
-- Palauttaa päivitetyt rivit yhteenlaskettuna 
CREATE OR REPLACE FUNCTION populoi_rahavaraus_idt()
RETURNS INTEGER AS $$
  DECLARE
    rivit_paivitetty INTEGER := 0;
  BEGIN
    -- ~ ~ toteutuneet_kustannukset ~ ~ --
    -- Lisää rahavaraus_id sarakkeet, on olemassa jo parissa taulussa, mutta ei haittaa 
    ALTER TABLE kulu_kohdistus ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
    ALTER TABLE kustannusarvioitu_tyo ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
    ALTER TABLE toteutuneet_kustannukset ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);

    -- Äkilliset hoitotyöt 
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = ra.id
      FROM (SELECT id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%' ORDER BY id ASC LIMIT 1) ra
     WHERE tyyppi = 'akillinen-hoitotyo' 
      AND ra.id IS NOT NULL;

    -- Vahinkojen korvaukset
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = ra.id
      FROM (SELECT id FROM rahavaraus WHERE nimi LIKE '%Vahinkojen korvaukset%' ORDER BY id ASC LIMIT 1) ra
     WHERE tyyppi = 'vahinkojen-korjaukset' 
      AND ra.id IS NOT NULL;

    -- ~ ~ kulu_kohdistus ~ ~ --
    -- Äkilliset hoitotyöt 
    UPDATE kulu_kohdistus
       SET rahavaraus_id = ra.id
      FROM (SELECT id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%' ORDER BY id ASC LIMIT 1) AS ra
     WHERE maksueratyyppi = 'akillinen-hoitotyo'
       AND ra.id IS NOT NULL;

    -- Maksuerätyyppi 'muu', luetaan laskutusyhteenvedeossa Vahinkojen korvauksena
    UPDATE kulu_kohdistus
       SET rahavaraus_id = ra.id
      FROM (SELECT id FROM rahavaraus WHERE nimi LIKE '%Vahinkojen korvaukset%' ORDER BY id ASC LIMIT 1) AS ra
     WHERE maksueratyyppi = 'muu'
       AND ra.id IS NOT NULL;

    -- ~ ~ kustannusarvioitu_tyo ~ ~ --
    -- Äkilliset hoitotyöt 
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = ra.id
      FROM (SELECT id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%' ORDER BY id ASC LIMIT 1) ra
     WHERE tyyppi = 'akillinen-hoitotyo' 
       AND ra.id IS NOT NULL;

    -- Vahinkojen korvaukset
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = ra.id
      FROM (SELECT id FROM rahavaraus WHERE nimi LIKE '%Vahinkojen korvaukset%' ORDER BY id ASC LIMIT 1) ra
     WHERE tyyppi = 'vahinkojen-korjaukset' 
       AND ra.id IS NOT NULL;

    -- Palauta pävittyneet rivit, debuggausta varten
    GET DIAGNOSTICS rivit_paivitetty = ROW_COUNT;
    RETURN rivit_paivitetty;
  END;
$$ LANGUAGE plpgsql;
