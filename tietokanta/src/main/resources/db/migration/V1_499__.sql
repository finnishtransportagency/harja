-- Tiemerkintäurakan yks. hint. työt -näkymän muutokset

-- yllapitokohde_tiemerkinta taulua käytettiin liittämään ylläpitokohteeseen tiemerkintään liittyvät
-- asiat. Käytännössä taulua käytettiin ja käytetään jatkossakin pelkästään yks. hint. työt -näkymän
-- toteumien tallentamiseen, joten nimetään taulu uudelleen.
ALTER TABLE yllapitokohde_tiemerkinta RENAME TO tiemerkinnan_yksikkohintainen_toteuma;

ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma
  ADD COLUMN selite VARCHAR(512),
  ADD COLUMN tr_numero INTEGER,
  ADD COLUMN yllapitoluokka INTEGER,
  ADD COLUMN pituus INTEGER,
  ADD CONSTRAINT pituus_ei_neg CHECK (pituus >= 0);

ALTER TYPE yllapitokohde_tiemerkinta_hintatyyppi RENAME TO tiemerkinta_toteuma_hintatyyppi;