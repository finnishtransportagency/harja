-- Tiemerkinnän muut kustannukset 
ALTER TYPE yllapito_muu_toteuma_tyyppi ADD VALUE 'lisatyo';
ALTER TYPE yllapito_muu_toteuma_tyyppi ADD VALUE 'muutostyo';
ALTER TYPE yllapito_muu_toteuma_tyyppi ADD VALUE 'sopimusalueen-muutos';

-- Tiemerkinnän tilat 
CREATE TYPE tiemerkinnan_tila_enum AS ENUM ('valmis', 'ei-tehda', 'kasittelematta', 'ei-tiemerkintaa');
ALTER TABLE paikkauskohde ADD COLUMN "tiemerkinnan-tila" tiemerkinnan_tila_enum DEFAULT 'ei-tiemerkintaa';


-- Aseta defaultit olemassa oleville kohteille
UPDATE paikkauskohde
SET "tiemerkinnan-tila" = CASE
	-- Tiemerkintää ei voida tehdä -> ei tiemerkintää 
    WHEN "paikkauskohteen-tila" IN ('ehdotettu', 'tilattu', 'tarkistettu', 'hylatty') THEN 'ei-tiemerkintaa'
    -- Valmis, tiemerkintää ei tuhoutunut -> ei tiemerkintää 
    WHEN "paikkauskohteen-tila" = 'valmis' 
    AND ("tiemerkintaa-tuhoutunut?" IS NULL
    	OR "tiemerkintaa-tuhoutunut?" IS FALSE) THEN 'ei-tiemerkintaa'
    -- Kohde valmistunut yli 3kk, voidaan asettaa tiemerkintä tehdyksi 
    WHEN "paikkauskohteen-tila" = 'valmis' 
    AND "tiemerkintaa-tuhoutunut?" IS TRUE 
    AND "valmistumispvm" <= current_date - interval '3 month' THEN 'valmis'
    -- Kohde vasta valmistunut (alle 3kk sitten), aseta tiemerkintä käsittelemättä tilaan  
    WHEN "paikkauskohteen-tila" = 'valmis' 
    AND "tiemerkintaa-tuhoutunut?" IS TRUE  
    AND "valmistumispvm" >= current_date - interval '3 month' THEN 'kasittelematta'
    ELSE "tiemerkinnan-tila"
END;
