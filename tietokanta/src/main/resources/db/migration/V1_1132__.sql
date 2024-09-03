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
    toimenpideinstanssi_id INTEGER;
BEGIN
    -- Looppaa kaikki teiden-hoito urakat 
    FOR urakka_id IN
        SELECT id FROM urakka WHERE tyyppi = 'teiden-hoito'
    LOOP
        -- Hae tehtäväryhmä missä virhe
        SELECT id INTO tehtavaryhma_id 
          FROM tehtavaryhma t 
         WHERE t.nimi LIKE '%Päällysteiden paikkaus%';

        -- Etsi urakan toimenpideinstanssi päällystepaikkaukselle
        SELECT tpi.id INTO toimenpideinstanssi_id
          FROM toimenpideinstanssi tpi
          JOIN toimenpide tp ON tpi.toimenpide = tp.id
          WHERE tp.koodi = '20107'  -- '20107' 'Päällystepaikkaukset'
            AND tpi.urakka = urakka_id;

        -- Katsotaan ensin että instanssi urakalle löytyy
        IF toimenpideinstanssi_id IS NOT NULL THEN
            -- Instanssi on löytynyt, päivitä se kulu_kohdistukseen mikäli kirjauksia tälle on
            -- RAISE NOTICE 'Päivitetään uusi instanssi urakalle: % instanssi id: % tehtavaryhma: %', urakka_id, toimenpideinstanssi_id, tehtavaryhma_id;

            -- Päivitetään rivejä mikäli niitä on
            UPDATE kulu_kohdistus
            -- Aseta uusi toimenpideinstanssi 'Päällystepaikkaukset'
            SET toimenpideinstanssi = toimenpideinstanssi_id,
                muokattu = current_timestamp,
                muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
            -- Missä tehtäväryhmä on päällysteiden paikkaus 
            WHERE tehtavaryhma = tehtavaryhma_id
              -- Missä urakan toimenpideinstanssi on vanha '23116' 'Liikenneympäristön hoito'
              AND toimenpideinstanssi = (
                        SELECT id FROM toimenpideinstanssi 
                          WHERE toimenpide = (
                              SELECT id FROM toimenpide 
                                WHERE koodi = '23116') 
                                  AND urakka = urakka_id
              );
        ELSE
            -- RAISE NOTICE 'Ei löytynyt pp toimenpideinstanssia urakalle: % tehtavaryhma: %', urakka_id, tehtavaryhma_id;
        END IF;
    END LOOP;
END $$;


