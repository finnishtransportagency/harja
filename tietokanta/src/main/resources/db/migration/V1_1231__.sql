-- Lisätään kulu_kohdistus tauluun tieto, jos Muu Tehtävä on käytössä, joka on dummytehtävä, jota ei voi valita kulu_kohdistukselle oikeasti.
ALTER TABLE kulu_kohdistus
    ADD COLUMN IF NOT EXISTS "muu-tehtava-kaytossa" BOOLEAN DEFAULT FALSE;
