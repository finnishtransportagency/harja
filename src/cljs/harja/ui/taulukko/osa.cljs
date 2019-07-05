(ns harja.ui.taulukko.osa
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.protokollat :as p]))

(defrecord Teksti [osan-id teksti parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{:keys [id class]} (:parametrit this)]
      [:div {:class class
             :id id}
       (:teksti this)]))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    osan-id)
  (osan-janan-id [this]
    (-> this meta ::janan-id)))

(defrecord Laajenna [osan-id teksti aukaise-fn]
  p/Osa
  (piirra-osa [this]
    (let [auki? (atom false)]
      (fn [this]
        (let [{:keys [id class]} (:parametrit this)]
          [:span.klikattava.osa-laajenna
           {:class class
            :id id
            :on-click
            #(do (.preventDefault %)
                 (swap! auki? not)
                 (println "TÄMÄ: " this)
                 (aukaise-fn this @auki?))}
           teksti
           (if @auki?
             ^{:key "laajenna-auki"}
             [ikonit/livicon-chevron-down "oikealle"]
             ^{:key "laajenna-kiini"}
             [ikonit/livicon-chevron-left "oikealle"])]))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    osan-id)
  (osan-janan-id [this]
    (-> this meta ::janan-id)))

(defrecord Raha [osan-id summa parametrit])