-- Sanktioraportti ylläpidolle
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
  'sanktioraportti-yllapito', 'Sakko- ja bonusraportti',
  ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
  ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri],
  '#''harja.palvelin.raportointi.raportit.sanktioraportti-yllapito/suorita',
  ARRAY['paallystys', 'paikkaus', 'tiemerkinta']::urakkatyyppi[]
);

ALTER TABLE yllapitokohde
  ADD CONSTRAINT yllapitoluokka_validi CHECK (yllapitoluokka BETWEEN 1 and 10);

ALTER TABLE sanktio
  ADD CONSTRAINT sakoille_maxmaara
CHECK (maara < 1000000000); -- yli 1000Me:n sakkoja tuskin tulee