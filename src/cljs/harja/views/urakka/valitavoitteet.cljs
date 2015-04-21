(ns harja.views.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as vt]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :refer [rooli-urakassa?]]
            [harja.domain.roolit :as roolit]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))


(defn valitavoite-lomake [ur vt]
  [:div.valitavoite
   [:div.valmis
    (let [{:keys [pvm merkitsija merkitty kommentti]} (:valmis vt)]
      [y/rivi
       y/tietopaneelin-elementtikoko
       
       [y/otsikolla "Valmistunut" "ei vielä valmis"]
       [y/otsikolla "Merkitty valmiiksi" "joskus jolloin"]
       [y/otsikolla "Valmiiksi merkitsijä" "jokumis jokufirma"]
       ])]])
     
;,     (when (and (nil? pvm)
;;                (rooli-urakassa? r/urakoitsijan-urakkaroolit-kirjoitus ur))
;;       ;; Ei ole valmis, sallitaan urakoitsijan käyttäjän merkitä se valmiiksi
;;       [:div "MERKITSE VALMIIKSI" [:button "just do it"]])
       

(defn valitavoitteet
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  [ur]
  (let [tavoitteet (atom nil)
        vaihda-urakka! (fn [ur]
                         (go (reset! tavoitteet (<! (vt/hae-urakan-valitavoitteet (:id ur))))))]
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
          :vetolaatikot (let [vets (into {}
                                         (map (juxt :id valitavoite-lomake))
                                         @tavoitteet)]
                          (log "vetolaatikoita on : " (pr-str vets))
                          vets)
          }

         [{:tyyppi :vetolaatikon-tila :leveys "5%"}
          {:otsikko "Nimi" :leveys "25%" :nimi :nimi :tyyppi :string}
          {:otsikko "Takaraja" :leveys "15%" :nimi :takaraja :fmt pvm/pvm :tyyppi :pvm}
          {:otsikko "Valmistunut" :leveys "15%"
           :hae (comp :pvm :valmis) :aseta (fn [rivi arvo]
                                             (assoc-in rivi [:valmis :pvm] arvo))
           :nimi :valmis :fmt #(if-not % "Ei valmis" (pvm/pvm %)) :tyyppi :pvm}
                                   
                                          
                                     
          ]

         @tavoitteet]]))))

         
            
            
