(ns harja.views.urakka.muut-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.muut-kustannukset :as tiedot-muut-kustannukset]
            ;; [harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot :refer [tallenna-toteuma hae-toteuma]]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce kustannukset-atom (atom {}))

;; Ylläpitokohteiden sarakkeiden leveydet
(def kustannus-nimi-leveys 5)
(def kustannus-summa-leveys 3)

;; muokkaus yllapitokohteet-viewin pohjalla wip

(def grid-opts {:otsikko "Muut kustannukset"
                :tyhja (if (nil? @kustannukset-atom) [ajax-loader "Haetaan kustannuksia..."] "Ei kustannuksia")
                :muutos (fn [grid]
                          #(log "muut-kustannukset: muutos kutsuttu"))
                :voi-lisata? true
                :voi-muokata-rivia? :muokattava
                :esta-poistaminen? (fn [rivi] (or (not (nil? (:paallystysilmoitus-id rivi))) ;; <- tahan tsekkaus onko kohdistamaton sanktio vai suoraan syötetty?
                                                  (not (nil? (:paikkausilmoitus-id rivi)))))
                :esta-poistaminen-tooltip
                (fn [_] "Kohteeseen liittymättömästä sanktiosta johtuvaa kustannusta ei voi poistaa.")})

(def grid-skeema
  (into [] (concat  [{:otsikko "Kustannus" :nimi :kustannus-nimi
                      :tyyppi :string :leveys kustannus-nimi-leveys
                      :validoi [[:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
                     {:otsikko "Hinta" :nimi :hinta
                      :tyyppi :numero :leveys kustannus-summa-leveys}]
                    [])))

(defn muut-kustannukset [urakka]
  (komp/luo
   (fn [urakka]
     [:div.muut-kustannukset
      [grid/grid (assoc grid-opts :tallenna #(tiedot-muut-kustannukset/tallenna-lomake urakka tiedot-muut-kustannukset/lomakedata %))
       grid-skeema
       (tiedot-muut-kustannukset/grid-tiedot)]])))
