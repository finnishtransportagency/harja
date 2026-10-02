ALTER TABLE tehtava
ADD COLUMN "laske-api-maara-mukaan?" BOOLEAN;

COMMENT ON COLUMN tehtava."laske-api-maara-mukaan?" is 'True, jos urakoitsijajärjestelmästä Harjaan tuotu, (yleensä) koneellisesti kerätty toteumamäärä lasketaan mukaan tehtävän toteuman yhteismäärään Harjan näkymässä Toteumat > Tehtävät.';
