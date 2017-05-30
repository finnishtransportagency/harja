-- (SELECT id FROM kayttaja WHERE kayttajanimi = 'harja' OR kayttajanimi = 'tero')

CREATE OR REPLACE FUNCTION lisaa_oletushinnoittelut() RETURNS TRIGGER AS $$
BEGIN
  -- ,'vesivayla-ruoppaus', 'vesivayla-turvalaitteiden-korjaus', 'vesivayla-kanavien-hoito', 'vesivayla-kanavien-korjaus'
  IF NEW.tyyppi IN ('vesivayla-hoito') THEN
    IF NEW.luoja IS NOT NULL THEN
    INSERT INTO vv_hinnoittelu ("urakka-id",nimi, hintaryhma, luoja) VALUES
      (NEW.id, 'Muutos- ja lisätyöt', TRUE, NEW.luoja),
      (NEW.id, 'Erikseen tilatut työt', TRUE, NEW.luoja);
    ELSE
      INSERT INTO vv_hinnoittelu ("urakka-id",nimi, hintaryhma, luoja) VALUES
        (NEW.id, 'Muutos- ja lisätyöt', TRUE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'harja' OR kayttajanimi = 'tero')),
        (NEW.id, 'Erikseen tilatut työt', TRUE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'harja' OR kayttajanimi = 'tero'));
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_lisaa_oletushinnoittelut_vesivaylaurakalle
AFTER INSERT
  ON urakka
FOR EACH ROW
EXECUTE PROCEDURE lisaa_oletushinnoittelut();