ALTER TABLE tielupa DROP CONSTRAINT tielupa_urakka_fkey;
ALTER TABLE tielupa ALTER COLUMN "urakan-nimi" TYPE TEXT [] USING ARRAY["urakan-nimi"];
ALTER TABLE tielupa ALTER COLUMN urakka TYPE INTEGER [] USING ARRAY[urakka];
ALTER TABLE tielupa RENAME COLUMN "urakan-nimi" TO "urakoiden-nimet";
ALTER TABLE tielupa RENAME COLUMN urakka TO urakat;

CREATE OR REPLACE FUNCTION aseta_tieluvalle_urakka(tielupa_id INTEGER)
  RETURNS VOID AS
$$
DECLARE
  sijainti_          TR_OSOITE_LAAJENNETTU;
  sijainnit_         TR_OSOITE_LAAJENNETTU [];
  geometriat_        GEOMETRY [];
  tieluvan_geometria GEOMETRY;
  alueurakkanrot_     TEXT [];
  alueurakkanimet_    TEXT [];
  urakka_idt_         INTEGER [];

BEGIN

  SELECT INTO alueurakkanimet_ "urakoiden-nimet"
  FROM tielupa
  WHERE id = tielupa_id;

  SELECT INTO sijainnit_ sijainnit
  FROM tielupa
  WHERE id = tielupa_id;

  IF (alueurakkanimet_ IS NOT NULL)
  THEN
    SELECT INTO alueurakkanrot_ array_agg(alueurakkanro::TEXT)
    FROM alueurakka
    WHERE nimi = ANY (alueurakkanimet_ ::TEXT[]);
  ELSIF (sijainnit_ IS NOT NULL AND sijainnit_ [1].tie IS NOT NULL)
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

    SELECT INTO alueurakkanrot_ array_agg(alueurakkanro::TEXT)
    FROM alueurakka
    WHERE st_intersects(alue, tieluvan_geometria);
  END IF;

  SELECT INTO urakka_idt_ array_agg(id::INTEGER)
  FROM urakka
  WHERE (urakkanro = ANY(alueurakkanrot_::TEXT[]) AND
         tyyppi='hoito'::urakkatyyppi);

  IF (urakka_idt_ IS NULL)
  THEN
    RAISE NOTICE 'Tieluvan urakan päättely epäonnistui. Tielupa: %. Luvan urakka: %. Alueurakkanumero: %.', tielupa_id, alueurakkanimet_, alueurakkanrot_;
  ELSE
    UPDATE tielupa
    SET urakat = urakka_idt_
    WHERE id = tielupa_id;
  END IF;

  RETURN;
END;
$$
LANGUAGE plpgsql;