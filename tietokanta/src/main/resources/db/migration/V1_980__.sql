ALTER TABLE yllapitokohteen_kustannukset ADD COLUMN maaramuutokset NUMERIC;
COMMENT ON COLUMN yllapitokohteen_kustannukset.maaramuutokset IS E'1.1.2023 eteenpäin ylläpitokohteen määrämuutokset kirjataan yhteissummana, eikä enää eriteltynä YLLAPITOKOHTEEN_MAARAMUUTOS-tauluun kuten 2022 asti.';
