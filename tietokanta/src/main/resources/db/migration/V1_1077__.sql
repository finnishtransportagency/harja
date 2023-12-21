-- Contstraint check on siirretty koodiin, jotta pg_dump -> pg_restore putki toimisi oikein
-- Samalla poistetaan turha viittaus tehtävä tauluun.
ALTER TABLE kulu_kohdistus
    DROP CONSTRAINT lasku_kohdistus_tehtava_tehtavaryhma_toimenpideinstanssi_oikein,
    DROP COLUMN tehtava;
