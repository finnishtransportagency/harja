(ns harja.tiedot.ilmoituskuittaukset
  (:require [reagent.core :refer [atom]]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn uusi-kuittaus-ilmoitukselle [ilmoitus]
  (let [kayttaja @istunto/kayttaja
        organisaatio (:organisaatio kayttaja)]
    {:ilmoituksen-id (:id ilmoitus)
     :ulkoinen-ilmoitusid (:ilmoitusid ilmoitus)
     :tyyppi :vastaanotto
     :ilmoittaja-etunimi (:etunimi kayttaja)
     :ilmoittaja-sukunimi (:sukunimi kayttaja)
     :ilmoittaja-matkapuhelin (:puhelin kayttaja)
     :ilmoittaja-tyopuhelin (:puhelin kayttaja)
     :ilmoittaja-sahkoposti (:sahkoposti kayttaja)
     :ilmoittaja-organisaatio (:nimi organisaatio)
     :ilmoittaja-ytunnus (:ytunnus organisaatio)}))

(defn laheta-kuittaukset! [ilmoitukset kuittaus]
  (k/post! :tallenna-ilmoitustoimenpiteet
           (into []
                 (map #(merge (uusi-kuittaus-ilmoitukselle %) kuittaus))
                 ilmoitukset)))
