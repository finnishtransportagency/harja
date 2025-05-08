-- Päivittää "tiemerkinnan-tila", kun "paikkauskohteen-tila" muuttuu
-- Jos tila on ehdotettu/tilattu/tarkistettu/hylätty -> 'ei-tiemerkintaa'
-- Jos tila on valmis ja merkintä tuhoutunut -> 'kasittelematta'
-- Jos tila on valmis eikä merkintä tuhoutunut -> 'ei-tiemerkintaa' 
-- 
-- Tilaa voi vaihtaa tiemerkintä urakka, paikkaus/päällystysurakka näkee tilan "paikkaukset" välilehdellä
-- 
DROP TRIGGER IF EXISTS paikkauskohde_tila_trigger ON paikkauskohde;


CREATE OR REPLACE FUNCTION paivita_tiemerkinnan_tila()
RETURNS trigger AS $$
BEGIN
    -- Jos kohteen tila vaihtuu johonkin näistä, voidaan asettaa tilaksi ei tiemerkintää
    IF NEW."paikkauskohteen-tila" IN ('ehdotettu', 'tilattu', 'tarkistettu', 'hylatty') THEN
        NEW."tiemerkinnan-tila" := 'ei-tiemerkintaa';
    -- Kohde valmistui, ja tiemerkintää tuhoutui, aseta tila -> käsittelemättä 
    ELSIF NEW."paikkauskohteen-tila" = 'valmis' AND NEW."tiemerkintaa-tuhoutunut?" = true THEN
        NEW."tiemerkinnan-tila" := 'kasittelematta';
    -- Tiemerkintää ei ole tuhoutunut -> ei tiemerkintää
    ELSIF NEW."paikkauskohteen-tila" = 'valmis' AND (NEW."tiemerkintaa-tuhoutunut?" = false OR NEW."tiemerkintaa-tuhoutunut?" IS NULL) THEN
        NEW."tiemerkinnan-tila" := 'ei-tiemerkintaa';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER paikkauskohde_tila_trigger
BEFORE UPDATE ON paikkauskohde
FOR EACH ROW
WHEN (OLD."paikkauskohteen-tila" IS DISTINCT FROM NEW."paikkauskohteen-tila")
EXECUTE FUNCTION paivita_tiemerkinnan_tila();
