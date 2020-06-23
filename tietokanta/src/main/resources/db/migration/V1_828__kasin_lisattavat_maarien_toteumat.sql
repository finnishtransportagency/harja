-- Vain osa toimenpidekoodeista on sellaisia tehtäviä, että niitä voidaan lisätä
-- käyttöliittymän kautta käsin suunnitelluille tehtäville.
-- Määritellään kaikki toimenpidekoodit aluksi käsin lisättävien joukosta.
-- Ja koska ympäristöt ovat erilaisia (tehtävien nimet ja id;t vaihtelevat ympäristön mukaan)
-- , niin etukäteen ei voida määritellä, että mitkä laitetana käsin lisättävien listalle ja mitä ei.
-- Niinpä ne on tehtävä jokaiseen ympäristöön erikseen. Siihen on oma käyttöliittymä hallintapuolella.
ALTER TABLE toimenpidekoodi
    ADD kasin_lisattava_maara BOOLEAN DEFAULT FALSE;