(ns harja.views.tilannekuva.yllapito
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction-writable]]))

(def yhteyshenkilot (atom nil))

(defn- yhteyshenkilot-view [yhteyshenkilot]
  (fn [yhteyshenkilot]
    (log "RENDER VIEW")
    (if yhteyshenkilot
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
        yhteyshenkilot]]
      [ajax-loader "Haetaan yhteyshenkilöitä..."])))

(defn- yhteyshenkilot-modal []
  (go (<! (timeout 5000))
      (log "NO NYT!") ;; TODO HAE PALVELIMELTA
      (reset! yhteyshenkilot []))
  [yhteyshenkilot-view @yhteyshenkilot])

(defn nayta-yhteyshenkilot-modal! [yhteyshenkilot]
  (log "Näytetään yhteyshenkilöt modalissa: " (pr-str yhteyshenkilot))
  (modal/nayta!
    {:otsikko "Kohteen urakan yhteyshenkilöt"
     :footer [:span
              [:button.nappi-toissijainen {:type "button"
                                           :on-click #(do (.preventDefault %)
                                                          (modal/piilota!))}
               "Sulje"]]}
    [yhteyshenkilot-modal]))