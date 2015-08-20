DROP MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain;
CREATE MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain AS
       SELECT p.id, p.nimi, p.alue, p.tunnus, 
              (SELECT id FROM organisaatio o
	        WHERE tyyppi='hallintayksikko'::organisaatiotyyppi AND ST_CONTAINS(o.alue, p.alue)) as hallintayksikko
	 FROM pohjavesialue p;
