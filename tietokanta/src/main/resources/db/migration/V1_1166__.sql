-- Lisätään uusi sarake, joka määrittää onko tehtävän valinta pakollinen uutta kulua luotaessa
ALTER TABLE tehtava 
ADD COLUMN pakollinen_uudessa_kulussa BOOLEAN DEFAULT FALSE;

-- Lisätään uusi sarake tehtavalle kohdistus-tauluun, joka viittaa tehtävään
ALTER TABLE kulu_kohdistus 
ADD COLUMN tehtava INTEGER REFERENCES tehtava(id);