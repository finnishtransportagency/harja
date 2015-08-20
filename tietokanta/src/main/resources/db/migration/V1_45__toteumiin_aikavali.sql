-- Pudottaa toteumataulusta aikaleiman ja korvaa sen aikavälillä

ALTER TABLE toteuma DROP COLUMN aika;
ALTER TABLE toteuma ADD COLUMN alkanut timestamp; -- Aika jolloin toteuma alkoi
ALTER TABLE toteuma ADD COLUMN paattynyt timestamp; -- Aika jolloin toteuma päättyi