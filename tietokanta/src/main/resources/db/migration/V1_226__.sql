-- Lisää TR-testidatan vaatimia toimenpidekoodeja

SELECT lisaa_tai_paivita_toimenpidekoodi('Linjamerkinnät massalla, paksuus 3 mm:Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen', 4, 'm2', (SELECT id FROM toimenpidekoodi WHERE koodi='20123'));
SELECT lisaa_tai_paivita_toimenpidekoodi('Linjamerkinnät massalla, paksuus 3 mm:Sulkuviiva ja varoitusviiva keltainen', 4, 'm2', (SELECT id FROM toimenpidekoodi WHERE koodi='20123'));
SELECT lisaa_tai_paivita_toimenpidekoodi('Pienmerkinnät massalla paksuus 7 mm:Pyörätien jatkeet ja suojatiet', 4, 'm2', (SELECT id FROM toimenpidekoodi WHERE koodi='20123'));
SELECT lisaa_tai_paivita_toimenpidekoodi('Muut pienmerkinnät', 4, 'm2', (SELECT id FROM toimenpidekoodi WHERE koodi='20123'));
