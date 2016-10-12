-- Lisää indeksi kenttä toteuma_tehtavalle

-- Voi olla muutos- ja lisätöitä, joissa ei
-- käytetä indeksiä.

ALTER TABLE toteuma_tehtava
  ADD COLUMN indeksi BOOLEAN DEFAULT TRUE;


-- Poistetaan kaikki cachet, koska laskutusyhteenveto muuttunut
DELETE FROM laskutusyhteenveto_cache;
