-- Lisää toteumille TR-tiedot (käytetään kun tallennetaan frontilta)

ALTER TABLE toteuma
ADD tr_numero INTEGER,
ADD tr_alkuosa INTEGER,
ADD tr_alkuetaisyys INTEGER,
ADD tr_loppuosa INTEGER,
ADD tr_loppuetaisyys INTEGER;