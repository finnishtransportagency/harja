-- toteuma_materiaali vaatii materiaalikoodin ja määrän
--
DELETE FROM toteuma_materiaali WHERE materiaalikoodi IS NULL;

ALTER TABLE toteuma_materiaali
  ALTER COLUMN materiaalikoodi SET NOT NULL,
  ALTER COLUMN maara SET NOT NULL;