-- Lisää taulu, johon tullaan tallentamaan odottavien sähköpostien tietoja (aluksi tiemerkinnän valmistuminen)
CREATE TYPE YLLAPITOKOHTEEN_SAHKOPOSTITIEDOT_TYYPPI AS ENUM ('tiemerkinta_valmistunut');

CREATE TABLE yllapitokohteen_sahkopostitiedot (
  id                 SERIAL PRIMARY KEY,
  tyyppi             YLLAPITOKOHTEEN_SAHKOPOSTITIEDOT_TYYPPI NOT NULL,
  yllapitokohde_id   INTEGER REFERENCES yllapitokohde (id)   NOT NULL,
  vastaanottajat     TEXT []                                 NOT NULL,
  saate              TEXT,
  kopio_lahettajalle BOOLEAN                                 NOT NULL DEFAULT FALSE -- Mailin aikaansaaneen käyttäjän s-posti, johon lähetetään kopio viestistä (tai NULL)
);

-- Vain yksi samantyyppinen rivi per ylläpitokohde
ALTER TABLE yllapitokohteen_sahkopostitiedot
  ADD CONSTRAINT uniikki_yllapitokohde UNIQUE (yllapitokohde_id, tyyppi);
