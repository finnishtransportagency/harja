UPDATE urakkatyypin_indeksi
   SET indeksinimi = 'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi'
 WHERE urakkatyyppi = 'vesivayla-hoito' AND indeksinimi = 'MAKU 2010 ylläpidon kokonaisindeksi';

UPDATE indeksi
   SET nimi = 'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi'
 WHERE nimi = 'MAKU 2010 ylläpidon kokonaisindeksi';

UPDATE urakka
   SET indeksi = 'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi'
 WHERE indeksi = 'MAKU 2010 ylläpidon kokonaisindeksi' and tyyppi = 'vesivayla-hoito';