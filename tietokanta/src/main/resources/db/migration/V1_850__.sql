-- Kun käytettään taulun kaikissa POT versioissa, on kätevä että on versio kirjoittetu selkeästi
ALTER TABLE paallystysilmoitus ADD COLUMN versio INTEGER;
UPDATE paallystysilmoitus SET versio = 1;
ALTER TABLE paallystysilmoitus ALTER COLUMN versio SET NOT NULL;
