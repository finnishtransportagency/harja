-- Vesiväylien turvalaitehaku ei ole ollut käytössä. Poistetaan integraatio kokonaan.
DELETE FROM integraatio WHERE jarjestelma = 'ptj' AND nimi = 'turvalaitteet-haku'
