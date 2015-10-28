-- Lisää talvisuolan käyttöraja

-- Talvisuolan käyttöraja
ALTER TABLE suolasakko ADD COLUMN talvisuolaraja NUMERIC;

-- Onko talvisuolan rajoitukset ja sakot lainkaan käytössä
ALTER TABLE suolasakko ADD COLUMN kaytossa BOOLEAN NOT NULL DEFAULT true;

-- Pohjavesialueiden talvisuolarajat
CREATE TABLE pohjavesialue_talvisuola (
  pohjavesialue varchar(16), -- pohjavesialueen tunnus
  urakka INTEGER REFERENCES urakka (id),
  hoitokauden_alkuvuosi smallint, 
  talvisuolaraja NUMERIC
);

CREATE INDEX pohjavesialue_talvisuola_urakka ON pohjavesialue_talvisuola (urakka);


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
	 
	

-- Pohjavesialueet näkymien päivitys
DROP FUNCTION paivita_hallintayksikoiden_pohjavesialueet ();

CREATE OR REPLACE FUNCTION paivita_pohjavesialueet()
  RETURNS VOID
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain;
  REFRESH MATERIALIZED VIEW pohjavesialueet_urakoittain;
RETURN;
END;
$$ LANGUAGE plpgsql;
;
