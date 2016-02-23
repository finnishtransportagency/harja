-- Tuhoa päivystäjätekstiviestit automaattisesti poistettaessa päivystäjä
ALTER TABLE paivystajatekstiviesti
DROP CONSTRAINT paivystajatekstiviesti_yhteyshenkilo_fkey,
ADD CONSTRAINT paivystajatekstiviesti_yhteyshenkilo_fkey
FOREIGN KEY (yhteyshenkilo)
REFERENCES yhteyshenkilo (id)
ON DELETE CASCADE;