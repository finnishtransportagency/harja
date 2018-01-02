CREATE TABLE kanava
(
  kanavanro                 INTEGER NOT NULL,
  aluenro                   INTEGER,
  nimi                      VARCHAR,
  kanavatyyppi              VARCHAR,
  aluetyyppi                VARCHAR,
  kiinnitys                 VARCHAR, -- (Liikkuvat pollarit TODO: listaa mahdolliset arvot)
  porttityyppi              VARCHAR, -- (Salpaus + Nosto/Lasku TODO: listaa mahdolliset arvot)
  kayttotapa                VARCHAR, -- (Kaukokäyttö TODO: listaa mahdolliset arvot)
  sulku_leveys              NUMERIC (10,2),
  sulku_pituus              NUMERIC (10,2),
  alus_leveys               NUMERIC (10,2),
  alus_pituus               NUMERIC (10,2),
  alus_syvyys               NUMERIC (10,2),
  alus_korkeus              NUMERIC (10,2),
  sulkumaara                INTEGER,
  putouskorkeus_1           NUMERIC (10,2),
  putouskorkeus_2           NUMERIC (10,2),
  alakanavan_alavertaustaso VARCHAR,
  alakanavan_ylavertaustaso VARCHAR,
  ylakanavan_ylavertaustaso VARCHAR,
  ylakanavan_alavertaustaso VARCHAR,
  kynnys_1                  VARCHAR,
  kynnys_2                  VARCHAR,
  vesisto                   VARCHAR,
  kanavakokonaisuus         VARCHAR,
  kanava_pituus             NUMERIC (10,2),
  kanava_leveys             NUMERIC (10,2),
  lahtopaikka               VARCHAR,
  kohdepaikka               VARCHAR,
  omistaja                  VARCHAR,
  geometria                 GEOMETRY,
  luoja                     VARCHAR,
  luotu                     TIMESTAMP,
  muokkaaja                 VARCHAR,
  muokattu                  TIMESTAMP
);

CREATE UNIQUE INDEX kanavanro_unique_index
  ON kanava (kanavanro);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'kanavat-haku');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'kanavat-muutospaivamaaran-haku');
