-- Päivitä YHA kohdeosien geometriat ajoratatiedolla


-- Tierekisteriosoitteelle viiva, ajoradan mukaan (tämä myös R migraatiossa)
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva_ajr(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER, ajr_ INTEGER)
  RETURNS SETOF geometry
AS $$
DECLARE
  tmp_osa INTEGER;
  tmp_et INTEGER;
  g GEOMETRY;
BEGIN
 IF (ajr_ = 1 AND (aosa_ > losa_ OR (aosa_ = losa_ AND aet_ > let_)))
    OR
    (ajr_ = 2 AND (aosa_ < losa_ OR (aosa_ = losa_ AND aet_ < let_)))
  THEN
   -- Jos halutaan 1 ajoradan geometria, mutta osat ovat laskevassa järjestyksessä
   -- tai halutaan 2 ajoradan geometria, mutta osat ovat nousevassa järjestyksessä
   -- => vaihdetaan alku ja loppu
   tmp_osa := aosa_;
   tmp_et := aet_;
   aosa_ := losa_;
   aet_ := let_;
   losa_ := tmp_osa;
   let_ := tmp_et;
 END IF;
 FOR g IN SELECT tierekisteriosoitteelle_viiva(tie_, aosa_, aet_, losa_, let_)
 LOOP
   RETURN NEXT g;
 END LOOP;
END;
$$ LANGUAGE plpgsql;

UPDATE yllapitokohdeosa
   SET sijainti = (SELECT tierekisteriosoitteelle_viiva_ajr(
                          tr_numero, tr_alkuosa, tr_alkuetaisyys,
			  tr_loppuosa, tr_loppuetaisyys, tr_ajorata))
 WHERE yhaid IS NOT NULL;
