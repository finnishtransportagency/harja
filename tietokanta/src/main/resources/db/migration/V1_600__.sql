CREATE INDEX integraatiotapahtuma_alkanut_idx ON integraatiotapahtuma (alkanut);
CREATE INDEX integraatioviesti_integraatiotapahtuma_idx ON integraatioviesti (integraatiotapahtuma);
CREATE EXTENSION pg_trgm;
CREATE INDEX index_integraatioviesti_on_sisalto_trigram ON integraatioviesti USING gin (sisalto gin_trgm_ops);
