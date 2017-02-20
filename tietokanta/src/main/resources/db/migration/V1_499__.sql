-- Sanktioraportti myös ylläpidolle

UPDATE raportti SET urakkatyyppi = ARRAY['hoito', 'paallystys', 'paikkaus', 'tiemerkinta']::urakkatyyppi[]
WHERE nimi = 'sanktioraportti';

ALTER TABLE yllapitokohde
  ADD CONSTRAINT yllapitoluokka_validi CHECK (yllapitoluokka BETWEEN 1 and 10);