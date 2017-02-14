(ns harja.views.urakka.muut-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.muut-kustannukset :as tiedot]
            ;; [harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot :refer [tallenna-toteuma hae-toteuma]]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

;; Ylläpitokohteiden sarakkeiden leveydet
(def kustannus-selite-leveys 5)
(def kustannus-hinta-leveys 3)
(def kustannus-pvm-leveys 3)

(def grid-opts {:otsikko "Muut kustannukset"
                :muutos (fn [grid]
                          #(log "muut-kustannukset: muutos kutsuttu"))
                :voi-lisata? true
                :voi-muokata-rivia? :muokattava
                :esta-poistaminen? (fn [rivi] (or (not (nil? (:paallystysilmoitus-id rivi))) ;; <- tahan tsekkaus onko kohdistamaton sanktio vai suoraan syötetty?
                                                  (not (nil? (:paikkausilmoitus-id rivi)))))
                :esta-poistaminen-tooltip
                (fn [_] "Kohteeseen liittymättömästä sanktiosta johtuvaa kustannusta ei voi poistaa.")})

(def grid-skeema
  (into [] (concat  [{:otsikko "Selite" :nimi :selite
                      :tyyppi :string :leveys kustannus-selite-leveys
                      :validoi [[:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
                     {:otsikko "Hinta" :nimi :hinta
                      :tyyppi :numero :leveys kustannus-hinta-leveys}
                     {:otsikko "Pvm" :nimi :pvm
                      :tyyppi :pvm :leveys kustannus-pvm-leveys}]

                    [])))

(defn muut-kustannukset [urakka]
  (komp/luo
   (fn [urakka]
     (log "mk komponentti: tiedot" (pr-str @tiedot/grid-tiedot))
     [:div.muut-kustannukset
      [grid/grid (assoc grid-opts
                        :tallenna #(tiedot/tallenna-lomake urakka tiedot/muiden-kustannusten-tiedot %)
                        :tyhja (if (nil? @tiedot/muiden-kustannusten-tiedot)
                                 [ajax-loader "Haetaan kustannuksia..."]
                                 "Ei kustannuksia"))
       grid-skeema
       @tiedot/grid-tiedot
       ]])))
