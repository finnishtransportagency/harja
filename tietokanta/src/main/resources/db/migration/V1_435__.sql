-- Lisää indeksi kenttä toteuma_tehtavalle

-- Voi olla muutos- ja lisätöitä, joissa ei
-- käytetä indeksiä.

ALTER TABLE toteuma_tehtava
  ADD COLUMN indeksi BOOLEAN DEFAULT TRUE;

-- Päivän hinnalla laskutettaviin ei oletuksena indeksiä
UPDATE toteuma_tehtava
   SET indeksi = FALSE
 WHERE paivan_hinta IS NOT NULL;

-- Poistetaan kaikki cachet, koska laskutusyhteenveto muuttunut
DELETE FROM laskutusyhteenveto_cache;
