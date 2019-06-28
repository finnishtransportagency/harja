(ns harja.tilanhallinta.tila
  "Liike yhtenäisempään tilanhallintaan keskitetyn masterin kautta"
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [cljs.core.async :as async]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [taoensso.timbre :as tlog])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def alkutila {:murupolku {:valinta-auki false
                           :valinnat nil
                           :nakyvissa? true}
               :navigaatio {:valittu-hallintayksikko nil
                            :valittu-urakka nil}
               :haku {:haku-kaynnissa? false
                      :haku-taustalla? false}
               :aluesuodattimet {}})

(defonce master (r/atom alkutila))

(defn- sota-row [row]
    (map (fn [x]
            (let [curr (get row x)]
              (if (and (coll? curr)
                       (not (empty? curr)))
                [:li (str x) [:ul (sota-row curr)]]
                [:li (str (if (coll? x)
                            (flatten (seq x)) x) " - " curr)]))) (keys row)))

(defn state-of-the-atom [& _]
  (let [open? (r/atom true)]
    (fn [& [a]]
      (when a (log "Käytetään annettua ratomia"))
      (let [ks (keys (or a
                         @master))]
        [:div {:class    (str "state-of-the-atom" (when-not @open? "--closed"))
              :on-click #(swap! open? not)} (if @open? "Sulje ikkuna" "Avaa ikkuna")
        [:ol
         (when @open?
           (map (fn [k]
                  (let [curr (get @master k)]
                    (if (and (coll? curr)
                             (not (empty? curr)))
                      [:li (str k) [:ul (sota-row curr)]]
                      [:li (str k " - " curr)]))) ks))]]))))