ALTER TABLE materiaalin_kaytto DROP CONSTRAINT materiaali_kaytto_uniikki;

CREATE UNIQUE INDEX materiaali_kaytto_uniikki_jos_ei_poistettu
    ON materiaalin_kaytto (alkupvm,loppupvm,materiaali,urakka,sopimus)
    WHERE poistettu = false;