(ns harja.views.urakka.muut-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.muut-kustannukset :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t]
            [harja.pvm :as pvm])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

;; Ylläpitokohteiden sarakkeiden leveydet
(def kustannus-selite-leveys 5)
(def kustannus-hinta-leveys 3)
(def kustannus-pvm-leveys 3)

(def grid-opts {:otsikko "Muut kustannukset"
                :voi-lisata? true
                :voi-muokata-rivia? :muokattava
                :esta-poistaminen? (complement :muokattava)
                :esta-poistaminen-tooltip
                (fn [_] "Kohteeseen liittymättömästä sanktiosta johtuvaa kustannusta ei voi poistaa.")})

(def grid-skeema
  [{:otsikko "Pvm" :nimi :pvm :fmt pvm/pvm
    :tyyppi :pvm :leveys kustannus-pvm-leveys}
   {:otsikko "Kustannuksen kuvaus" :nimi :selite
    :tyyppi :string :leveys kustannus-selite-leveys}
   {:otsikko "Summa" :nimi :hinta
    :tyyppi :numero :leveys kustannus-hinta-leveys}])

(defn muut-kustannukset [urakka]
  (komp/luo
   (fn [urakka]
     [:div.muut-kustannukset
      [grid/grid (assoc grid-opts
                        :tallenna #(tiedot/tallenna-lomake! urakka tiedot/muiden-kustannusten-tiedot %)
                        :tyhja (if (nil? @tiedot/muiden-kustannusten-tiedot)
                                 [ajax-loader "Haetaan kustannuksia..."]
                                 "Ei kustannuksia"))
       grid-skeema
       @tiedot/grid-tiedot]])))
