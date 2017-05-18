CREATE TYPE toimenpidehaun_komponentti AS (id INTEGER, nimi TEXT, tila TEXT);
CREATE OR REPLACE FUNCTION toimenpidehaun_komponentit_ok(komponentit toimenpidehaun_komponentti[])
RETURNS BOOLEAN AS $$
  DECLARE komponentti toimenpidehaun_komponentti;
BEGIN
  FOREACH komponentti IN ARRAY komponentit
  LOOP
    IF komponentti.id IS NULL THEN
      RETURN FALSE;
    END IF;
    IF komponentti.tila IS NULL THEN
      RETURN FALSE;
    END IF;
  END LOOP;
  RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE reimari_toimenpide
ADD COLUMN "reimari-komponentit" toimenpidehaun_komponentti[]
      NOT NULL
      CHECK (toimenpidehaun_komponentit_ok("reimari-komponentit"))
      DEFAULT '{}';
