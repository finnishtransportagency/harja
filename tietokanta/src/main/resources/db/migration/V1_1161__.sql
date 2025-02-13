DROP FUNCTION IF EXISTS pistevalin_suolausalueet(piste1 POINT, piste2 POINT, urakka_id_ INTEGER); -- Siivotaan vanhentunut versio funktiosta pois. Uusi versio R__Suolapisteet-migraatiossa.

-- Pohjavesitietoja ei käytetä enää
ALTER TYPE suolausalueen_osuus
DROP ATTRIBUTE pohjavesialue_tunnus;
