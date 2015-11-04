-- Lisää TR-testidatan vaatimia toimenpidekoodeja

-- Päivitä pari josta löytyi typo:
UPDATE toimenpidekoodi
SET nimi = 'Linjamerkinnät massalla, paksuus 3 mm: Sulkuviiva ja varoitusviiva keltainen'
WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm:Sulkuviiva ja varoitusviiva keltainen'

UPDATE toimenpidekoodi
SET nimi = 'Linjamerkinnät massalla, paksuus 3 mm: Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen'
WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm:Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen'

UPDATE toimenpidekoodi
SET nimi = 'Pienmerkinnät massalla paksuus 7 mm: Pyörätien jatkeet ja suojatiet'
WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm:Pyörätien jatkeet ja suojatiet'

-- Lisää uudet:
SELECT lisaa_tai_paivita_toimenpidekoodi('Pienmerkinnät massalla paksuus 7 mm: Pyörätien jatkeet ja suojatiet', NULL, 4, 'm2',NULL,false,false,'Laaja toimenpide','20123',3);
SELECT lisaa_tai_paivita_toimenpidekoodi('Muut pienmerkinnät', NULL, 4, 'm2',NULL,false,false,'Laaja toimenpide','20123',3);
SELECT lisaa_tai_paivita_toimenpidekoodi('Nuolet ja nopeusrajoitusmerkinnät ja väistämisviivat', NULL, 4, 'kpl', NULL,false,false,'Laaja toimenpide','20123',3);
SELECT lisaa_tai_paivita_toimenpidekoodi('Sulkualueet', NULL, 4, 'm2',NULL,false,false,'Laaja toimenpide','20123',3);
