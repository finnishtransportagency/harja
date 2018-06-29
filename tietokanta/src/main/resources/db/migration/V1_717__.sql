

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

  SELECT INTO alueurakkanimi_ "urakan-nimi"
  FROM tielupa
  WHERE id = tielupa_id;

  SELECT INTO sijainnit_ sijainnit
  FROM tielupa
  WHERE id = tielupa_id;

  IF (sijainnit_ IS NOT NULL AND sijainnit_ [1].tie IS NOT NULL)
  THEN

    FOREACH sijainti_ IN ARRAY sijainnit_
    LOOP
      IF (sijainti_.geometria IS NOT NULL) THEN
        geometriat_ := array_append(geometriat_, sijainti_.geometria);
      END IF;
    END LOOP;

    tieluvan_geometria := st_union(geometriat_);

    -- Jos geometria on piste, ei tehdä collection extract -käsittelyä. CE tehdään koska osa geometrioista on GEOMETRYCOLLECTIONeita.
    IF (GeometryType(tieluvan_geometria) NOT IN ('POINT', 'MULTIPOINT'))
    THEN
      tieluvan_geometria := st_collectionextract(tieluvan_geometria, 2);
    END IF;

    SELECT INTO alueurakkanro_ alueurakkanro
    FROM alueurakka
    WHERE st_intersects(alue, tieluvan_geometria) LIMIT 1; -- Jos tielupa osuu usealle alueelle, valitaan vain yksi alueurakka. Tässä on jatkokehityksen paikka.

  END IF;

  IF (alueurakkanro_ IS NULL)
  THEN

    SELECT INTO alueurakkanro_ alueurakkanro
    FROM alueurakka
    WHERE nimi = alueurakkanimi_;

  END IF;

  SELECT INTO urakka_id_ id
  FROM urakka
  WHERE urakkanro = alueurakkanro_
  ORDER BY loppupvm DESC
  LIMIT 1;

  -- Jos urakka id ei löytynyt geometrian perusteella, kokeile vielä nimeä.
  IF (alueurakkanro_ IS NULL AND urakka_id_ IS NULL)
  THEN

    SELECT INTO alueurakkanro_ alueurakkanro
    FROM alueurakka
    WHERE nimi = alueurakkanimi_;

    SELECT INTO urakka_id_ id
    FROM urakka
    WHERE urakkanro = alueurakkanro_
    ORDER BY loppupvm DESC
    LIMIT 1;

  END IF;

  IF (urakka_id_ IS NULL)
  THEN
    RAISE NOTICE 'Tieluvan urakan päättely epäonnistui. Tielupa: %. Luvan urakka: %. Alueurakkanumero: %.', tielupa_id, alueurakkanimi_, alueurakkanro_;
  ELSE
    UPDATE tielupa
    SET urakka = urakka_id_
    WHERE id = tielupa_id;
  END IF;

  RETURN;
END;
$$
LANGUAGE plpgsql;
