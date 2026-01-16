-- Lisää "sorateiden pinnan hoito" suoritettavatehtava-enumiin
ALTER TYPE suoritettavatehtava ADD VALUE 'sorateiden pinnan hoito' AFTER 'sorateiden polynsidonta';

-- Päivitä tehtävän suoritettavatehtava-sarake
UPDATE tehtava
SET suoritettavatehtava = 'sorateiden pinnan hoito'
WHERE id = 2869;
