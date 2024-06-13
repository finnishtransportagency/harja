-- Indeksit ajetaan tuotantoon manuaalisesti niiden keston takia. Migraatio olemassa dokumentaatiota
-- ja muiden ympäristöjen yhtenäisenä pitämistä varten.
DROP INDEX CONCURRENTLY IF EXISTS index_integraatioviesti_on_sisalto_trigram;
-- Indeksöidään vain ensimmäiset 2000 merkkiä integraatioviesti-taulun sisällöstä
-- Tällä tavoitellaan integraatioviestien insertoinnin nopeutusta ja samalla indeksin koon pienentämistä.
CREATE INDEX CONCURRENTLY IF NOT EXISTS index_integraatioviesti_on_sisalto_substring_trigram ON integraatioviesti USING gin (substring(sisalto, 0, 2000) gin_trgm_ops);
