-- Päivittää sopimuksen materiaalikäytön päivälle
CREATE OR REPLACE FUNCTION paivita_sopimuksen_materiaalin_kaytto(sopimus INTEGER, pvm DATE, urakkaid INTEGER)
    RETURNS VOID AS
$$
DECLARE
    mat RECORD;
    sop INTEGER;
BEGIN
    sop := sopimus;
    DELETE
      FROM sopimuksen_kaytetty_materiaali skm
     WHERE skm.sopimus = sop
       AND skm.alkupvm = pvm;
    FOR mat IN SELECT DATE_TRUNC('day', t.alkanut) AS alkupvm, tm.materiaalikoodi,
                      SUM(CASE
                              WHEN (t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE)
                                  THEN tm.maara
                              ELSE 0
                          END)                     AS maara
                 FROM toteuma t
                      JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                WHERE t.alkanut BETWEEN pvm::DATE AND pvm::DATE + '1 days'::INTERVAL
                  -- Lisättiin urakkaid ehto, jotta se osuu paremmin indeksiin
                  AND t.urakka = urakkaid
                  AND t.sopimus = sop
                GROUP BY DATE_TRUNC('day', t.alkanut), tm.materiaalikoodi
        LOOP
            INSERT
              INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
            VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara, CURRENT_TIMESTAMP)
                ON CONFLICT ON CONSTRAINT uniikki_sop_pvm_mk
                    DO UPDATE SET maara = mat.maara;
        END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_sopimuksen_materiaalikaytto_muutospaivalla(
    sopimus_id INTEGER, muutospvm DATE, urakka_id INTEGER
) RETURNS void AS $$
DECLARE
    rivi RECORD;
    u_id INTEGER := urakka_id;
    kasitellyt_muutos_idt INTEGER[];
BEGIN
    kasitellyt_muutos_idt := ARRAY[]::INTEGER[];
    
    -- Haetaan kaikki t.alkanut-päivät niille toteumille,
    -- joiden t.luotu tai t.muokattu on muutospvm-päivänä
    FOR rivi IN
        SELECT DISTINCT t.alkanut::date AS pvm
        FROM toteuma t
        WHERE t.urakka = u_id
          AND t.sopimus = sopimus_id
          AND ((t.luotu  >= muutospvm AND t.luotu  < muutospvm + INTERVAL '1 day')
            OR (t.muokattu >= muutospvm AND t.muokattu < muutospvm + INTERVAL '1 day'))
    LOOP
        PERFORM paivita_sopimuksen_materiaalin_kaytto(sopimus_id, rivi.pvm, urakka_id);
    END LOOP;
  
    -- Päivitetään sopimuksen materiaalin käyttö päiviltä, joilta materiaalia on poistettu TOTEUMA.alkanut-ajankohtaa muuttamalla. 
    -- Vanha alkanut-ajankohta löytyy taulusta MATERIAALIVALIMUISTI_PAIVITYSTARVE.
    FOR rivi IN
        SELECT DISTINCT
            mp.id AS muutos_id,
            mp.toteuma_alkanut_vanha::date AS pvm
        FROM materiaalivalimuisti_paivitystarve mp
        JOIN toteuma t ON t.id = mp.toteuma_id
        WHERE mp.urakka_id = u_id
          AND t.sopimus = sopimus_id
          AND mp.sopimuksen_valimuisti_paivitetty = FALSE
    LOOP
        BEGIN
            PERFORM paivita_sopimuksen_materiaalin_kaytto(sopimus_id, rivi.pvm, urakka_id);
            kasitellyt_muutos_idt := array_append(kasitellyt_muutos_idt, rivi.muutos_id);
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'Materiaalikäytön päivitys epäonnistui sopimukselle %, päivälle %: %', sopimus_id, rivi.pvm, SQLERRM;
        END;
    END LOOP;
  
    -- Merkitään vain käsitellyt toteuma_muutos rivit päivitetyiksi
    IF array_length(kasitellyt_muutos_idt, 1) > 0 THEN
        UPDATE materiaalivalimuisti_paivitystarve
        SET sopimuksen_valimuisti_paivitetty = TRUE,
            muokattu = CURRENT_TIMESTAMP,
            muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
        WHERE id = ANY(kasitellyt_muutos_idt);
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Päivittää tietyn sopimuksen kaiken materiaalin käytön
CREATE OR REPLACE FUNCTION paivita_koko_sopimuksen_materiaalin_kaytto(
  sopimus INTEGER)
RETURNS void AS $$
DECLARE
  mat RECORD;
  sop INTEGER;
BEGIN
  sop := sopimus;
  DELETE FROM sopimuksen_kaytetty_materiaali skm WHERE skm.sopimus = sop;
  FOR mat IN SELECT date_trunc('day',t.alkanut) as alkupvm, tm.materiaalikoodi,
                    SUM(CASE
                          WHEN (t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE)
                          THEN tm.maara
                          ELSE 0
                        END) as maara
               FROM toteuma t
                    JOIN toteuma_materiaali tm ON tm.toteuma = t.id
              WHERE t.sopimus = sop
              GROUP BY date_trunc('day',t.alkanut), tm.materiaalikoodi
  LOOP
    INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
    VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara, current_timestamp);
  END LOOP;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION paivita_kaikki_sopimuksen_kaytetty_materiaali() RETURNS void AS $$
BEGIN
  -- Poistetaan kaikki
  DELETE FROM sopimuksen_kaytetty_materiaali;

  -- Luodaan uudet haun perusteella
  INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
      SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara), current_timestamp
        FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
       WHERE t.poistettu IS NOT TRUE and tm.poistettu IS NOT TRUE
	GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
END;
$$ LANGUAGE plpgsql;

-- Allaoleva funktio päivittää sopimuksen käytetyn materiaalin cachen päivämääräväliltä
-- Se voidaan suorittaa hätätilanteessa esim. SQL-tulkin avulla tuotantokantaa vasten AINA transaktion sisällä (BEGIN... do stuff; COMMIT/ROLLBACK;)
-- Mahdolliset rivien deletoinnit tehtävä käsin ennen ajoa.
CREATE OR REPLACE FUNCTION paivita_sopimuksen_kaytetty_materiaali_pvm_aikavalille(alku DATE, loppu DATE)
RETURNS void AS $$
DECLARE
BEGIN
        INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
        SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara), current_timestamp
          FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
         WHERE t.poistettu IS NOT TRUE and tm.poistettu IS NOT TRUE
            AND t.alkanut BETWEEN alku AND (select date_trunc('day', loppu) + interval '1 day' - interval '1 second')
         GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
    RETURN;
END;
$$ LANGUAGE plpgsql;
