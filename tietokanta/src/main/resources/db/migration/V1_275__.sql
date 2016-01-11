-- Toteumalle pakollisia arvoja

DELETE FROM toteuma WHERE alkanut IS NULL;
DELETE FROM toteuma WHERE paattynyt IS NULL;
DELETE FROM toteuma_tehtava WHERE maara IS NULL;

ALTER TABLE toteuma ALTER alkanut SET NOT NULL;
ALTER TABLE toteuma ALTER paattynyt SET NOT NULL;
ALTER TABLE toteuma_tehtava ALTER maara SET NOT NULL;