
-- Poistetaan uniikki vaatimus roolista, koska niitä voi lisätä ja poistaa historian saatossa

ALTER TABLE kayttaja_rooli DROP CONSTRAINT uniikki_kayttaja_rooli;

CREATE INDEX kayttajan_ajantasaiset_roolit
          ON kayttaja_rooli (kayttaja, rooli)
       WHERE poistettu=false;

CREATE INDEX kayttajan_ajantasaiset_urakka_roolit
          ON kayttaja_urakka_rooli (kayttaja,urakka,rooli)
       WHERE poistettu=false;

