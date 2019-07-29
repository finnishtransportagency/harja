(ns harja.ui.taulukko.osa
  "Määritellään taulukon osat täällä."
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
    (-> this meta ::janan-id))
  (osan-tila [this]))

(defrecord Laajenna [osan-id teksti aukaise-fn parametrit]
  p/Tila
  (hae-tila [this]
    (:tila this))
  (aseta-tila! [this]
    (assoc this :tila (atom false)))
  p/Osa
  (piirra-osa [this]
    (let [auki? (or (p/hae-tila this)
                    (atom false))]
      (fn [this]
        (let [{:keys [id class]} (:parametrit this)]
          [:span.klikattava.osa-laajenna
           {:class class
            :id id
            :on-click
            #(do (.preventDefault %)
                 (swap! auki? not)
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
    (-> this meta ::janan-id))
  (osan-tila [this]
    (p/hae-tila this)))

(defrecord Raha [osan-id summa parametrit])

(defn luo-tilallinen-laajenna [osan-id teksti aukaise-fn parametrit]
  (p/aseta-tila! (->Laajenna osan-id teksti aukaise-fn parametrit)))