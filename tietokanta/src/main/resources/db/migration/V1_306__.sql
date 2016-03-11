<<<<<<< HEAD
-- kuvaus: Vaihda yks. hint. työt päivittäin -rapsan namespace
UPDATE raportti
SET koodi = '#''harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain/suorita'
WHERE kuvaus = 'Yksikköhintaiset työt päivittäin';
=======
-- Indeksoi toteuman ulkoinen_id ja luoja
-- Tämä huomattavasti nopeuttaa ulkoisen id:n olemassaolon tarkistusta
CREATE INDEX toteuma_ulkoinen_id_luoja ON toteuma (ulkoinen_id, luoja);
>>>>>>> develop
