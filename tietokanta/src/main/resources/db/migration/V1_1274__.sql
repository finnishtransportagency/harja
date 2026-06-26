-- Nimetaan bonus_profiili_rivi-taulun sarakkeet selkeammiksi:
--   toimenpideinstanssi_t2_koodi         -> toimenpide_t2_koodi
--   toimenpideinstanssi_rajauksen_tyyppi -> toimenpiderajauksen_tyyppi
--
-- Perustelut: t2-koodi loytyy toimenpide-taulusta, ei toimenpideinstanssi-taulusta.
-- Ketju: toimenpideinstanssi.toimenpide -> t3 -> t3.emo = t2 -> t2.koodi

ALTER TABLE bonus_profiili_rivi
  RENAME COLUMN toimenpideinstanssi_t2_koodi
              TO toimenpide_t2_koodi;

ALTER TABLE bonus_profiili_rivi
  RENAME COLUMN toimenpideinstanssi_rajauksen_tyyppi
              TO toimenpiderajauksen_tyyppi;

-- Paivitetaan CHECK-rajoitteet (poistetaan nimet generoimattomat vanhat, lisataan nimetyt uudet).
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT conname
      FROM pg_constraint
     WHERE conrelid = 'bonus_profiili_rivi'::regclass
       AND contype = 'c'
  LOOP
    EXECUTE format('ALTER TABLE bonus_profiili_rivi DROP CONSTRAINT %I', r.conname);
  END LOOP;
END;
$$;

ALTER TABLE bonus_profiili_rivi
  ADD CONSTRAINT bonus_profiili_rivi_toimenpiderajauksen_tyyppi_check
    CHECK (toimenpiderajauksen_tyyppi IN ('kaikki', 't2-koodi')),
  ADD CONSTRAINT bonus_profiili_rivi_toimenpide_t2_koodi_check
    CHECK (
        (toimenpiderajauksen_tyyppi = 'kaikki'   AND toimenpide_t2_koodi IS NULL)
        OR
        (toimenpiderajauksen_tyyppi = 't2-koodi' AND toimenpide_t2_koodi IS NOT NULL
                                                  AND btrim(toimenpide_t2_koodi) <> '')
    );

-- Paivitetaan indeksit uusille sarakkeiden nimille
DROP INDEX bonus_profiili_rivi_kaikki_unique_idx;
DROP INDEX bonus_profiili_rivi_t2_unique_idx;
DROP INDEX bonus_profiili_rivi_haku_idx;

CREATE UNIQUE INDEX bonus_profiili_rivi_kaikki_unique_idx
    ON bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id)
    WHERE toimenpiderajauksen_tyyppi = 'kaikki';

CREATE UNIQUE INDEX bonus_profiili_rivi_t2_unique_idx
    ON bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id, toimenpide_t2_koodi)
    WHERE toimenpiderajauksen_tyyppi = 't2-koodi';

CREATE INDEX bonus_profiili_rivi_haku_idx
    ON bonus_profiili_rivi (bonus_profiili_id, toimenpiderajauksen_tyyppi, toimenpide_t2_koodi, aktiivinen, jarjestys);
