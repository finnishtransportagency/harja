(ns harja.ui.palaute
  (:require [clojure.string :as string]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as str]
            [reagent.core :refer [atom]]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.tapahtumat :as t]
            [harja.pvm :as pvm]
            [harja.loki :as log]
            [harja.tiedot.palaute :as tiedot]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.ikonit :as ikonit]))

(defn palautelomake []
  (let [laheta-palaute (fn [] (log "Painoit nappia"))]
    [napit/yleinen "Lähetä palaute" laheta-palaute]))

(defn palaute-linkki []
  [:a.palautelinkki.klikattava
   {:on-click (modal/nayta! {:otsikko "Palautteen lähettäminen"}
                            palautelomake)}
   [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "Palautetta!"]])
