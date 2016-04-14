(ns harja.kyselyt.ilmoitukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/ilmoitukset.sql"
            {:positional? true})

(defn ilmoitukset/luo-ilmoitustoimenpide
  [db
   ilmoitus
   ilmoitusid
   kuitattu
   vapaateksti
   kuittaustyyppi
   kuittaaja-henkilo-etunimi
   kuittaaja-henkilo-sukunimi
   kuittaaja-henkilo-matkapuhelin
   kuittaaja-henkilo-tyopuhelin
   kuittaaja-henkilo-sahkoposti
   kuittaaja-organisaatio-nimi
   kuittaaja-organisaatio-ytunnus
   kasittelija-henkilo-etunimi
   kasittelija-henkilo-sukunimi
   kasittelija-henkilo-matkapuhelin
   kasittelija-henkilo-tyopuhelin
   kasittelija-henkilo-sahkoposti
   kasittelija-organisaatio-nimi
   kasittelija-organisaatio-ytunnus]
  (let [toimenpide (harja.kyselyt.ilmoitukset/luo-ilmoitustoimenpide<!
                     db
                     ilmoitus
                     ilmoitusid
                     kuitattu
                     vapaateksti
                     kuittaustyyppi
                     kuittaaja-henkilo-etunimi
                     kuittaaja-henkilo-sukunimi
                     kuittaaja-henkilo-matkapuhelin
                     kuittaaja-henkilo-tyopuhelin
                     kuittaaja-henkilo-sahkoposti
                     kuittaaja-organisaatio-nimi
                     kuittaaja-organisaatio-ytunnus
                     kasittelija-henkilo-etunimi
                     kasittelija-henkilo-sukunimi
                     kasittelija-henkilo-matkapuhelin
                     kasittelija-henkilo-tyopuhelin
                     kasittelija-henkilo-sahkoposti
                     kasittelija-organisaatio-nimi
                     kasittelija-organisaatio-ytunnus)]
    (when (= :lopetus kuittaustyyppi)
      (poista-ilmoituksen-viestit! db (:id toimenpide)))
    toimenpide))
