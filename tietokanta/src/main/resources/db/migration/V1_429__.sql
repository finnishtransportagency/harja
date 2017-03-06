-- Lisää TR osalle envelope ja indeksi

-- Poistetaan vanha geom indeksi
DROP INDEX tr_osan_ajorata_geom_idx;

-- Lisätään envelope
ALTER TABLE tr_osan_ajorata ADD COLUMN envelope GEOMETRY;
CREATE INDEX tr_osan_ajorata_envelope_idx ON tr_osan_ajorata USING GIST (envelope);

UPDATE tr_osan_ajorata SET envelope = ST_Expand(ST_Envelope(geom), 250);
