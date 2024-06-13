-- Poistetaan turhaksi jääneitä kolumneita kulu ja kulu_kohdistus tauluista
ALTER TABLE kulu
    DROP COLUMN IF EXISTS tyyppi;
-- Tyypin voi poistaa, koska kaikki on tyyppiä 'laskutettava'

-- Poistetaan turhaksi jäänyt laskutustyyppi
DROP TYPE IF EXISTS laskutyyppi;

-- Kululla voi olla monta kohdistusta ja niiden tyyppi on helpointa hallita kohdistuksessa itsessään
CREATE TYPE kohdistustyyppi AS ENUM ('rahavaraus', 'hankintakulu','muukulu', 'lisatyo', 'paatos');

-- Asetetaan defaultiksi useimmin käytössäoleva hankintakulu.
-- Lopulliset tyypit tulee, kun rahavarausten korjaava systeemi ajetaan kantaan
ALTER TABLE harja.public.kulu_kohdistus
    ADD COLUMN IF NOT EXISTS tyyppi kohdistustyyppi DEFAULT 'hankintakulu' NOT NULL;

UPDATE kulu_kohdistus
   SET tyyppi = 'rahavaraus'
 WHERE rahavaraus_id IS NOT NULL;

ALTER TABLE kulu_kohdistus
    DROP COLUMN IF EXISTS suoritus_alku,
    DROP COLUMN IF EXISTS suoritus_loppu; -- Suoritusajat voi poistaa, koska ne ovat aina samat kuin kulu.erapaiva
