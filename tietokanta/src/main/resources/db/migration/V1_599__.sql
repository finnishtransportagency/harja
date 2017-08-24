-- Vesiväylien indeksit
INSERT INTO urakkatyypin_indeksi(urakkatyyppi, indeksinimi, koodi, raakaaine)
VALUES
  ('vesivayla-hoito'::urakkatyyppi, 'MAKU 2005 kp-osaindeksi', NULL, NULL),
  ('vesivayla-hoito'::urakkatyyppi, 'MAKU 2010 kp-osaindeksi', NULL, NULL);


-- indeksilaskennan sprocista tehdään useaa urakkatyyppiä tukeva, uudelleennimetään funktio
-- tiedostossa R__Indeksilaskenta.sql ja pudotetaan vanha tässä migraatiossa
DROP FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER, indeksinimi VARCHAR);