-- Rahavaraus id:n lisäys ja populointi --
-- Palauttaa päivitetyt rivit yhteenlaskettuna
CREATE OR REPLACE FUNCTION populoi_rahavaraus_idt()
    RETURNS INTEGER AS $$
DECLARE
    -- Rahavarausidt
    rv_vahingot_id INT;
    rv_akilliset_id INT;
    rv_tunneli_id INT;
    rv_lupaukseen1_id INT;
    rv_muut_tavoitehintaan_id INT;

    -- tehtavaidt
    t_tunneli_id INT;
    t_lupaukseen1_id INT;
    t_muut_tavoitehintaan_id INT;

    -- tehtäväryhmäidt
    tr_lupaus1_id INT;
    tr_muut_yllapito_id INT;

    rivit_paivitetty INTEGER := 0;

    puuttuva_rivi RECORD;

BEGIN
    -- Haetaan rahavarausten id:t
    SELECT id INTO rv_akilliset_id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO rv_vahingot_id FROM rahavaraus WHERE nimi LIKE 'Vahinkojen korjaukset' ORDER BY id ASC LIMIT 1;
    SELECT id INTO rv_tunneli_id FROM rahavaraus WHERE nimi LIKE '%Tunnelit%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO rv_lupaukseen1_id FROM rahavaraus WHERE nimi LIKE 'Tilaajan rahavaraus kannustinjärjestelmään' ORDER BY id ASC LIMIT 1;
    SELECT id INTO rv_muut_tavoitehintaan_id FROM rahavaraus WHERE nimi LIKE '%Muut tavoitehintaan vaikuttavat rahavaraukset%' ORDER BY id ASC LIMIT 1;

    -- Haetaan tehtävien id:t
    SELECT id INTO t_tunneli_id FROM tehtava WHERE nimi LIKE '%Tunneleiden hoito%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO t_lupaukseen1_id FROM tehtava WHERE nimi LIKE '%Tilaajan rahavaraus lupaukseen 1%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO t_muut_tavoitehintaan_id FROM tehtava WHERE nimi LIKE '%Muut tavoitehintaan%' ORDER BY id ASC LIMIT 1;

    -- Haetaan Tehtäväryhmien idt
    SELECT id INTO tr_lupaus1_id FROM tehtavaryhma WHERE nimi LIKE '%Tilaajan rahavaraus lupaukseen 1%' ORDER BY id ASC LIMIT 1;
    SELECT id INTO tr_muut_yllapito_id FROM tehtavaryhma WHERE nimi LIKE '%Muut, MHU ylläpito (F)%' ORDER BY id ASC LIMIT 1;

    -- ~ ~ toteutuneet_kustannukset ~ ~ --

    -- Äkilliset hoitotyöt
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = rv_akilliset_id
     WHERE tyyppi = 'akillinen-hoitotyo'
       AND rv_akilliset_id IS NOT NULL;

    -- Vahinkojen korvaukset
    UPDATE toteutuneet_kustannukset
       SET rahavaraus_id = rv_vahingot_id
     WHERE tyyppi = 'vahinkojen-korjaukset'
       AND rv_vahingot_id IS NOT NULL;

    -- ~ ~ kulu_kohdistus ~ ~ --
    -- Äkilliset hoitotyöt
    UPDATE kulu_kohdistus
       SET rahavaraus_id = rv_akilliset_id,
           tyyppi = 'rahavaraus'
     WHERE maksueratyyppi = 'akillinen-hoitotyo'
       AND rv_akilliset_id IS NOT NULL;

    -- Maksuerätyyppi 'muu', luetaan laskutusyhteenvedeossa Vahinkojen korvauksena
    UPDATE kulu_kohdistus
       SET rahavaraus_id = rv_vahingot_id,
           tyyppi = 'rahavaraus'
     WHERE maksueratyyppi = 'muu'
       AND rv_vahingot_id IS NOT NULL;

    --  Muut, MHU ylläpito (F) - Kulut rahavarauksiin -- Näitä ei ole. Kaikki 'muu' tyyppiset on vahingonkorvauksia

    -- Kun tehtäväryhmä on Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään (T3) - siitä tehdään kannustinjärjestelmä rahavaraus
    UPDATE kulu_kohdistus
       SET rahavaraus_id = rv_lupaukseen1_id,
           tyyppi = 'rahavaraus'
     WHERE tehtavaryhma = tr_lupaus1_id
       AND rv_lupaukseen1_id IS NOT NULL;


    -- ~ ~ kustannusarvioitu_tyo ~ ~ --
    -- Äkilliset hoitotyöt
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_akilliset_id,
           osio = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'akillinen-hoitotyo'
       AND rv_akilliset_id IS NOT NULL;

    -- Vahinkojen korvaukset
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_vahingot_id,
           osio = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'vahinkojen-korjaukset'
       AND rv_vahingot_id IS NOT NULL;

    -- muut-rahavaraukset -- tunnelien hoito
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_tunneli_id,
           osio = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'muut-rahavaraukset' AND tehtava = t_tunneli_id
       AND rv_tunneli_id IS NOT NULL;

    -- muut-rahavaraukset -- tehtävä: Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_lupaukseen1_id,
           osio = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'muut-rahavaraukset' AND tehtava = t_lupaukseen1_id
       AND rv_lupaukseen1_id IS NOT NULL;

    -- muut-rahavaraukset -- tehtävä: Muut tavoitehintaan vaikuttavat rahavaraukset
    UPDATE kustannusarvioitu_tyo
       SET rahavaraus_id = rv_muut_tavoitehintaan_id,
           osio = 'tavoitehintaiset-rahavaraukset'
     WHERE tyyppi = 'muut-rahavaraukset' AND tehtava = t_muut_tavoitehintaan_id
       AND rv_muut_tavoitehintaan_id IS NOT NULL;

    -- Tehdään ja ajetaan funktio, joka päivittää tarvittavat rahavaraukset kustannusarvioitu_tyo taulun tehtävien perusteella
    FOR puuttuva_rivi IN SELECT DISTINCT ON (concat(s.urakka, kt.rahavaraus_id)) concat(s.urakka, kt.rahavaraus_id),  s.urakka AS urakka_id, kt.rahavaraus_id, ru.rahavaraus_id
                           FROM kustannusarvioitu_tyo kt
                                    JOIN sopimus s ON s.id = kt.sopimus
                                    LEFT JOIN rahavaraus_urakka ru
                                              ON ru.urakka_id = s.urakka AND ru.rahavaraus_id = kt.rahavaraus_id
                          WHERE ru.rahavaraus_id IS NULL
                            AND kt.rahavaraus_id IS NOT NULL
        LOOP
            INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
            VALUES (puuttuva_rivi.urakka_id, puuttuva_rivi.rahavaraus_id,
                    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
            RAISE NOTICE 'Lisätty rahavaraus % urakalle %', puuttuva_rivi.rahavaraus_id, puuttuva_rivi.urakka_id;
        END LOOP;


    -- Palauta pävittyneet rivit, debuggausta varten
    GET DIAGNOSTICS rivit_paivitetty = ROW_COUNT;
    RETURN rivit_paivitetty;
END;
$$ LANGUAGE plpgsql;

-- Ja tehdään päivitys samalla
SELECT populoi_rahavaraus_idt();
