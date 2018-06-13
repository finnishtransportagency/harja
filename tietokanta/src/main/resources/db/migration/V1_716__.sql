CREATE OR REPLACE FUNCTION aseta_tieluvalle_urakka(tielupa_id INTEGER)
  RETURNS VOID AS
$$
DECLARE
  sijainti_          TR_OSOITE_LAAJENNETTU;
  sijainnit_         TR_OSOITE_LAAJENNETTU [];
  geometriat_        GEOMETRY [];
  tieluvan_geometria GEOMETRY;
  alueurakkanro_     TEXT;
  urakka_id_         INTEGER;
BEGIN
  SELECT INTO sijainnit_ sijainnit
  FROM tielupa
  WHERE id = tielupa_id;

  FOREACH sijainti_ IN ARRAY sijainnit_
  LOOP
    geometriat_ := array_append(geometriat_, sijainti_.geometria);
  END LOOP;

  tieluvan_geometria := st_union(geometriat_);

  SELECT INTO alueurakkanro_ alueurakkanro
  FROM alueurakka
  WHERE st_contains(alue, tieluvan_geometria);

  SELECT INTO urakka_id_ id
  FROM urakka
  WHERE urakkanro = alueurakkanro_
  ORDER BY loppupvm DESC
  LIMIT 1;

  UPDATE tielupa
  SET urakka = urakka_id_
  WHERE id = tielupa_id;

  RETURN;
END;
$$
LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION aseta_tieluvalle_urakka(tielupa_id INTEGER)
  RETURNS VOID AS
$$
DECLARE
  sijainti_          TR_OSOITE_LAAJENNETTU;
  sijainnit_         TR_OSOITE_LAAJENNETTU [];
  geometriat_        GEOMETRY [];
  tieluvan_geometria GEOMETRY;
  alueurakkanro_     TEXT;
  alueurakkanimi_    TEXT;
  urakka_id_         INTEGER;
BEGIN
  SELECT INTO sijainnit_ sijainnit
  FROM tielupa
  WHERE id = tielupa_id;

  RAISE NOTICE 'Sijainnit %', sijainnit_;

  IF (sijainnit_ IS NOT NULL AND sijainnit_[1].tie IS NOT NULL)
  THEN

    FOREACH sijainti_ IN ARRAY sijainnit_
    LOOP
      geometriat_ := array_append(geometriat_, sijainti_.geometria);
    END LOOP;

    tieluvan_geometria := ST_Multi(st_union(geometriat_));

    SELECT INTO alueurakkanro_ alueurakkanro
    FROM alueurakka
    WHERE st_contains(alue, tieluvan_geometria);

  END IF;

  RAISE NOTICE 'Alueurakkanumero 1 %', alueurakkanro_;

  IF (alueurakkanro_ IS NULL)
  THEN

    SELECT INTO alueurakkanimi_ "urakan-nimi"
    FROM tielupa
    WHERE id = tielupa_id;

    SELECT INTO alueurakkanro_ alueurakkanro
    FROM alueurakka
    WHERE nimi = alueurakkanimi_;

  END IF;

  RAISE NOTICE 'Alueurakkanimi %', alueurakkanimi_;
  RAISE NOTICE 'Alueurakkanumero 2 %', alueurakkanro_;


  SELECT INTO urakka_id_ id
  FROM urakka
  WHERE urakkanro = alueurakkanro_
  ORDER BY loppupvm DESC
  LIMIT 1;

  UPDATE tielupa
  SET urakka = urakka_id_
  WHERE id = tielupa_id;

  RETURN;
END;
$$
LANGUAGE plpgsql;
