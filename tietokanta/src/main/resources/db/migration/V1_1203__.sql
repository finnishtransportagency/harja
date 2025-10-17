-- Poistetaan tarjous_johto_ja_hallintokorvaus-taulusta columnit joita ei tarvittu ja lisätään maksukausi
ALTER TABLE tarjous_johto_ja_hallintokorvaus
    DROP COLUMN IF EXISTS tehtavaryhma_id,
    DROP COLUMN IF EXISTS tehtava_id,
    ADD COLUMN IF NOT EXISTS maksukausi VARCHAR(20);
