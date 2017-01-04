-- Reittimerkintään tieto liittyvästä havainnosta
ALTER TABLE tarkastusreitti ADD COLUMN liittyy_merkintaan INTEGER;

-- Muuta yleishavainto pistemäiseksi.
-- Lomakkeelta kirjattu havainto on aina yleishavainto, paitsi jos liittyy johonkin toiseen havaintoon, mutta
-- silloin tyypillä ei ole väliä, sillä kirjatut tiedot ainoastaan lisätään liittyvään havaintoon
UPDATE vakiohavainto SET jatkuva = FALSE WHERE avain = 'yleishavainto';