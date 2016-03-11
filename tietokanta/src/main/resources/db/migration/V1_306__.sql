-- kuvaus: Vaihda yks. hint. työt päivittäin -rapsan namespace
UPDATE raportti
SET koodi = '#''harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain/suorita'
WHERE kuvaus = 'Yksikköhintaiset työt päivittäin';