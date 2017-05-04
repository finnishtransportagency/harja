(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.otsikkokomponentti :refer [otsikot]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def testidata [{:id 0 :nimi "Varkaus, Kuopion väylä"}
                {:id 1 :nimi "Kopio, Iisalmen väylä"}])

(defn- otsikon-sisalto [sijainnit]
  [grid/grid
   {:vetolaatikot {0 [:div "Sisältö tulee tähän"]
                   1 [:div "Sisältö tulee tähän"]}}
   [{:tyyppi :vetolaatikon-tila :leveys 5}
    {:otsikko "Sijainti" :nimi :nimi :leveys 95}]
   sijainnit])

(defn kokonaishintaiset-toimenpiteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div
       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]

       [otsikot ["Viitat (510kpl)"
                 (fn [] [otsikon-sisalto testidata])
                 "Poljut (0)"
                 (fn [] [otsikon-sisalto testidata])
                 "Tukityöt (0)"
                 (fn [] [otsikon-sisalto testidata])]]])))

(defn kokonaishintaiset-toimenpiteet []
  [tuck tiedot/tila kokonaishintaiset-toimenpiteet*])