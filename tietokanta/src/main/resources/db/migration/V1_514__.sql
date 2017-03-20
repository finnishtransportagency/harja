-- Refactoroi ylläpitokohteen aikataulutiedot omaan tauluun
CREATE TABLE yllapitokohteen_aikataulu (
  id SERIAL PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde(id) UNIQUE NOT NULL, -- Kohteella voi olla vain yksi aikataulu
  kohde_alku DATE,
  paallystys_alku TIMESTAMP,
  paallystys_loppu TIMESTAMP,
  valmis_tiemerkintaan TIMESTAMP,
  tiemerkinta_takaraja DATE,
  tiemerkinta_alku DATE,
  tiemerkinta_loppu DATE,
  kohde_valmis DATE,
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id)
);

-- Ylläpitokohteen aikataululle validointisääntöjä
ALTER TABLE yllapitokohteen_aikataulu ADD CONSTRAINT paallystys_loppu_validi CHECK (paallystys_loppu IS NULL OR paallystys_alku IS NOT NULL);
ALTER TABLE yllapitokohteen_aikataulu ADD CONSTRAINT valmis_tiemerkintaan_validi CHECK (valmis_tiemerkintaan IS NULL OR paallystys_loppu IS NOT NULL);
ALTER TABLE yllapitokohteen_aikataulu ADD CONSTRAINT tiemerkinta_valmis_validi CHECK (tiemerkinta_loppu IS NULL OR tiemerkinta_alku IS NOT NULL);

-- Migratoi olemassa oleva data uuteen tauluun
CREATE OR REPLACE FUNCTION migratoi_yllapitokohteiden_aikataulu() RETURNS VOID AS
$BODY$
DECLARE
  rivi RECORD;
BEGIN
  FOR rivi IN SELECT * FROM yllapitokohde
  LOOP
    INSERT INTO yllapitokohteen_aikataulu (yllapitokohde,
                                           aikataulu_kohde_alku,
                                           paallystys_alku,
                                           paallystys_loppu,
                                           valmis_tiemerkintaan,
                                           tiemerkinta_takaraja,
                                           tiemerkinta_alku,
                                           tiemerkinta_loppu,
                                           kohde_valmis,
                                           muokattu,
                                           muokkaaja)
    VALUES (rivi.id,
            rivi.aikataulu_kohde_alku,
            rivi.aikataulu_paallystys_alku,
            rivi.aikataulu_paallystys_loppu,
            rivi.valmis_tiemerkintaan,
            rivi.aikataulu_tiemerkinta_takaraja,
            rivi.aikataulu_tiemerkinta_alku,
            rivi.aikataulu_tiemerkinta_loppu,
            rivi.aikataulu_kohde_valmis,
            rivi.aikataulu_muokattu,
            rivi.aikataulu_muokkaaja);
  END LOOP;
  RETURN;
END
$BODY$
LANGUAGE 'plpgsql';

SELECT * FROM migratoi_yllapitokohteiden_aikataulu();
DROP FUNCTION migratoi_yllapitokohteiden_aikataulu(); -- Ei tarvi tehdä kuin kerran

-- Poista ylläpitokohteelta aikataulutiedot
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_paallystys_alku;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_paallystys_loppu;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_tiemerkinta_alku;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_tiemerkinta_loppu;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_kohde_valmis;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_muokattu;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_muokkaaja;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_tiemerkinta_takaraja;
ALTER TABLE yllapitokohde DROP COLUMN aikataulu_kohde_alku;
ALTER TABLE yllapitokohde DROP COLUMN valmis_tiemerkintaan;
