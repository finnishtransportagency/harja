-- Muutetaan konstrainttia siten, että se hyväksyy materiaalihinnat ilman yksikköä.
-- Tämän muutoksen voi oikeastaan perua, jos materiaalille liätään yksikkö

ALTER TABLE kan_hinta
DROP CONSTRAINT validi_hinta;

ALTER TABLE kan_hinta
ADD CONSTRAINT validi_hinta
CHECK ((((summa IS NOT NULL) OR
         ((maara IS NOT NULL) AND
          (yksikko IS NOT NULL) AND
          (yksikkohinta IS NOT NULL)) OR
         ((maara IS NOT NULL) AND
          (ryhma = 'materiaali') AND
          (yksikkohinta IS NOT NULL))) AND
        (((summa IS NOT NULL) AND
          (maara IS NULL)) OR
         ((maara IS NOT NULL) AND
          (summa IS NULL)))));

ALTER TABLE kan_hinta ADD COLUMN "materiaali-id" INTEGER REFERENCES vv_materiaali (id);
