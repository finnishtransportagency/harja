ALTER TABLE pohjavesialue_talvisuola ADD tie INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pohjavesialue_talvisuola ADD PRIMARY KEY(pohjavesialue, tie);
