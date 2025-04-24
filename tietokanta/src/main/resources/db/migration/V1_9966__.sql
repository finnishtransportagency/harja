--
-- TIEDOSSA: Uudelleenimetään tiedosto ennen mergeä 
-- 

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
    WHEN "paikkauskohteen-tila" IN ('ehdotettu', 'tilattu', 'tarkistettu', 'hylatty') THEN 'ei-tiemerkintaa'
    -- Paitsi ..
    -- Kohde valmistunut yli 45 päivää sitten, voidaan asettaa tiemerkintä tehdyksi 
    WHEN "paikkauskohteen-tila" = 'valmis' AND "valmistumispvm" <= current_date - interval '1 month 15 days' THEN 'valmis'
    -- Kohde vasta valmistunut, aseta tiemerkintä käsittelemättä tilaan  
    WHEN "paikkauskohteen-tila" = 'valmis' THEN 'kasittelematta'
    ELSE "tiemerkinnan-tila"
END;
