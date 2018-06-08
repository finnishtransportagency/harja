ALTER TABLE tielupa
  DROP COLUMN "ely-nimi",
  ADD COLUMN alueurakka TEXT;



CREATE OR REPLACE FUNCTION aseta_tieluvalle_urakka(tielupa_id INTEGER)
  RETURNS VOID AS
$$
DECLARE
  sijainti_          TR_OSOITE_LAAJENNETTU;
  sijainnit_         TR_OSOITE_LAAJENNETTU [];
  geometriat_        GEOMETRY [];
  tieluvan_geometria GEOMETRY;
  alueurakka_        TEXT;
  alueurakkanro_     TEXT;
  urakka_id_         INTEGER;
BEGIN
  SELECT INTO sijainnit_ sijainnit, alueurakka_ alueurakka
  FROM tielupa
  WHERE id = tielupa_id;

  SELECT INTO alueurakka_ alueurakka
  FROM tielupa
  WHERE id = tielupa_id;

  RAISE NOTICE 'Sijainnit %', sijainnit_;
  RAISE NOTICE 'Alueurakka %', alueurakka_;

  FOREACH sijainti_ IN ARRAY sijainnit_
  LOOP
    geometriat_ := array_append(geometriat_, sijainti_.geometria);
  END LOOP;

  tieluvan_geometria := st_union(geometriat_);

  SELECT INTO alueurakkanro_ alueurakkanro
  FROM alueurakka
  WHERE st_contains(alue, tieluvan_geometria);

  RAISE NOTICE 'Alueurakkanumero A %', alueurakkanro_;

  -- Jos alueurakkanumeroa ei voida selvittää geometrian avulla, käytetään tieluvan kirjauksessa annettua alueurakan nimeä
  IF (alueurakkanro_ IS NULL)
  THEN
    SELECT INTO alueurakkanro_ alueurakkanro
    FROM alueurakka WHERE nimi = 'Hyvinkää';
  END IF;

  RAISE NOTICE 'Alueurakkanumero B %', alueurakkanro_;

  SELECT INTO urakka_id_ id
  FROM urakka
  WHERE urakkanro = alueurakkanro_
  ORDER BY loppupvm DESC
  LIMIT 1;

  RAISE NOTICE 'URAKKA %', urakka_id_;


  UPDATE tielupa
  SET urakka = urakka_id_
  WHERE id = tielupa_id;

  RETURN;
END;
$$
LANGUAGE plpgsql;
