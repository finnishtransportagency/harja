-- Lisää pisteytys-offset sarake
ALTER TABLE lupaus_kustannusennuste_kuukausi_pisteet 
ADD COLUMN pisteytys_hoitovuosi_offset INTEGER DEFAULT 0 NOT NULL;

COMMENT ON COLUMN lupaus_kustannusennuste_kuukausi_pisteet.pisteytys_hoitovuosi_offset 
IS 'Offset hoitovuoteen jolla kirjaus pisteytettään. 
    0 = kirjauksen oma hoitovuosi (normaali)
    1 = seuraava hoitovuosi (esim. HK1 elokuu pisteytettään HK2:lle)
    -1 = edellinen hoitovuosi (jos joskus tarpeen)';

-- Päivitä elokuun rivit käyttämään offsettia 1
UPDATE lupaus_kustannusennuste_kuukausi_pisteet
SET pisteytys_hoitovuosi_offset = 1
WHERE kuukausi = 8;