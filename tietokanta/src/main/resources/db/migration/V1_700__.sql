-- Irrota ylläpitokohteen rahakentät omaan tauluun
CREATE TABLE yllapitokohteen_kustannukset (
  id                       SERIAL PRIMARY KEY,
  yllapitokohde            INTEGER REFERENCES yllapitokohde (id) UNIQUE NOT NULL, -- Kohteella voi olla vain yksi kustannusrivi
  sopimuksen_mukaiset_tyot NUMERIC,
  arvonvahennykset         NUMERIC,
  bitumi_indeksi           NUMERIC,
  kaasuindeksi             NUMERIC,
  toteutunut_hinta         NUMERIC, -- Koskee vain paikkauskohteita, joilla ei ole tarjoushintaa ja määrämuutoksia
  muokkaaja                INTEGER REFERENCES kayttaja (id),
  muokattu                 TIMESTAMP
);

-- Migratoi olemassa oleva data uuteen tauluun
CREATE OR REPLACE FUNCTION migratoi_yllapitokohteiden_kustannukset()
  RETURNS VOID AS
$BODY$
DECLARE
  rivi RECORD;
BEGIN
  FOR rivi IN SELECT * FROM yllapitokohde
  LOOP
    INSERT INTO yllapitokohteen_kustannukset (yllapitokohde,
                                              sopimuksen_mukaiset_tyot,
                                              arvonvahennykset,
                                              bitumi_indeksi,
                                              kaasuindeksi,
                                              toteutunut_hinta,
                                              muokattu)
    VALUES (rivi.id,
            rivi.sopimuksen_mukaiset_tyot,
            rivi.arvonvahennykset,
            rivi.bitumi_indeksi,
            rivi.kaasuindeksi,
            rivi.toteutunut_hinta,
            rivi.muokattu);
  END LOOP;
  RETURN;
END
$BODY$
LANGUAGE 'plpgsql';


SELECT * FROM migratoi_yllapitokohteiden_kustannukset();
DROP FUNCTION migratoi_yllapitokohteiden_kustannukset(); -- Ei tarvi tehdä kuin kerran

-- Poista ylläpitokohteelta kustannustiedot
ALTER TABLE yllapitokohde DROP COLUMN sopimuksen_mukaiset_tyot;
ALTER TABLE yllapitokohde DROP COLUMN arvonvahennykset;
ALTER TABLE yllapitokohde DROP COLUMN bitumi_indeksi;
ALTER TABLE yllapitokohde DROP COLUMN kaasuindeksi;
ALTER TABLE yllapitokohde DROP COLUMN toteutunut_hinta;