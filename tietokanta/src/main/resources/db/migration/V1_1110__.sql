--  Nopeutetaan tyokonehavainto-taulua indeksoimalla tehtavat ja lahetysaika -kentät
CREATE INDEX idx_tyokonehavainto_tehtavat ON tyokonehavainto USING GIN (tehtavat);
CREATE INDEX idx_tyokonehavainto_lahetysaika ON tyokonehavainto (lahetysaika);
