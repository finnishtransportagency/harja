--
-- TIEDOSSA: Uudelleenimetään tiedosto ennen mergeä 
-- 

ALTER TYPE yllapito_muu_toteuma_tyyppi ADD VALUE 'lisatyo';
ALTER TYPE yllapito_muu_toteuma_tyyppi ADD VALUE 'muutostyo';
ALTER TYPE yllapito_muu_toteuma_tyyppi ADD VALUE 'sopimusalueen-muutos';


CREATE TYPE tiemerkinnan_tila_enum AS ENUM ('valmis', 'ei-tehda', 'kasittelematta', 'ei-tiemerkintaa');
ALTER TABLE paikkauskohde ADD COLUMN "tiemerkinnan-tila" tiemerkinnan_tila_enum DEFAULT 'ei-tiemerkintaa';
