-- Indeksoi toteuman reitti
CREATE INDEX toteuma_reitti_idx ON toteuma USING GIST (reitti);
