ALTER TABLE reittipiste ADD COLUMN sijainti POINT;

UPDATE reittipiste SET sijainti = ST_MAKEPOINT(reittipiste.x, reittipiste.y, reittipiste.y)::POINT;

ALTER TABLE reittipiste DROP COLUMN x;
ALTER TABLE reittipiste DROP COLUMN y;
ALTER TABLE reittipiste DROP COLUMN z;