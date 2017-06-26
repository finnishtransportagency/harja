CREATE TABLE toteuma_liite (
  toteuma INTEGER REFERENCES toteuma (id),
  liite INTEGER REFERENCES liite (id)
);
