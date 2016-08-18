-- Levennä toteuman lisätietokenttää
-- Kaikki AURAsta siirretyt lisätiedot eivät mahdu Harjan kenttäleveyteen
ALTER TABLE toteuma ALTER COLUMN lisatieto TYPE TEXT;
