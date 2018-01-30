-- Lisää taulu, johon tullaan tallentamaan odottavien sähköpostien tietoja (aluksi tiemerkinnän valmistuminen)
CREATE TYPE odottava_sahkoposti_tyyppi AS ENUM ('tiemerkinta_valmistunut');

CREATE TABLE odottava_sahkoposti (
  id                 SERIAL PRIMARY KEY,
  tyyppi odottava_sahkoposti_tyyppi NOT NULL,
  "vastaanottajat"   TEXT []   NOT NULL,
  viesti             TEXT      NOT NULL,
  kopio_lahettajalle TEXT -- Mailin aikaansaaneen käyttäjän s-posti, johon lähetetään kopio viestistä (tai NULL)
);