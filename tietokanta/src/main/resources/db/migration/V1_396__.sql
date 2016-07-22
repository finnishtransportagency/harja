-- Indeksoi toteuman reitti
CREATE toteuma_reitti_idx ON toteuma USING GIST (reitti);
