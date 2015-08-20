ALTER TABLE muutoshintainen_tyo DROP CONSTRAINT muutoshintainen_tyo_urakka_sopimus_tehtava_alkupvm_loppupvm_key;

CREATE UNIQUE INDEX uniikki_muutoshintainen_tyo
  ON muutoshintainen_tyo (urakka, sopimus, tehtava, alkupvm, loppupvm)
  WHERE poistettu = false;

