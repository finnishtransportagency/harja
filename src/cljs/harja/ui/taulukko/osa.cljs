(ns harja.ui.taulukko.osa
  "Määritellään taulukon osat täällä."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.protokollat :as p]))

(defrecord Teksti [osan-id teksti parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{:keys [id class]} (:parametrit this)]
      [:div.solu.osa-teksti {:class class
                        :id id}
       (:teksti this)]))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    osan-id)
  (osan-janan-id [this]
    (-> this meta ::janan-id))
  (osan-tila [this]))

(defrecord Otsikko [osan-id otsikko jarjesta-fn! parametrit]
  p/Osa
  (piirra-osa [_]
    (let [otsikon-jarjestys-fn! (fn [jarjesta-fn! e]
                                  (.preventDefault e)
                                  (jarjesta-fn!))]
      (fn [this]
        (let [{:keys [id class]} (:parametrit this)]
          [:div.solu.osa-otsikko {:class class
                                  :id id}
           (:otsikko this)
           [:span.klikattava.otsikon-jarjestys {:on-click (r/partial otsikon-jarjestys-fn! (:jarjesta-fn! this))}
            [ikonit/sort]]]))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-janan-id [this]
    (-> this meta ::janan-id))
  (osan-tila [this]))

(defrecord Syote [osan-id toiminnot parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{:keys [id class type value name readonly? required? tabindex disabled?
                  checked? default-checked? indeterminate?
                  alt height src width
                  autocomplete max max-length min min-length pattern placeholder size]} (:parametrit this)
          {:keys [on-blur on-change on-click on-focus on-input on-key-down on-key-press
                  on-key-up]} (:toiminnot this)
          parametrit (into {}
                           (remove (fn [[_ arvo]]
                                     (nil? arvo))
                                   {;; Inputin parametrit
                                    :class class
                                    :id id
                                    :type type
                                    :value value
                                    :name name
                                    :read-only readonly?
                                    :required required?
                                    :tab-index tabindex
                                    :disabled disabled?
                                    ;; checkbox or radio paramterit
                                    :checked checked?
                                    :default-checked default-checked?
                                    :indeterminate indeterminate?
                                    ;; kuvan parametrit
                                    :alt alt
                                    :height height
                                    :src src
                                    :width width
                                    ;; numero/teksti input
                                    :auto-complete autocomplete
                                    :max max
                                    :max-length max-length
                                    :min min
                                    :min-length min-length
                                    :pattern pattern
                                    :placeholder placeholder
                                    :size size
                                    ;; GlobalEventHandlers
                                    :on-blur on-blur
                                    :on-change on-change
                                    :on-click on-click
                                    :on-focus on-focus
                                    :on-input on-input
                                    :on-key-down on-key-down
                                    :on-key-press on-key-press
                                    :on-key-up on-key-up}))]
      [:input.solu.osa-syote parametrit]))
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
          [:span.solu.klikattava.osa-laajenna
           {:class class
            :id id
            :on-click
            #(do (.preventDefault %)
                 (swap! auki? not)
                 (aukaise-fn this @auki?))}
           teksti
           (if @auki?
             ^{:key "laajenna-auki"}
             [ikonit/livicon-chevron-down]
             ^{:key "laajenna-kiini"}
             [ikonit/livicon-chevron-left])]))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    osan-id)
  (osan-janan-id [this]
    (-> this meta ::janan-id))
  (osan-tila [this]
    (p/hae-tila this)))

(defn luo-tilallinen-laajenna [osan-id teksti aukaise-fn parametrit]
  (p/aseta-tila! (->Laajenna osan-id teksti aukaise-fn parametrit)))