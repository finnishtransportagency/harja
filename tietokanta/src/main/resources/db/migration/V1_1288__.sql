---- Muuta lupauksessa elokuun määräpäivä oikeaksi
update lupaus_kustannusennuste_kuukausi_pisteet set paiva = 31, kuvaus = 'Elokuu 31.päivä (2025 urakat)' where "urakan-alkuvuosi" = 2025 and kuukausi = 8;
update lupaus_kustannusennuste_kuukausi_pisteet set paiva = 31, kuvaus = 'Elokuu 31.päivä (2026 urakat)' where "urakan-alkuvuosi" = 2026 and kuukausi = 8;

---- Poista vahingossa luotu laatupoikkeama ja siihen liittyvä sanktio
DO $$
    DECLARE
lp_id INTEGER;
BEGIN
SELECT id INTO lp_id FROM laatupoikkeama WHERE urakka = 455 and kohde = 'jepjep' and luotu = '2026-07-03 12:52:40.891682' LIMIT 1;

IF lp_id IS NOT NULL THEN
UPDATE sanktio
SET poistettu = TRUE,
    muokattu  = NOW(),
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE laatupoikkeama = lp_id;

UPDATE laatupoikkeama
SET muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu  = CURRENT_TIMESTAMP,
    poistettu = TRUE
WHERE id = lp_id;
END IF;
END $$;
