(ns harja.tiedot.ilmoituskuittaukset
  (:require [reagent.core :refer [atom]]
            [harja.domain.ilmoitusapurit :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce uusi-kuittaus (atom nil))

(defn laheta-uusi-kuittaus [kuittaus]
  (k/post! :tallenna-ilmoitustoimenpide kuittaus nil true))

(defn alusta-uusi-kuittaus [valittu-ilmoitus]
  (if (nil? valittu-ilmoitus)
    (reset! uusi-kuittaus nil)
    (let [kayttaja @istunto/kayttaja
          organisaatio (:organisaatio kayttaja)]
      (reset! uusi-kuittaus
              {:ilmoituksen-id          (:id @valittu-ilmoitus)
               :ulkoinen-ilmoitusid     (:ilmoitusid @valittu-ilmoitus)
               :tyyppi                  :vastaanotto
               :ilmoittaja-etunimi      (:etunimi kayttaja)
               :ilmoittaja-sukunimi     (:sukunimi kayttaja)
               :ilmoittaja-matkapuhelin (:puhelin kayttaja)
               :ilmoittaja-tyopuhelin   (:puhelin kayttaja)
               :ilmoittaja-sahkoposti   (:sahkoposti kayttaja)
               :ilmoittaja-organisaatio (:nimi organisaatio)
               :ilmoittaja-ytunnus      (:ytunnus organisaatio)}))))
