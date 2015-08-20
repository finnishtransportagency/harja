ALTER TABLE toteuma DROP COLUMN toimenpidekoodi;

ALTER TABLE materiaalitoteuma RENAME TO reitti_materiaali;
ALTER TABLE reitti_materiaali DROP COLUMN toteuma;
ALTER TABLE reitti_materiaali RENAME COLUMN materiaali TO materiaalikoodi;

CREATE TABLE reitti_tehtava (
  id serial primary key,
  reittipiste integer references reittipiste (id),
  luotu timestamp,
  toimenpidekoodi integer REFERENCES toimenpidekoodi (id),
  maara numeric
);

CREATE TABLE toteuma_tehtava (
  id serial primary key,
  toteuma integer references toteuma (id),
  luotu timestamp,
  toimenpidekoodi integer REFERENCES toimenpidekoodi (id),
  maara numeric
);

CREATE TABLE toteuma_materiaali (
  id serial primary key,
  toteuma integer references toteuma (id),
  luotu timestamp,
  materiaalikoodi integer REFERENCES materiaalikoodi (id),
  maara numeric
);