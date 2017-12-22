-- Vesiväylien urakka-alueen muodostamiseen liittyvät muutokset
ALTER TABLE reimari_turvalaiteryhma
  ADD COLUMN urakka_alue GEOMETRY;

-- Funktio ja triggeri vesiväyläurakan urakka-alueen muodostamiseen turvalaiteryhmään
-- kuuluvien turvalaitteiden sijaintipisteiden perusteella

CREATE OR REPLACE FUNCTION muodosta_vesivaylaurakan_geometria()
  RETURNS TRIGGER AS $$
BEGIN
  IF NEW.turvalaitteet IS NOT NULL
  THEN
    NEW.urakka_alue := (SELECT ST_ConvexHull(ST_UNION(geometria))
                        FROM vatu_turvalaite
                        WHERE turvalaitenro = ANY ((NEW.turvalaitteet) :: INTEGER []));
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_muodosta_vesivaylaurakan_geometria
BEFORE INSERT OR UPDATE
  ON reimari_turvalaiteryhma
FOR EACH ROW
EXECUTE PROCEDURE muodosta_vesivaylaurakan_geometria();

