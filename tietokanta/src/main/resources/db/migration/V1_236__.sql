-- Merkitse aika pakolliseksi reittipisteille. Päivittää kaikille reittipisteille, joilla ei ole aikaa toteuman alkuajan.
UPDATE reittipiste AS rp
SET aika = t.alkanut
FROM toteuma AS t
WHERE rp.toteuma = t.id;

ALTER TABLE reittipiste ALTER COLUMN aika SET NOT NULL;