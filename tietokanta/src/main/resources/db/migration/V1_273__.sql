-- Yksikköhintaisten töiden raporteille uudet kontekstit

UPDATE raportti SET konteksti = ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti]
WHERE nimi = 'yks-hint-tehtavien-summat';
UPDATE raportti SET konteksti = ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti]
WHERE nimi = 'yks-hint-kuukausiraportti';