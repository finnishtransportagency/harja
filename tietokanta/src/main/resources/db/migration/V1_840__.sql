-- Samassa migraatiotiedostossa ei voi olla transaktion sisällä olevia päivityksiä
-- ja ilman transaktiota pyöriviä päivitytksiä.
-- CONCURRENTLY indeksien luominen on non-transactional päivitys, joten ne on erotettu tähän erilliseen tiedostoon

-- Päivitä toteuma taulujen indeksejä, jotta suorituskyky paranee
CREATE INDEX CONCURRENTLY toteuma_urakka_alkanut_idx ON toteuma (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_010101_191001_urakka_alkanut_poistettu_idx ON toteuma_010101_191001 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_191001_200701_urakka_alkanut_poistettu_idx ON toteuma_191001_200701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_200701_210101_urakka_alkanut_poistettu_idx ON toteuma_200701_210101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_210101_210701_urakka_alkanut_poistettu_idx ON toteuma_210101_210701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_210701_220101_urakka_alkanut_poistettu_idx ON toteuma_210701_220101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_220101_220701_urakka_alkanut_poistettu_idx ON toteuma_220101_220701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_220701_230101_urakka_alkanut_poistettu_idx ON toteuma_220701_230101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_230101_230701_urakka_alkanut_poistettu_idx ON toteuma_230101_230701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_230701_240101_urakka_alkanut_poistettu_idx ON toteuma_230701_240101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_240101_240701_urakka_alkanut_poistettu_idx ON toteuma_240101_240701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_240701_250101_urakka_alkanut_poistettu_idx ON toteuma_240701_250101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_250101_991231_urakka_alkanut_poistettu_idx ON toteuma_250101_991231 (urakka, alkanut, poistettu);

-- Lisätään sitten indeksi toteuma_tehtava taululle, jotta suorituskyky paranee
CREATE INDEX CONCURRENTLY toteuma_tehtava_urakka_poistettu ON toteuma_tehtava (urakka_id, poistettu);
