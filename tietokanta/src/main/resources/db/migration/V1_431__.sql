ALTER TABLE tarkastus ADD COLUMN nayta_urakoitsijalle boolean DEFAULT FALSE NOT NULL;

UPDATE tarkastus
SET nayta_urakoitsijalle = true
WHERE id IN
      (
        SELECT t.id
        FROM tarkastus t
        JOIN kayttaja k ON t.luoja = k.id
        JOIN organisaatio o ON k.organisaatio = o.id
        WHERE o.tyyppi = 'urakoitsija'::organisaatiotyyppi
      );

