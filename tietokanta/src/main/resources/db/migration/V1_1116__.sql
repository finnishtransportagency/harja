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
ALTER TABLE kulu_kohdistus
    ADD COLUMN IF NOT EXISTS tyyppi kohdistustyyppi DEFAULT 'hankintakulu' NOT NULL;

UPDATE kulu_kohdistus
   SET tyyppi = 'rahavaraus'
 WHERE rahavaraus_id IS NOT NULL;

ALTER TABLE kulu_kohdistus
    DROP COLUMN IF EXISTS suoritus_alku,
    DROP COLUMN IF EXISTS suoritus_loppu; -- Suoritusajat voi poistaa, koska ne ovat aina samat kuin kulu.erapaiva

-- Jotta tulevat rahavarausten automaattiset tausta-ajot korjaisivat kulu_kohdistus ja kustannusarvioitu_työ taulujen
-- rivit oikein. Meidän on lisättävä vielä yksi rahavaraus
INSERT INTO rahavaraus (nimi, luoja, luotu) VALUES ('Rahavararaus M - Muut rahavaraukset', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), now());

-- Lisätään muutama pakollinen tehtävä rahavarukselle
INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
SELECT rv.id,
       t.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
       now()
  FROM rahavaraus rv,
       tehtava t
 WHERE t.nimi IN ('Muut tavoitehintaan vaikuttavat rahavaraukset','Pohjavesisuojaukset')
   AND rv.nimi = 'Rahavararaus M - Muut rahavaraukset';

