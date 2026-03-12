-- Päivittää (delete + upsert) reittitoteuman kirjauksen yhteydessä hoitoluokittaiset määrät
CREATE OR REPLACE FUNCTION paivita_urakan_materiaalin_kaytto_hoitoluokittain (
  urakka_id INTEGER, alkupvm DATE, loppupvm DATE)
  RETURNS void AS $$
DECLARE
  rivi RECORD;
  u INTEGER;
BEGIN
  u := urakka_id;

  -- Poista hoitoluokittainen materiaalicache kaikille reittipisteiden pvm:ille tässä urakassa
  DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain
  WHERE pvm BETWEEN alkupvm AND (loppupvm + interval '1 day')::DATE
        AND (u IS NULL or urakka = u);

  -- Päivitä materiaalin käyttö ko. pvm:lle ja urakalle
  FOR rivi IN SELECT t.urakka, rp.talvihoitoluokka AS talvihoitoluokka, rp.soratiehoitoluokka, mat.materiaalikoodi,
                               sum(mat.maara) as summa,
                rp.aika::DATE
              FROM toteuma t
                JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
                JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                JOIN LATERAL unnest(rp.materiaalit) mat ON true
              WHERE t.alkanut BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE
                    AND (u IS NULL OR t.urakka = u) AND t.poistettu IS FALSE
              GROUP BY t.urakka, rp.talvihoitoluokka, rp.soratiehoitoluokka, mat.materiaalikoodi, rp.aika::DATE
  -- Tässä otetaan talteen erikoiskäsittelyllä kaikki käsin syötetyt toteumat, ja annetaan niille
  -- hoitoluokaksi 99 eli 'Käsin kirjattu'
  UNION
  SELECT t.urakka,
         (CASE
            WHEN m.materiaalityyppi = 'talvisuola' THEN 99
            WHEN m.materiaalityyppi = 'formiaatti' THEN 99
            ELSE NULL
         END) AS talvihoitoluokka,
         (CASE
              WHEN m.materiaalityyppi = 'kesasuola' THEN 99
              ELSE NULL
             END) AS soratiehoitoluokka,
         tm.materiaalikoodi,
         sum(tm.maara) as summa,
         t.alkanut::DATE as aika
    FROM toteuma t
             JOIN toteuma_materiaali tm ON tm.toteuma = t.id and tm.poistettu IS FALSE
             JOIN materiaalikoodi m on tm.materiaalikoodi = m.id
   WHERE t.lahde = 'harja-ui' and
         t.alkanut BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE
         AND (u IS NULL OR t.urakka = u) AND t.poistettu IS FALSE
   GROUP BY t.urakka, talvihoitoluokka, soratiehoitoluokka, tm.materiaalikoodi, t.alkanut::DATE
  LOOP
    INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
    (pvm, materiaalikoodi, talvihoitoluokka, soratiehoitoluokka, urakka, maara, muokattu)
    VALUES (rivi.aika,
            rivi.materiaalikoodi,
            COALESCE(rivi.talvihoitoluokka, 100),
            COALESCE(rivi.soratiehoitoluokka, 100),
            rivi.urakka,
            rivi.summa,
            current_timestamp)
    ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
    UPDATE SET maara = urakan_materiaalin_kaytto_hoitoluokittain.maara + EXCLUDED.maara,
               muokattu = current_timestamp;
  END LOOP;

END;
$$ LANGUAGE plpgsql;


-- Päivittää (delete + insert) reittitoteuman kirjauksen yhteydessä hoitoluokittaiset määrät, annetulle ajanjaksolle. 
-- Käytetään ajastetussa tehtävässä, joka päivittää urakan_materiaalinkaytto_hoitoluokittain välimuistitaulun joka yö.
-- Välimuistitaulussa päivitettävät rivit rajataan urakalla ja päivämäärällä. Päivämäärät saadaan hakemalla
-- niiden toteumien reittipisteiden ajankohdat, jotka saadaan rajaamalla tälle proseduurille parametrina annetuilla tiedoilla:
-- urakka ja luonti tai muokkausajankohta.
--
-- 1. Proseduuri hakee relevantit toteumat urakan (urakka) ja luonti/muokkausaikaleiman (alkupvm-loppupvm) perusteella.
-- 2. Proseduuri hakee välimuistissa päivitettävät päivämäärät palautuneiden toteumien reittipisteistä (aika) ja käsinkirjattujen toteumien alkanut-sarakkeesta.
-- 3. Proseduuri poistaa välimuistitaulusta urakan päivämäärää koskevat rivit.
-- 4. Proseduuri insertoi välimuistitauluun uudet päivämäärää koskevat rivit.
CREATE OR REPLACE FUNCTION paivita_urakan_materiaalikaytto_hoitoluokittain_muutospaivalla (
  urakka_id INTEGER, alkupvm DATE, loppupvm DATE)
  RETURNS void AS $$
DECLARE
  rivi RECORD;
  u INTEGER;
BEGIN
  u := urakka_id;

  -- Päivitä materiaalin käyttö ko. pvm:lle ja urakalle
  FOR rivi IN SELECT t.urakka, rp.talvihoitoluokka AS talvihoitoluokka, rp.soratiehoitoluokka, mat.materiaalikoodi,
                               sum(CASE WHEN t.poistettu IS TRUE THEN 0 ELSE mat.maara END) as summa,
                rp.aika::DATE
              FROM toteuma t
                JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
                JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                JOIN LATERAL unnest(rp.materiaalit) mat ON true
              WHERE ((t.luotu BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE) OR (t.muokattu BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE))
                    AND urakka = u
              GROUP BY t.urakka, rp.talvihoitoluokka, rp.soratiehoitoluokka, mat.materiaalikoodi, rp.aika::DATE
  -- Tässä otetaan talteen erikoiskäsittelyllä kaikki käsin syötetyt toteumat, ja annetaan niille
  -- hoitoluokaksi 99 eli 'Käsin kirjattu'
  UNION
  SELECT t.urakka,
         (CASE
            WHEN m.materiaalityyppi = 'talvisuola' THEN 99
            WHEN m.materiaalityyppi = 'formiaatti' THEN 99
            ELSE NULL
         END) AS talvihoitoluokka,
         (CASE
              WHEN m.materiaalityyppi = 'kesasuola' THEN 99
              ELSE NULL
             END) AS soratiehoitoluokka,
         tm.materiaalikoodi,
         sum(tm.maara) as summa,
         t.alkanut::DATE as aika
    FROM toteuma t
             JOIN toteuma_materiaali tm ON tm.toteuma = t.id and tm.poistettu IS FALSE
             JOIN materiaalikoodi m on tm.materiaalikoodi = m.id
   WHERE t.lahde = 'harja-ui' and
         ((t.luotu BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE) OR (t.muokattu BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE))
                    AND urakka = u AND t.poistettu IS FALSE
   GROUP BY t.urakka, talvihoitoluokka, soratiehoitoluokka, tm.materiaalikoodi, t.alkanut::DATE
  LOOP
    -- Poista päivitettävä materiaalicacherivi
    DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain
    WHERE pvm = rivi.aika 
          AND urakka = u
          AND materiaalikoodi = rivi.materiaalikoodi
          AND talvihoitoluokka = COALESCE(rivi.talvihoitoluokka, 100)
          AND soratiehoitoluokka = COALESCE(rivi.soratiehoitoluokka, 100);
    INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
    (pvm, materiaalikoodi, talvihoitoluokka, soratiehoitoluokka, urakka, maara, muokattu)
    VALUES (rivi.aika,
            rivi.materiaalikoodi,
            COALESCE(rivi.talvihoitoluokka, 100),
            COALESCE(rivi.soratiehoitoluokka, 100),
            rivi.urakka,
            rivi.summa,
            current_timestamp);
  END LOOP;

END;
$$ LANGUAGE plpgsql;


-- Päivitä kaikki materiaalin käyttö päivämäärävälille. Huom! Tätä ei tällä hetkellä käytetä sovelluslogiikassa,
-- mutta tämä on voimakas työkalu jos tarvii päivitellä tuotannossa datoja. Käytä harkiten.
CREATE OR REPLACE FUNCTION paivita_materiaalin_kaytto_hoitoluokittain_aikavalille(alku DATE, loppu DATE)
  RETURNS VOID AS $$
DECLARE
BEGIN
    -- Tässä passataan urakka-parametriksi NULL, jolloin päivitetään kaikki urakat ko. aikaväliltä
    PERFORM paivita_urakan_materiaalin_kaytto_hoitoluokittain(NULL, alku, loppu);
  RETURN;
END;
$$ LANGUAGE plpgsql;
