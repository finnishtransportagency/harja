-- CASCADE Poista reittipisteen tehtävät ja materiaalit
ALTER TABLE reitti_tehtava
 DROP CONSTRAINT reitti_tehtava_reittipiste_fkey,
 ADD CONSTRAINT reitti_tehtava_reittipiste_fkey
     FOREIGN KEY (reittipiste) REFERENCES reittipiste (id)
     ON DELETE CASCADE;

ALTER TABLE reitti_materiaali
 DROP CONSTRAINT materiaalitoteuma_reittipiste_fkey,
 ADD CONSTRAINT materiaalitoteuma_reittipiste_fkey
     FOREIGN KEY (reittipiste) REFERENCES reittipiste (id)
     ON DELETE CASCADE;
