-- Toteumalle pakollisia arvoja

DELETE FROM reitti_materiaali WHERE reittipiste IN (SELECT id FROM reittipiste WHERE toteuma IN (SELECT id FROM toteuma WHERE alkanut IS NULL or paattynyt IS NULL));
DELETE FROM reitti_tehtava WHERE reittipiste IN (SELECT id FROM reittipiste WHERE toteuma IN (SELECT id FROM toteuma WHERE alkanut IS NULL or paattynyt IS NULL));
DELETE FROM reittipiste WHERE toteuma IN (SELECT id FROM toteuma WHERE alkanut IS NULL or paattynyt IS NULL);
DELETE FROM varustetoteuma WHERE toteuma IN (SELECT id FROM toteuma WHERE alkanut IS NULL or paattynyt IS NULL);
DELETE FROM toteuma_materiaali WHERE toteuma IN (SELECT id FROM toteuma WHERE alkanut IS NULL or paattynyt IS NULL);;
DELETE FROM toteuma_tehtava WHERE maara IS NULL;
DELETE FROM toteuma WHERE alkanut IS NULL or paattynyt IS NULL);

ALTER TABLE toteuma ALTER alkanut SET NOT NULL;
ALTER TABLE toteuma ALTER paattynyt SET NOT NULL;
ALTER TABLE toteuma_tehtava ALTER maara SET NOT NULL;