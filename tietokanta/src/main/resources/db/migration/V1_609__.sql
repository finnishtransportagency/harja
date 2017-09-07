<<<<<<< HEAD
ALTER TABLE ilmoitus
  ADD COLUMN "aiheutti-toimenpiteita" BOOLEAN;
=======
-- Vesiväylien indeksit
INSERT INTO urakkatyypin_indeksi(urakkatyyppi, indeksinimi, koodi, raakaaine)
VALUES
  ('vesivayla-hoito'::urakkatyyppi, 'MAKU 2005 kunnossapidon osaindeksi', NULL, NULL),
  ('vesivayla-hoito'::urakkatyyppi, 'MAKU 2010 ylläpidon kokonaisindeksi', NULL, NULL);

UPDATE urakka
   SET indeksi = CASE
              WHEN alkupvm < '2017-8-1' THEN 'MAKU 2005 kunnossapidon osaindeksi'
              ELSE 'MAKU 2010 ylläpidon kokonaisindeksi' END
 WHERE tyyppi = 'vesivayla-hoito' and indeksi IS NULL;


-- indeksilaskennan sprocista tehdään useaa urakkatyyppiä tukeva, uudelleennimetään funktio
-- tiedostossa R__Indeksilaskenta.sql ja pudotetaan vanha tässä migraatiossa
DROP FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER, indeksinimi VARCHAR);
DROP FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER);
>>>>>>> develop
