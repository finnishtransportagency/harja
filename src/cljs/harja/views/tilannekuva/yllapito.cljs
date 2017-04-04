(ns harja.views.tilannekuva.yllapito
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.views.urakka.yleiset :refer [urakkaan-liitetyt-kayttajat]]
            [harja.ui.modal :as modal]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.domain.roolit :as roolit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction-writable]]))

(def yhteyshenkilot (atom nil))

(defn- yhteyshenkilot-view [yhteyshenkilot-atom]
  (fn [yhteyshenkilot-atom]
    (let [{:keys [fim-kayttajat yhteyshenkilot] :as tiedot} @yhteyshenkilot-atom]
      (case tiedot
        nil [ajax-loader "Haetaan yhteyshenkilöitä..."]
        :ei-oikeutta [:p "Ei käyttöoikeutta tarkastella yhteyshenkilöitä."]
        [:div
         [urakkaan-liitetyt-kayttajat fim-kayttajat]
         [grid/grid
          {:otsikko "Yhteyshenkilöt"
           :tyhja "Ei yhteyshenkilöitä."}
          [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
           {:otsikko "Nimi" :nimi :nimi :tyyppi :string
            :hae #(str (:etunimi %) " " (:sukunimi %))}
           {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin}
           {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin}
           {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email}]
          yhteyshenkilot]]))))

(defn nayta-yhteyshenkilot-modal! [yllapitokohde-id]
  (go (let [vastaus (<! (k/post! :yllapitokohteen-urakan-yhteyshenkilot {:yllapitokohde-id yllapitokohde-id}))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Virhe haettaessa yhteyshenkilöitä!" :warning)
          (reset! yhteyshenkilot vastaus))))

  (modal/nayta!
    {:otsikko "Kohteen urakan yhteyshenkilöt"
     :footer [:span
              [:button.nappi-toissijainen {:type "button"
                                           :on-click #(do (.preventDefault %)
                                                          (modal/piilota!))}
               "Sulje"]]}
    [yhteyshenkilot-view yhteyshenkilot]))