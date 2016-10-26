-- Ylläpitokohteen tiemerkintätiedoille tieto siitä, mille osoitteelle hinta alkujaan annettiin
ALTER TABLE yllapitokohde_tiemerkinta ADD COLUMN hinta_osoitteelle VARCHAR(256);