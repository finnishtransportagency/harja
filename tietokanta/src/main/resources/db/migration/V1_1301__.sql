-- Datakorjauksia urakkaparametri procciin
-- Insert pathista puuttui muutosten hallinta, sekä laskutusraja pvm korjattiin
UPDATE urakka_parametrit
   SET muutosten_hallinta = TRUE
 WHERE urakkaid IN (
    SELECT id FROM urakka 
             WHERE alkupvm >= '2025-01-01'
               AND tyyppi IN ('teiden-hoito', 'hoito')
);

UPDATE urakka_parametrit
   SET laskutusraja_kaytossa = TRUE
 WHERE urakkaid IN (
    SELECT id FROM urakka
             WHERE alkupvm >= '2025-01-01'
               AND tyyppi IN ('teiden-hoito', 'hoito')
);
