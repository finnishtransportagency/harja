-- Jotta työmaapäiväkirjaan saadaan näkyville muutoshistoriatiedot, tarvimme lisätty sarakkeen
ALTER TABLE tyomaapaivakirja_kalusto ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_paivystaja ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_poikkeussaa ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_tapahtuma ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_saaasema ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_tieston_toimenpide ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_tyonjohtaja ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_toimeksianto ADD COLUMN IF NOT EXISTS muokattu timestamp;


DROP FUNCTION IF EXISTS tyomaapaivakirja_etsi_taulun_versiomuutokset(TEXT, TEXT[], TEXT[], INTEGER, INTEGER, INTEGER, INTEGER, TEXT);

CREATE OR REPLACE FUNCTION tyomaapaivakirja_etsi_taulun_versiomuutokset(
  t_taulu TEXT, -- taulu mistä haetaan
  t_sarakkeet TEXT[], -- sarakkeet mitä verrataan
  t_ei_verratut TEXT[], -- sarakkeet mitkä palautetaan mutta ei verrata 
  t_id INT, -- tyomaapaivakirja_id 
  t_urakka_id INT, -- urakka_id 
  t_vanha_versio INT, -- työmaapäiväkirjan vanha versio 
  t_uusi_versio INT, -- työmaapäiväkirjan usi versio 
  t_vastaa_sarakkeeseen TEXT -- OPTIONAL, tämä sarake täytyy olla sama verratessa, voi olla NULL 
) RETURNS TABLE (
  ACTION TEXT, -- 'lisatty', 'poistettu', 'muutettu'
  vanhat JSONB,
  uudet JSONB
) AS $$
DECLARE
  ehdot TEXT;
  on_ehto TEXT;
BEGIN
	
  -- Heitä exception koska tarvitaan jotain mitä verrataan
  IF t_ei_verratut = t_sarakkeet THEN
    RAISE EXCEPTION 'Ei verratut sarakkeet eivät voi olla samoja kun verratut sarakkeet';
  END IF;
    
  -- Generoi konditiot sarakkeille
  ehdot := array_to_string(
      ARRAY(
          SELECT format('uusi.%I <> vanha.%I', col, col)
          FROM unnest(t_sarakkeet) col
      ), ' OR '
  );
   
  -- Jos halutaan sarakkeet vastaamaan johonkin, tehdään se tässä
  IF t_vastaa_sarakkeeseen IS NULL THEN
    on_ehto := (SELECT string_agg(format('vanha.%I = uusi.%I', col, col), ' AND ') FROM unnest(t_sarakkeet) col WHERE col NOT IN (SELECT unnest(t_ei_verratut)));
  else 
    on_ehto := format('vanha.%I = uusi.%I', t_vastaa_sarakkeeseen, t_vastaa_sarakkeeseen);
  end IF;
   
  -- Valitaan parametrien mukaisista taulusta parametrien mukaiset sarakkeet missä havaittu muutoksia
  -- Katsotaan mikä on muuttunut, poistunut ja mitä lisätty 
  RETURN QUERY EXECUTE format('
  SELECT
    CASE
        WHEN %1$s THEN ''lisatty''
        WHEN %2$s THEN ''poistettu''
        ELSE
            CASE
                WHEN %3$s THEN ''sama''
                ELSE ''muutettu''
            END
    END AS toiminto,
    CASE
        WHEN %1$s THEN NULL
        ELSE jsonb_build_object(%4$s)
    END AS vanhat,
    CASE
        WHEN %2$s THEN NULL
        ELSE jsonb_build_object(%5$s)
    END AS uudet
  FROM (
      SELECT DISTINCT %6$s
      FROM %7$s
      WHERE tyomaapaivakirja_id = $3 AND versio = $2
  ) AS vanha
  FULL JOIN (
      SELECT DISTINCT %6$s
      FROM %7$s
      WHERE tyomaapaivakirja_id = $3 AND versio = $1
  ) AS uusi
  ON %8$s;
  ',
  (SELECT string_agg(format('vanha.%I IS NULL', col), ' AND ') FROM unnest(t_sarakkeet) col WHERE col NOT IN (SELECT unnest(t_ei_verratut))), -- lisatty
  (SELECT string_agg(format('uusi.%I IS NULL', col), ' AND ') FROM unnest(t_sarakkeet) col WHERE col NOT IN (SELECT unnest(t_ei_verratut))), -- poistettu 
  (SELECT string_agg(format('vanha.%I = uusi.%I', col, col), ' AND ') FROM unnest(t_sarakkeet) col WHERE col NOT IN (SELECT unnest(t_ei_verratut))), -- ei muutettu && on condition
  (SELECT string_agg(format('''%I'', vanha.%I', col, col), ', ') FROM unnest(t_sarakkeet) col), -- jsonb_build
  (SELECT string_agg(format('''%I'', uusi.%I', col, col), ', ') FROM unnest(t_sarakkeet) col), -- jsonb_build 
  (SELECT string_agg(format('%s', col), ', ') FROM unnest(t_sarakkeet) col), -- SELECT distinct
  t_taulu,
  on_ehto) USING t_uusi_versio, t_vanha_versio, t_id;
END;
$$ LANGUAGE plpgsql;
