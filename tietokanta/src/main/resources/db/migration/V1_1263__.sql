-- Tallennetaan laskutusrajan alkuperäinen arvo ennen muutostöiden korotuksia.
-- Käytetään laskutusrajan automaattisessa tarkistuksessa hoitovuoden aikana.
ALTER TABLE urakka_tavoite ADD COLUMN IF NOT EXISTS laskutusraja_alkuperainen NUMERIC;
