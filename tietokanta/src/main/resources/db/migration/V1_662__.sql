-- Taulu Vatusta AVA:n kautta Harjaan tuoduille turvalaitteille. Korvaa myöhemmässä refaktoroinnissa vv_turvalaite-taulun

CREATE TABLE kanava
(
  kanavanro         INTEGER NOT NULL,
  aluenro           INTEGER,
  nimi              VARCHAR,
  kanavatyyppi      VARCHAR,
  aluetyyppi        VARCHAR,
  kiinnitys         VARCHAR, -- (Liikkuvat pollarit TODO: listaa mahdolliset arvot)
  porttityyppi      VARCHAR, -- (Salpaus + Nosto/Lasku TODO: listaa mahdolliset arvot)
  kayttotapa        VARCHAR, -- (Kaukokäyttö TODO: listaa mahdolliset arvot)
  sulku_leveys      DECIMAL,
  sulku_pituus      DECIMAL,
  alus_leveys       DECIMAL,
  alus_pituus       DECIMAL,
  alus_syvyys       DECIMAL,
  alus_korkeus      DECIMAL,
  sulkumaara        INTEGER,
  putouskorkeus_1   DECIMAL,
  putouskorkeus_2   DECIMAL,
  ala_ver_1         VARCHAR, -- TODO: uudelleennimeä nämä sarakkeet kun järkevämmät nimet on tiedossa
  ala_ver_2         VARCHAR,
  yla_ver_1         VARCHAR,
  yla_ver_2         VARCHAR,
  kynnys_1          VARCHAR,
  kynnys_2          VARCHAR,
  vesisto           VARCHAR,
  kanavakokonaisuus VARCHAR,
  kanava_pituus     VARCHAR,
  kanava_leveys     VARCHAR,
  lahtopaikka       VARCHAR,
  kohdepaikka       VARCHAR,
  omistaja          VARCHAR,
  geometria         GEOMETRY,
  luoja             VARCHAR,
  luotu             TIMESTAMP,
  muokkaaja         VARCHAR,
  muokattu          TIMESTAMP
);

CREATE UNIQUE INDEX kanavanro_unique_index
  ON kanava (kanavanro);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'kanavat-haku');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'kanavat-muutospaivamaaran-haku');
