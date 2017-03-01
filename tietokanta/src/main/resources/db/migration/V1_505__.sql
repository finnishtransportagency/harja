-- Muuta POT-lomakkeen teknistä osaa kuvaava asiatarkastus kuvaamaan sitä, onko omake ollut
-- tarkastuksessa hyväksyttävä

-- Aiemmin tähän merkittiin vain onko tekninen/taloudellinen osa tarkastettu (rasti ruutuun)
-- Kuitenkaan checkbox ei ilmaissut sitä, onko sisältö ollut tarkastuksessa ok ja pvm-kenttä
-- ilmaisi jo saman asian, että tarkastus on tehty.

ALTER TABLE paallystysilmoitus RENAME COLUMN asiatarkastus_tekninen_osa TO asiatarkastus_hyvaksytty;