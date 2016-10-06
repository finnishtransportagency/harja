-- Luo TR osoite usealle pisteelle aggregate

-- Luodaan dummy deklaraation vuoksi, myöhemmin R__Tieverkko luo oikean
CREATE FUNCTION yrita_sama_tierekisteriosoite_pisteille (pisteet geometry[]) RETURNS tr_osoite
AS $$
BEGIN
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE AGGREGATE sama_tr_pisteille ( geometry ) (
  sfunc = array_append,
  finalfunc = yrita_sama_tierekisteriosoite_pisteille,
  stype = GEOMETRY[]
);
