-- Päivitetään virheellisesti kyselyksi merkitty lupaustyyppi
UPDATE lupaus
SET lupaustyyppi = 'monivalinta'
WHERE kuvaus = 'Kohdistetun seurannan parantaminen';
