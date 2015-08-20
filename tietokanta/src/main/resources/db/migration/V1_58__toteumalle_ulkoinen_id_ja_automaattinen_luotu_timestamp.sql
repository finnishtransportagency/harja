ALTER TABLE toteuma ALTER COLUMN luotu SET DEFAULT current_timestamp;
ALTER TABLE reittipiste ALTER COLUMN luotu SET DEFAULT current_timestamp;
ALTER TABLE reitti_tehtava ALTER COLUMN luotu SET DEFAULT current_timestamp;
ALTER TABLE reitti_materiaali ALTER COLUMN luotu SET DEFAULT current_timestamp;

ALTER TABLE toteuma ADD COLUMN ulkoinen_id varchar(64);