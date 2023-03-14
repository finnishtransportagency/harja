-- Poistetaan vanha versio laske_kuukauden_indeksikorotus sprocista
-- Uusi versio sprocista ottaa vastaan parameterin, jolla voi halutessaan pyöristää indeksin

DROP FUNCTION IF EXISTS laske_kuukauden_indeksikorotus(v INTEGER, kk INTEGER, indeksinimi VARCHAR, summa NUMERIC, perusluku NUMERIC)
