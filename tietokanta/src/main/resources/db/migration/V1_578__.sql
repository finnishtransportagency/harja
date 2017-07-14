-- Poistetaan vanhan malliset reittipisteet


-- Poistetaan siirron funktiot, jotka viittavaat tauluun
DROP FUNCTION siirra_kaikki_reittipisteet();
DROP FUNCTION siirra_reittipisteet(INTEGER);

-- Poistetaan reittipisteeseen linkatut tehtävät ja materiaalit
DROP TABLE reitti_tehtava;
DROP TABLE reitti_materiaali;


-- Poistetaan itse reittipistetaulu
DROP TABLE reittipiste;
