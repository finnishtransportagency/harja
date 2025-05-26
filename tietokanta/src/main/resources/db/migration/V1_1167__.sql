-- Lisää uudet tiemerkintäkustannus taulut
CREATE TABLE tiemerkinta_yllapitokohteen_kustannus (
  id serial PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde (id),
  luoja INTEGER REFERENCES kayttaja (id),
  luotu TIMESTAMP NOT NULL DEFAULT NOW(),
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja (id),
  
  linjamerkinnat NUMERIC(10, 2),
  pienmerkinnat NUMERIC(10, 2),
  jyrsinnat NUMERIC(10, 2)
);


COMMENT ON TABLE tiemerkinta_yllapitokohteen_kustannus IS
    E'Tauluun kirjataan tiemerkintaurakoissa paallyskohteisiin liitoksissa olevia kustannuksia. Jokainen rivi linkittyy ylläpitokohteeseen yllapitokohde-sarakkeen kautta. Tietoja käytetään tiemerkintäurakoiden kustannusseurantaan.';

CREATE TABLE tiemerkinta_paikkauskohteen_kustannus (
  id serial PRIMARY KEY,
  paikkauskohde INTEGER REFERENCES paikkauskohde (id),
  luoja INTEGER REFERENCES kayttaja (id),
  luotu TIMESTAMP NOT NULL DEFAULT NOW(),
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja (id),
  
  linjamerkinnat NUMERIC(10, 2),
  pienmerkinnat NUMERIC(10, 2),
  jyrsinnat NUMERIC(10, 2)
);


COMMENT ON TABLE tiemerkinta_paikkauskohteen_kustannus IS
    E'Tauluun kirjataan tiemerkintaurakoissa paikkauskohteisiin liitoksissa olevia kustannuksia. Jokainen rivi linkittyy paikkauskohteeseen paikkauskohde-sarakkeen kautta. Tietoja käytetään tiemerkintäurakoiden kustannusseurantaan.';