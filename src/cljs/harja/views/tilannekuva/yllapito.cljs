(ns harja.views.tilannekuva.yllapito
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]
            [harja.ui.modal :as modal])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction-writable]]))

(defn- yllapitokohteen-yhteyshenkilot-modal [yhteyshenkilot]
  (log "Näytetään yhteyshenkilöt modalissa: " (pr-str yhteyshenkilot))
  (modal/nayta!
    {:otsikko "Kohteen urakan yhteyshenkilöt"
     :footer [:span
              [:button.nappi-toissijainen {:type "button"
                                           :on-click #(do (.preventDefault %)
                                                          (modal/piilota!))}
               "Sulje"]]}
    [:div
     [grid/grid
      {:otsikko "Yhteyshenkilöt"
       :tyhja "Ei yhteyshenkilöitä."}
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
       {:otsikko "Nimi" :nimi :nimi :tyyppi :string
        :hae #(str (:etunimi %) " " (:sukunimi %))}
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin}
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email}]
      yhteyshenkilot]]))