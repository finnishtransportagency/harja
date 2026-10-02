-- Uusi parametri katsomaan, onko uusi kustannussuunnitelma eli hoitovuoden alun tavoitehinta sivu käytössä
ALTER TABLE urakka_parametrit
    ADD COLUMN hoitovuoden_alun_tavoitehinta_kaytossa boolean DEFAULT false NOT NULL;

-- Oletuksena asetus on epätosi; otetaan se käyttöön vuonna 2025 ja myöhemmin alkaville urakoille.
UPDATE urakka_parametrit up
SET hoitovuoden_alun_tavoitehinta_kaytossa = true
FROM urakka u
WHERE u.id = up.urakkaid
  AND EXTRACT(YEAR FROM u.alkupvm) >= 2025
  AND u.tyyppi = 'teiden-hoito';
