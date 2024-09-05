-- Muutetaan 'Muut päällysteiden paikkaukseen liittyvät työt' tehtävän emo 
-- 'Liikenneympäristön hoito' -> 'Päällystepaikkaukset'
-- Nyt hankintakulua kirjatessa kulu tulee oikean toimenpideinstanssin alle
-- sekä laskutusyhteenvedossa 'Päällysteiden paikkaukset' laariin

UPDATE tehtava
   SET emo       = (SELECT id FROM toimenpide WHERE koodi = '20107'),
       muokattu  = current_timestamp,
       muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt'
  AND emo  = (SELECT id FROM toimenpide WHERE koodi = '23116'); 

-- '23116' 'Liikenneympäristön hoito'
-- '20107' 'Päällystepaikkaukset'


-- Tämän yllä olevan päivityksen myötä meidän pitää nyt päivittää kulu_kohdistus taulun toimenpideinstanssit, 
-- eli kaikki vanhat kirjatut kulut tuolla vanhalla emolla sisältää nyt väärät toimenpideinstanssit.
-- 
-- Tässä tehdään nuo toimenpideinstanssi kytkökset oikein, jonka jälkeen kulut näytetään oikein sekä niitä pystytään muokata
-- 
-- Päivittää siis 'Liikenneympäristön hoito -> Päällysteiden paikkaukset'
-- -> Päällysteiden paikkaus (hoidon ylläpito)	Päällysteiden paikkaus (Y)

DO $$
DECLARE
    urakka_id INTEGER;
    tehtavaryhma_id INTEGER;
    tpi_paallystepaikkaus INTEGER;
    tpi_liikenneymparisto INTEGER;
BEGIN
    RAISE NOTICE '***********************************************';
    RAISE NOTICE 'Ajetaan migraatio päällystyskytkös aika: %', current_timestamp;

    -- Hae tehtäväryhmä missä virhe
    SELECT id INTO tehtavaryhma_id 
      FROM tehtavaryhma t 
     WHERE t.nimi = 'Päällysteiden paikkaus (Y)';
      
    -- Looppaa kaikki teiden-hoito urakat 
    FOR urakka_id IN
        SELECT id FROM urakka WHERE tyyppi = 'teiden-hoito'
    LOOP
        -- Etsi urakan toimenpideinstanssi päällystepaikkaukselle
        SELECT tpi.id INTO tpi_paallystepaikkaus
          FROM toimenpideinstanssi tpi
          JOIN toimenpide tp ON tpi.toimenpide = tp.id
         WHERE tp.koodi = '20107'  -- '20107' 'Päällystepaikkaukset'
           AND tpi.urakka = urakka_id;

        -- Etsi urakan toimenpideinstanssi liikenneympäristön hoidolle
        SELECT tpi.id INTO tpi_liikenneymparisto
          FROM toimenpideinstanssi tpi
          JOIN toimenpide tp ON tpi.toimenpide = tp.id
         WHERE tp.koodi = '23116'  -- '23116' 'Liikenneympäristön hoito'
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
                muokattu = current_timestamp,
                muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
            -- Missä tehtäväryhmä on päällysteiden paikkaus 
            WHERE tehtavaryhma = tehtavaryhma_id
              -- Missä urakan toimenpideinstanssi on vanha '23116' 'Liikenneympäristön hoito'
              AND toimenpideinstanssi = (
                        tpi_liikenneymparisto
              );
        ELSE
            RAISE NOTICE 'Ei löytynyt tietoja urakalle: % tehtavaryhma: % - pp id: % ly id: %', 
            urakka_id, tehtavaryhma_id, tpi_paallystepaikkaus, tpi_liikenneymparisto;
        END IF;
    END LOOP;
END $$;


-- TL;DR
-- Korjataan 'Muut päällysteiden paikkaukseen liittyvät työt' tehtävän emo,
-- ja korjataan samalla instanssit kulu_kohdistus tauluun kirjatut kulut tälle tehtävälle  
