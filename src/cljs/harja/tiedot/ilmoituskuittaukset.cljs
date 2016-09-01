(ns harja.tiedot.ilmoituskuittaukset
  (:require [reagent.core :refer [atom]]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
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

(def kuittauksen-tyypin-vakiofraasi
  {:vastaanotto "Vastaanotettu"
   :aloitus "Aloitettu"
   :lopetus "Lopetettu"
   :muutos "Muutos"
   :vastaus "Vastaus"})

(defn uusi-kuittaus-ilmoitukselle [ilmoitus]
  (let [kayttaja @istunto/kayttaja
        organisaatio (:organisaatio kayttaja)
        vakiotyyppi :vastaanotto]
    {:ilmoituksen-id          (:id ilmoitus)
     :ulkoinen-ilmoitusid     (:ilmoitusid ilmoitus)
     :tyyppi                  vakiotyyppi
     :vapaateksti             (kuittauksen-tyypin-vakiofraasi vakiotyyppi)
     :ilmoittaja-etunimi      (:etunimi kayttaja)
     :ilmoittaja-sukunimi     (:sukunimi kayttaja)
     :ilmoittaja-matkapuhelin (:puhelin kayttaja)
     :ilmoittaja-tyopuhelin   (:puhelin kayttaja)
     :ilmoittaja-sahkoposti   (:sahkoposti kayttaja)
     :ilmoittaja-organisaatio (:nimi organisaatio)
     :ilmoittaja-ytunnus      (:ytunnus organisaatio)}))

(defn laheta-kuittaukset! [ilmoitukset kuittaus]
  (k/post! :tallenna-ilmoitustoimenpiteet
           (into []
                 (map #(merge (uusi-kuittaus-ilmoitukselle %) kuittaus))
                 ilmoitukset)))

(defn alusta-uusi-kuittaus [valittu-ilmoitus]
  (if (nil? valittu-ilmoitus)
    (reset! uusi-kuittaus nil)
    (let [kayttaja @istunto/kayttaja
          organisaatio (:organisaatio kayttaja)]
      (reset! uusi-kuittaus
              (uusi-kuittaus-ilmoitukselle @valittu-ilmoitus)))))

(defn tarkista-ja-paivita-vapaateksti [lomakedata-nyt edellinen-data]
  (let [tyyppi-muuttunut? (not= (:tyyppi edellinen-data)
                                (:tyyppi lomakedata-nyt))
        kasitelty-uusi-data (if tyyppi-muuttunut?
                              (assoc lomakedata-nyt
                                :vapaateksti
                                (kuittauksen-tyypin-vakiofraasi (:tyyppi lomakedata-nyt)))
                              lomakedata-nyt)]
    kasitelty-uusi-data))