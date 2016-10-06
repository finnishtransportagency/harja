-- Luo TR osoite usealle pisteelle aggregate
CREATE AGGREGATE sama_tr_pisteille ( geometry ) (
  sfunc = array_append,
  finalfunc = yrita_sama_tierekisteriosoite_pisteille,
  stype = GEOMETRY[]
);
