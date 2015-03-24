(ns harja.views.toimenpidekoodit
  "Toimenpidekoodien ylläpitonäkymä"
  (:require [reagent.core :refer [atom wrap] :as reagent]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [bootstrap :as bs]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [cljs.core.async.macros :refer [go]]))



 
;; PENDING: en laittanut näille omia harja.tiedot alla olevaa nimiavaruutta
;; siirretään omaansa, jos näitä kooditietoja tarvitaan muualtakin kuin täältä
;; hallintanäkymästä.
(def koodit "id->koodi mäppäys kaikista toimenpidekoodeista" (atom nil))

(comment
  (add-watch koodit ::debug (fn [_ _ old new]
                              (.log js/console "koodit: " (pr-str old) " => " (pr-str new)))))

(def uusi-tehtava "uuden tehtävän kirjoittaminen" (atom ""))

(defonce valittu-taso1 (atom nil))
(defonce valittu-taso2 (atom nil))
(defonce valittu-taso3 (atom nil))

(defonce valittu-toimenpidekoodi (atom nil))                

(defn resetoi-koodit [tiedot]
  (loop [acc {}
         [tpk & tpkt] tiedot]
    (if-not tpk
      (reset! koodit acc)
      (recur (assoc acc (:id tpk) tpk)
             tpkt))))

(defn tallenna-tehtavat [tehtavat uudet-tehtavat]
  (go (let [lisattavat
          (mapv #(assoc % :emo (:id @valittu-taso3))
                (into []
                      (comp (filter 
                              #(and 
                                 (not (:poistettu %))
                                 (< (:id %) 0))))
                      uudet-tehtavat))
          muokattavat (into []
                            (filter (fn [t]
                                      ;; vain muuttuneet "vanhat" rivit
                                      (not (some #(= % t) tehtavat)))
                                    (into []
                                          (comp (filter 
                                                  #(and 
                                                     (not (:poistettu %))
                                                     (> (:id %) 0))))
                                          uudet-tehtavat)))
          poistettavat 
          (into []
                (keep #(when (and (:poistettu %)
                                  (> (:id %) 0))
                         (:id %)))
                uudet-tehtavat)
          res (<! (k/post! :tallenna-tehtavat
                           {:lisattavat lisattavat
                            :muokattavat muokattavat
                            :poistettavat poistettavat}))]
      (resetoi-koodit res))))

(def toimenpidekoodit
  "Toimenpidekoodien hallinnan pääkomponentti"
  (with-meta 
    (fn []
      (let [kaikki-koodit @koodit
            koodit-tasoittain (group-by :taso (sort-by :koodi (vals kaikki-koodit)))
            taso1 @valittu-taso1
            taso2 @valittu-taso2
            taso3 @valittu-taso3
            valinnan-koodi #(get kaikki-koodit (-> % .-target .-value js/parseInt))]
        [:div.container-fluid.toimenpidekoodit
         [:div.input-group
          [:select#taso1 {:on-change #(do (reset! valittu-taso1 (valinnan-koodi %))
                                          (reset! valittu-taso2 nil)
                                          (reset! valittu-taso3 nil))
                          :value (str (:id @valittu-taso1))}
           [:option {:value ""} "-- Valitse 1. taso --"] 
           (for [tpk (get koodit-tasoittain 1)]
             ^{:key (:id tpk)}
             [:option {:value (:id tpk)} (str (:koodi tpk) " " (:nimi tpk))])]]
         [:div.input-group
          [:select#taso2 {:on-change #(do (reset! valittu-taso2 (valinnan-koodi %))
                                          (reset! valittu-taso3 nil))
                          :value (str (:id @valittu-taso2))}
           [:option {:value ""} "-- Valitse 2. taso --"]
           (when-let [emo1 (:id taso1)]
             (for [tpk (filter #(= (:emo %) emo1) (get koodit-tasoittain 2))]
               ^{:key (:id tpk)}
               [:option {:value (:id tpk)} (str (:koodi tpk) " " (:nimi tpk))]))]]
         [:div.input-group
          [:select#taso3 {:on-change #(reset! valittu-taso3 (valinnan-koodi %))
                          :value (str (:id @valittu-taso3))}
           [:option {:value ""} "-- Valitse 3. taso --"]
           (when-let [emo2 (:id taso2)]
             (for [tpk (filter #(= (:emo %) emo2) (get koodit-tasoittain 3))]
               ^{:key (:id tpk)}
               [:option {:value (:id tpk)} (str (:koodi tpk) " " (:nimi tpk))]))
           ]]

         [:br]
         (when-let [emo3 (:id taso3)]
           (let [tehtavat (filter #(= (:emo %) emo3) (get koodit-tasoittain 4))]
             [grid/grid
              {:otsikko "Tehtävät"
               :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
               :tallenna (istunto/jos-rooli istunto/rooli-jarjestelmavastuuhenkilo 
                                            #(tallenna-tehtavat tehtavat %) 
                                            :ei-mahdollinen)
               :tunniste :id}
              
              [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "85%"}
               {:otsikko "Yksikkö" :nimi :yksikko    :tyyppi :string :leveys "15%"}]
              tehtavat]))
          ]))
    
    {:displayName  "toimenpidekoodit"
     :component-did-mount (fn [this]
                            (go (let [res (<! (k/get! :hae-toimenpidekoodit))]
                                  (resetoi-koodit res))))}))

  
 
