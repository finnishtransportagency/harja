CREATE OR REPLACE FUNCTION ajoneuvo_suljetulle_tieosuudelle_test(suljettu_osuus_id INTEGER, tyokone INTEGER, ajoneuvotyyppi VARCHAR, urakkaid INTEGER, tehtavat suoritettavatehtava[]) RETURNS VOID AS $$
BEGIN
  WITH q AS (SELECT CAST(st_lineinterpolatepoint(geometria, random()) AS point) AS ajoneuvo_sijainti
               FROM suljettu_tieosuus 
              WHERE osuus_id=suljettu_osuus_id)
  INSERT INTO tyokonehavainto (tyokoneid, jarjestelma, organisaatio, viestitunniste, lahetysaika, vastaanotettu, tyokonetyyppi, sijainti, urakkaid, edellinensijainti, suunta, tehtavat) 
       VALUES (tyokone, 'Jarjestelma', 1, 123, current_timestamp, current_timestamp, ajoneuvotyyppi, (SELECT ajoneuvo_sijainti FROM q), urakkaid, (SELECT ajoneuvo_sijainti FROM q), 0, tehtavat)
     ON CONFLICT (jarjestelma,tyokoneid) DO UPDATE SET sijainti = EXCLUDED.sijainti, edellinensijainti = EXCLUDED.edellinensijainti;
END
$$ LANGUAGE plpgsql;
