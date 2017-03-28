(ns harja.views.urakka.paallystys-muut-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.yllapitokohteet.muut-kustannukset :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.validointi :as validointi]
            [cljs-time.core :as t]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

;; Ylläpitokohteiden sarakkeiden leveydet
(def kustannus-selite-leveys 5)
(def kustannus-hinta-leveys 3)
(def kustannus-pvm-leveys 3)



(defn- rivi-poistettavissa? [m]
  (log "rivi-poistettavissa? " (pr-str m))
  (or (-> m :muokattava) (-> m :id neg?)))

(def grid-opts {:otsikko "Urakan muut kustannukset (ei kohdekohtaiset)"
                :voi-lisata? true
                :voi-muokata-rivia? :muokattava
                :esta-poistaminen? (complement rivi-poistettavissa?)
                :esta-poistaminen-tooltip
                (fn [_] "Kohteeseen liittymättömästä sanktiosta johtuvaa kustannusta ei voi poistaa.")})

(defn- validoi-rajattu-numero-jos-muokattava [arvo rivi]
  (when (or (:muokattava rivi) (neg? (:id rivi)))
    (when (neg? (js/parseFloat arvo))
      "Summan on oltava positiivinen")))

(def grid-skeema
  [{:otsikko "Pvm" :nimi :pvm :fmt pvm/pvm
    :validoi [[:ei-tyhja "Anna päivämäärä"]]
    :tyyppi :pvm :leveys kustannus-pvm-leveys}
   {:otsikko "Kustannuksen kuvaus" :nimi :selite
    :validoi [[:ei-tyhja "Anna kuvaus"]]
    :tyyppi :string :leveys kustannus-selite-leveys}
   {:otsikko "Summa" :nimi :hinta :fmt fmt/euro-opt
    :validoi [validoi-rajattu-numero-jos-muokattava]
    :tyyppi :numero :leveys kustannus-hinta-leveys}])

(defn muut-kustannukset [urakka]
  (komp/luo
   (komp/lippu tiedot/nakymassa?)
   (fn [urakka]
     [:div.muut-kustannukset
      [grid/grid (assoc grid-opts
                        :tallenna #(tiedot/tallenna-muut-kustannukset! urakka tiedot/muiden-kustannusten-tiedot %)
                        :tyhja (if (nil? @tiedot/muiden-kustannusten-tiedot)
                                 [ajax-loader "Haetaan kustannuksia..."]
                                 "Ei kustannuksia"))
       grid-skeema
       @tiedot/grid-tiedot]])))
