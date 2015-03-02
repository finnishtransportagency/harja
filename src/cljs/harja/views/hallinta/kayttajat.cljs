(ns harja.views.hallinta.kayttajat
  "Käyttäjähallinnan näkymä"
  (:require [reagent.core :refer [atom] :as re]
            [cljs.core.async :refer [<! chan]]

            [harja.tiedot.kayttajat :as k]
            [harja.ui.grid :as grid])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))


(defn kayttajat
  "Käyttäjälistauskomponentti"
  []
  (let [haku (atom "")
        sivu (atom 0)
        sivuja (atom 0)
        kayttajat (atom nil)]

    ;; Haetaan sivun ja datan perusteella, hakee uudestaan jos data muuttuu
    (run! (let [haku @haku
                sivu @sivu]
            (go (let [[lkm data] (<! (k/hae-kayttajat haku (* sivu 50) 50))]
                  (reset! sivuja (int (js/Math.ceil (/ lkm 50))))
                  (reset! kayttajat data)))))

    (fn []
      [grid/grid
       {:otsikko "Käyttäjät"
        :tyhja "Ei käyttäjiä."
        }
       
       [{:otsikko "Nimi" :hae #(str (:etunimi %) " " (:sukunimi %))}
        {:otsiko "Tyyppi" :nimi :tyyppi}
        ]
       
       @kayttajat]
      
      
      )))
