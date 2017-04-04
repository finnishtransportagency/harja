(ns harja.views.tilannekuva.yllapito
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.views.urakka.yleiset :refer [urakkaan-liitetyt-kayttajat]]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction-writable]]))

(def yhteyshenkilot (atom nil))

(defn- yhteyshenkilot-view [tiedot]
  (fn [tiedot]
    (if tiedot
      [:div
       [urakkaan-liitetyt-kayttajat nil]
       [grid/grid
        {:otsikko "Yhteyshenkilöt"
         :tyhja "Ei yhteyshenkilöitä."}
        [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
         {:otsikko "Nimi" :nimi :nimi :tyyppi :string
          :hae #(str (:etunimi %) " " (:sukunimi %))}
         {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin}
         {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin}
         {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email}]
        tiedot]]
      [ajax-loader "Haetaan yhteyshenkilöitä..."])))

(defn- yhteyshenkilot-modal []
  (go (<! (timeout 5000))
      (log "NO NYT!") ;; TODO HAE PALVELIMELTA
      (reset! yhteyshenkilot []))
  [yhteyshenkilot-view @yhteyshenkilot])

(defn nayta-yhteyshenkilot-modal! [yhteyshenkilot]
  (modal/nayta!
    {:otsikko "Kohteen urakan yhteyshenkilöt"
     :footer [:span
              [:button.nappi-toissijainen {:type "button"
                                           :on-click #(do (.preventDefault %)
                                                          (modal/piilota!))}
               "Sulje"]]}
    [yhteyshenkilot-modal]))