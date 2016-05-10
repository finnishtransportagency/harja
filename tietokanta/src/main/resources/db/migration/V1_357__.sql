CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER)
  RETURNS SETOF geometry
AS $$
DECLARE
  ajoratavalinta INTEGER;
  aos INTEGER;
  los INTEGER;
  aet INTEGER;
  let INTEGER;
BEGIN
  IF aosa_ = losa_ THEN
    IF aet_ < let_ THEN
      ajoratavalinta := 1;
      aet := aet_;
      let := let_;
    ELSE
      ajoratavalinta := 2;
      aet := let_;
      let := aet_;
    END IF;
    IF ajoratavalinta=2 THEN
      RETURN QUERY SELECT ST_Reverse(ST_Line_Substring(geom, aet/tr_pituus::FLOAT, let/tr_pituus::FLOAT))
                   FROM tieverkko_paloina
                   WHERE tie=tie_
                         AND osa=aosa_
                         AND (ajorata=0 OR ajorata=ajoratavalinta);
    ELSE
      RETURN QUERY SELECT ST_Line_Substring(geom, aet/tr_pituus::FLOAT, let/tr_pituus::FLOAT)
                   FROM tieverkko_paloina
                   WHERE tie=tie_
                         AND osa=aosa_
                         AND (ajorata=0 OR ajorata=ajoratavalinta);
    END IF;
  ELSE
    IF aosa_ < losa_ THEN
      ajoratavalinta := 1;
      aos := aosa_;
      los := losa_;
      aet := aet_;
      let := let_;
    ELSE
      ajoratavalinta := 2;
      aos := losa_;
      los := aosa_;
      aet := let_;
      let := aet_;
    END IF;
    IF ajoratavalinta=2 THEN
      RETURN QUERY WITH q as (SELECT ST_LineMerge(ST_Union((CASE WHEN osa=aos THEN ST_Line_Substring(geom, LEAST(1, aet/ST_Length(geom)), 1)
                                                            WHEN osa=los THEN ST_Line_Substring(geom, 0, LEAST(1,let/ST_Length(geom)))
                                                            ELSE geom END) ORDER BY osa)) AS geom
                              FROM tieverkko_paloina
                              WHERE tie = tie_
                                    AND osa >= aos
                                    AND osa <= los
                                    AND (ajorata=0 OR ajorata=ajoratavalinta)
      )
      SELECT ST_Reverse(geom) FROM q;
    ELSE
      RETURN QUERY WITH q as (SELECT ST_LineMerge(ST_Union((CASE WHEN osa=aos THEN ST_Line_Substring(geom, LEAST(1, aet/ST_Length(geom)), 1)
                                                            WHEN osa=los THEN ST_Line_Substring(geom, 0, LEAST(1,let/ST_Length(geom)))
                                                            ELSE geom END) ORDER BY osa)) AS geom
                              FROM tieverkko_paloina
                              WHERE tie = tie_
                                    AND osa >= aos
                                    AND osa <= los
                                    AND (ajorata=0 OR ajorata=ajoratavalinta)
      )
      SELECT geom FROM q;
    END IF;
  END IF;
END;
$$ LANGUAGE plpgsql;
