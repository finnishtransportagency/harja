-- Tiemerkintäurakan yks. hint. työt -näkymän muutokset

ALTER TABLE yllapitokohde_tiemerkinta RENAME TO tiemerkinnan_toteuma;

ALTER TABLE tiemerkinnan_toteuma
  ADD COLUMN kohde_nimi VARCHAR(512),
  ADD COLUMN tr_numero INTEGER,
  ADD COLUMN yllapitoluokka INTEGER,
  ADD COLUMN pituus INTEGER,
  ADD CONSTRAINT pituus_ei_neg CHECK (pituus >= 0)