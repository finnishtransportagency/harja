-- Siirretään :vesi tyyppiset urakat oikeisiin "elinvoimakeskuksiin", joita vesiväylät ja muut ei toki ole.
-- Mutta käyttöliittymän mukaan ne on.
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Kanavat ja avattavat sillat')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Kanavat ja avattavat sillat');

UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Sisävesiväylät')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Sisävesiväylät');

UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Meriväylät')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Meriväylät');
