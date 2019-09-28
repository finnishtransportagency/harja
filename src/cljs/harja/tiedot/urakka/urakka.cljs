(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom cursor]]
            [harja.tiedot.navigaatio :as nav]))

(def suunnittelu-default-arvot {:tehtavat {:valinnat {:toimenpide nil
                                                      :valitaso nil}}
                                :kustannussuunnitelma {:hankintakustannukset {:valinnat {:toimenpide :talvihoito
                                                                                         :maksetaan :molemmat
                                                                                         :kopioidaan-tuleville-vuosille? true
                                                                                         :laskutukseen-perustuen-valinta #{}}}
                                                       :hallinnolliset-toimenpiteet {:valinnat {:maksetaan :molemmat}}}})

(defonce tila (atom {:yleiset {:urakka {}}
                     :suunnittelu suunnittelu-default-arvot}))

(defonce yleiset (cursor tila [:yleiset]))

(defonce suunnittelu-tehtavat (cursor tila [:suunnittelu :tehtavat]))

(defonce suunnittelu-kustannussuunnitelma (cursor tila [:suunnittelu :kustannussuunnitelma]))

(add-watch nav/valittu-urakka :urakan-id-watch
           (fn [_ _ _ uusi-urakka]
             (swap! tila (fn [tila]
                           (-> tila
                               (assoc-in [:yleiset :urakka] (dissoc uusi-urakka :alue))
                               (assoc :suunnittelu suunnittelu-default-arvot))))))