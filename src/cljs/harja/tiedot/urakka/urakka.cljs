(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom cursor]]
            [clojure.core.async :refer [chan]]
            [harja.tiedot.navigaatio :as nav]))

(def suunnittelu-default-arvot {:tehtavat {:valinnat {:samat-kaikille false
                                                      :toimenpide nil
                                                      :valitaso nil
                                                      :noudetaan 0}}
                                :kustannussuunnitelma {:hankintakustannukset {:valinnat {:toimenpide :talvihoito
                                                                                         :maksetaan :molemmat
                                                                                         :kopioidaan-tuleville-vuosille? true
                                                                                         :laskutukseen-perustuen-valinta #{}}}
                                                       :hallinnolliset-toimenpiteet {:valinnat {:maksetaan :molemmat}}
                                                       :kaskytyskanava              (chan)}})

(defn hankinnat-testidata [maara]
  (into []
        (drop 9
              (drop-last 3
                         (mapcat (fn [vuosi]
                                   (map #(identity
                                           {:pvm   (harja.pvm/luo-pvm vuosi % 15)
                                            :maara maara})
                                        (range 0 12)))
                                 (range (harja.pvm/vuosi (harja.pvm/nyt)) (+ (harja.pvm/vuosi (harja.pvm/nyt)) 6)))))))

(defonce tila (atom {:yleiset     {:urakka {}}
                     :laskutus    {:kohdistetut-kulut {:meta        {:haetaan 0}
                                                       :taulukko    nil
                                                       :kulut       []
                                                       :syottomoodi false}}
                     :suunnittelu suunnittelu-default-arvot}))


(defonce laskutus-kohdistetut-kulut (cursor tila [:laskutus :kohdistetut-kulut]))

(defonce yleiset (cursor tila [:yleiset]))

(defonce suunnittelu-tehtavat (cursor tila [:suunnittelu :tehtavat]))

(defonce suunnittelu-kustannussuunnitelma (cursor tila [:suunnittelu :kustannussuunnitelma]))

(add-watch nav/valittu-urakka :urakan-id-watch
           (fn [_ _ _ uusi-urakka]
             (swap! tila (fn [tila]
                           (-> tila
                               (assoc-in [:yleiset :urakka] (dissoc uusi-urakka :alue))
                               (assoc :suunnittelu suunnittelu-default-arvot))))))