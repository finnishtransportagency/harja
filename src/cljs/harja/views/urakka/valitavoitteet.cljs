(ns harja.views.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as vt]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn valitavoitteet
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  [ur]
  (let [tavoitteet (atom nil)
        vaihda-urakka! (fn [ur]
                         (go (reset! tavoitteet (<! (vt/hae-urakan-valitavoitteet ur)))))]
    (vaihda-urakka! ur)
    (komp/luo
     
     {:component-will-receive-props (fn [_ & [_ ur]]
                                      (log "uusi urakka: " (pr-str (dissoc ur :alue)))
                                      (vaihda-urakka! ur))}
     
     (fn [ur]
       [:div.valitavoitteet
        [:br][:br][:br] ;; FIXME: mieti mitä pienelle kartalle pitäisi tehdä, jää "muokkaa" napin alle
        [grid/grid
         {:otsikko "Välitavoitteet"
          :tallenna #(logt %)
          }

         [{:otsikko "Nimi" :leveys "25%" :nimi :nimi :tyyppi :string}
          {:otsikko "Takaraja" :leveys "15%" :nimi :takaraja :fmt pvm/pvm :tyyppi :pvm}
          {:otsikko "Valmistunut" :leveys "15%"
           :hae (comp :pvm :valmis) :aseta (fn [rivi arvo]
                                             (assoc-in rivi [:valmis :pvm] arvo))
           :nimi :valmis :fmt #(if-not % "Ei valmis" (pvm/pvm %)) :tyyppi :pvm}
                                   
                                          
                                     
          ]

         @tavoitteet]]))))

         
            
            
