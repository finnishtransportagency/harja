-- Pohjavesialueet urakalle

-- Pohjavesialueet tulevat PTJ:stä tiepätkinä, ei yhtenä uniikkina alueena
-- Haetaan näkymään urakan pohjavesialueet.
-- Tehdään ST_UNION kutsulla tiepätkistä yksi multilinestring

-- Huom: polygonialueen laskeminen ST_BUFFER avulla epäonnistui, koska
-- tuli virhe TopologyException: depth mismatch


CREATE MATERIALIZED VIEW pohjavesialueet_urakoittain AS
  WITH
  urakat_alueet AS (
     SELECT u.id, au.alue
       FROM urakka u
            JOIN hanke h ON u.hanke = h.id
	    JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
      WHERE u.tyyppi = 'hoito'::urakkatyyppi),
  pohjavesialue_alue AS (
      SELECT p.nimi, p.tunnus, ST_UNION(p.alue) as alue
        FROM pohjavesialue p GROUP BY nimi, tunnus)
  SELECT pa.nimi, pa.tunnus, pa.alue, ua.id as urakka
    FROM pohjavesialue_alue pa
         CROSS JOIN urakat_alueet ua
   WHERE ST_CONTAINS(ua.alue, pa.alue);
	 
	
