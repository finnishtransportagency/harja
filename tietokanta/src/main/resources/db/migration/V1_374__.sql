-- Lisää indeksit reittipistelle ja sen tehtäville/materiaaleille
CREATE INDEX reittipiste_toteuma ON reittipiste (toteuma);
CREATE INDEX reitti_tehtava_reittipiste ON reitti_tehtava (reittipiste);
CREATE INDEX reitti_materiaali_reittipiste ON reitti_materiaali (reittipiste);
