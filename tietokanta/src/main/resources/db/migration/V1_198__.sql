ALTER TABLE materiaalikoodi ADD COLUMN nimi_lyhenne VARCHAR(64);
UPDATE materiaalikoodi SET nimi_lyhenne = 'CaCl12' WHERE nimi = 'Talvisuolaliuos CaCl2';
UPDATE materiaalikoodi SET nimi_lyhenne = 'eri. NaCl' WHERE nimi = 'Erityisalueet NaCl';
UPDATE materiaalikoodi SET nimi_lyhenne = 'eri. NaCl-liuos' WHERE nimi = 'Erityisalueet NaCl-liuos';
UPDATE materiaalikoodi SET nimi_lyhenne = 'h.hiekka' WHERE nimi = 'Hiekoitushiekka';
UPDATE materiaalikoodi SET nimi_lyhenne = 'kal.form' WHERE nimi = 'Kaliumformiaatti';
UPDATE materiaalikoodi SET nimi_lyhenne = 'NaCl (rak)' WHERE nimi = 'Talvisuola NaCl, rakeinen';
UPDATE materiaalikoodi SET nimi_lyhenne = 'NaCl' WHERE nimi = 'Talvisuolaliuos NaCl';