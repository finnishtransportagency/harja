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
  ALA_VER_1         DECIMAL,
  ALA_VER_2         DECIMAL,
  YLA_VER_1         DECIMAL,
  YLA_VER_2         DECIMAL,
  kynnys_1          DECIMAL,
  kynnys_2          DECIMAL,
  vesisto           VARCHAR,
  kanavakokonaisuus VARCHAR,
  kanava_pituus     VARCHAR,
  kanava_leveys     VARCHAR,
  lahtopaikka       VARCHAR,
  kohdepaikka       VARCHAR, --Todo uudelleennimeä
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

-- TODO: muuta noi epäselvät sarakenimet kun