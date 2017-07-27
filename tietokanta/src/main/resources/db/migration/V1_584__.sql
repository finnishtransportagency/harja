-- Luo näkymä, jossa reimarin väylän nimi

-- Koska Reimarin antama väyläid on tällä hetkellä ERI kuin inspire
-- palvelusta tuoduille väylille, joudutaan match tekemään nimellä.
-- Muodostetaan reimari toimenpiteen väylä id triggerillä.

CREATE INDEX vv_vayla_nimi_idx ON vv_vayla (nimi);

CREATE FUNCTION vv_aseta_toimenpiteen_vayla() RETURNS trigger AS $$
DECLARE
  v reimari_vayla;
  id_ INTEGER;
BEGIN
  v := NEW."reimari-vayla";
  SELECT INTO id_ id FROM vv_vayla WHERE nimi = v.nimi;
  NEW."vayla-id" := id_;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER tg_vv_aseta_toimenpiteen_vayla
BEFORE INSERT OR UPDATE ON reimari_toimenpide
FOR EACH ROW
EXECUTE PROCEDURE vv_aseta_toimenpiteen_vayla();


UPDATE reimari_toimenpide
   SET "vayla-id" = (SELECT id FROM vv_vayla v WHERE v.nimi = ("reimari-vayla").nimi LIMIT 1);
