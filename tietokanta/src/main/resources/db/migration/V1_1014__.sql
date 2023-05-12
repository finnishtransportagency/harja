ALTER TYPE lahde ADD VALUE 'sql-tulkki';

COMMENT ON TYPE lahde IS E'harja-ls-mobiili: Laadunseurannan mobiilityokalu
harja-ui: Harjan käyttöliittymä
harja-api: Ulkoisten järjestelmien käyttämät rajapinnat
sql-tulkki: SQL-tulkilla käsin lisätty arvo, esim. poikkeuksellinen korjaus toteumiin.';
