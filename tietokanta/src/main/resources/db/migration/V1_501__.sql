-- Tiemerkintäurakan yks. hint. työt -näkymän muutokset

-- yllapitokohde_tiemerkinta taulua käytettiin liittämään ylläpitokohteeseen tiemerkintään liittyvät
-- asiat. Käytännössä taulua käytettiin ja käytetään jatkossakin pelkästään yks. hint. työt -näkymän
-- toteumien tallentamiseen, joten nimetään taulu uudelleen.
ALTER TABLE yllapitokohde_tiemerkinta RENAME TO tiemerkinnan_yksikkohintainen_toteuma;

ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma
  ADD COLUMN urakka integer REFERENCES urakka (id) NOT NULL,
  ADD COLUMN selite VARCHAR(512) NOT NULL,
  ADD COLUMN tr_numero INTEGER, -- NOT NULL vain jos ylläpitokohdetta ei ole annettu, ks. CONSTRAINT
  ADD COLUMN pituus INTEGER, -- NOT NULL vain jos ylläpitokohdetta ei ole annettu, ks. CONSTRAINT
  ADD COLUMN yllapitoluokka INTEGER,
  ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT pituus_ei_neg CHECK (pituus >= 0),
    -- Jos linkattu ylläpitokohteeseen, ei voi olla omia kohdetietoja, mutta hinta kohteelle -tieto on pakko olla
    -- Jos ei ole linkattu ylläpitokohteeseen, on pakko olla tienumero ja pituus
    ADD CONSTRAINT linkattu_tai_omat_tiedot_tarkistus CHECK
      ((yllapitokohde IS NULL AND tr_numero IS NOT NULL AND pituus IS NOT NULL)
        OR (yllapitokohde IS NOT NULL AND tr_numero IS NULL AND yllapitoluokka IS NULL AND pituus IS NULL AND hinta_kohteelle IS NOT NULL));
ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma ALTER COLUMN yllapitokohde DROP NOT NULL;
ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma DROP CONSTRAINT yllapitokohde_tiemerkinta_yllapitokohde_key;

-- Jatkossa tiemerkinnän yks. hint. toteumat sidotaan suoraan urakkaan, koska ylläpitokohde-linkitys
-- on vapaaehtoinen. Migratoidaan vanha data (tehdään urakka-linkki ylläpitokohteen kautta)
UPDATE tiemerkinnan_yksikkohintainen_toteuma tyt
SET urakka = (SELECT suorittava_tiemerkintaurakka FROM yllapitokohde WHERE id = tyt.yllapitokohde);

-- Hinta pitäisi olla numeric
ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma ALTER COLUMN hinta TYPE NUMERIC(10, 2) USING hinta::NUMERIC;
ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma ALTER COLUMN hinta SET NOT NULL;
ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma ALTER COLUMN hintatyyppi SET NOT NULL;

-- Päivitä hintatyyppi-type
ALTER TYPE yllapitokohde_tiemerkinta_hintatyyppi RENAME TO tiemerkinta_toteuma_hintatyyppi;