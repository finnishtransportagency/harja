(ns harja.views.ilmoituksen-tiedot
  (:require [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.ui.bootstrap :as bs]
            [clojure.string :refer [capitalize]]

            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne-ja-nimi
                                              +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
                                              +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
                                              kuittaustyypin-selite nayta-tierekisteriosoite]]
            [harja.views.ilmoituskuittaukset :as kuittaukset]))

(defn ilmoitus
  ([ilm] (ilmoitus ilm nil))
  ([ilmoitus kuittauslomake]
  [:div
   [bs/panel {}
    (ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))
    [:span
     [yleiset/tietoja {}
      "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus))
      "Sijainti: " (nayta-tierekisteriosoite (:tr ilmoitus))
      "Otsikko: " (:otsikko ilmoitus)
      "Lyhyt selite: " (:lyhytselite ilmoitus)
      "Pitkä selite: " (when (:pitkaselite ilmoitus)
                         [yleiset/pitka-teksti (:pitkaselite ilmoitus)])
      "Selitteet: " (parsi-selitteet (:selitteet ilmoitus))]

     [:br]
     [yleiset/tietoja {}
      "Ilmoittaja:" (let [henkilo (nayta-henkilo (:ilmoittaja ilmoitus))
                          tyyppi (capitalize (name (get-in ilmoitus [:ilmoittaja :tyyppi])))]
                      (if (and henkilo tyyppi)
                        (str henkilo ", " tyyppi)
                        (str (or henkilo tyyppi))))
      "Puhelinnumero: " (parsi-puhelinnumero (:ilmoittaja ilmoitus))
      "Sähköposti: " (get-in ilmoitus [:ilmoittaja :sahkoposti])]

     [:br]
     [yleiset/tietoja {}
      "Lähettäjä:" (nayta-henkilo (:lahettaja ilmoitus))
      "Puhelinnumero: " (parsi-puhelinnumero (:lahettaja ilmoitus))
      "Sähköposti: " (get-in ilmoitus [:lahettaja :sahkoposti])]]]
   [:div.kuittaukset
    [:h3 "Kuittaukset"]
    [:div
     (when kuittauslomake [kuittauslomake])

     (when-not (empty? (:kuittaukset ilmoitus))
       [:div
        (for [kuittaus (sort-by :kuitattu pvm/jalkeen? (:kuittaukset ilmoitus))]
          (kuittaukset/kuittauksen-tiedot kuittaus))])]]]))