(ns harja.views.toimenpidekoodit
  "Toimenpidekoodien ylläpitonäkymä"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.asiakas.kommunikaatio :as k]
            [clojure.string :as str]
            [bootstrap :as bs]))

(def koodit "id->koodi mäppäys kaikista toimenpidekoodeista" (atom nil))

(def uusi-tehtava "uuden tehtävän kirjoittaminen" (atom ""))

(def valittu-taso1 "Valittu 1. tason SAMPO koodi" (atom nil))
(def valittu-taso2 "Valittu 2. tason SAMPO koodi" (atom nil))
(def valittu-taso3 "Valittu 3. tason SAMPO koodi" (atom nil))

(defonce valittu-toimenpidekoodi (atom nil))

(defn nayta-koodi
  "Esitysmuoto koodille, näyttää kaikki vanhemmat myös"
  [koodi]
  (let [koodit @koodit]
    (loop [acc []
           koodi koodi]
      (if-not koodi
        [:span.toimepidekoodi
         (butlast (interleave
                   (for [{:keys [nimi koodi taso]} (reverse acc)]
                     [:span {:class (str "taso" taso)}
                      koodi " " nimi])
                   (repeat " / ")))]
        (recur (conj acc koodi)
               (get koodit (:emo koodi)))))))

  
(defn hae-koodeja [termi]
  (let [termi (.toLowerCase termi)]
    (vec (filter (fn [{koodi :koodi nimi :nimi}]
                   (or (not= -1 (.indexOf koodi termi))
                       (not= -1 (.indexOf (.toLowerCase nimi) termi))))
                 (vals @koodit)))))


(defn lisaa-tehtavakoodi [taso3 tehtavanimi]
  (k/post! :lisaa-toimenpidekoodi {:nimi tehtavanimi
                                   :emo (:id taso3)}
           (fn [koodi]
             (if-let [id (:id koodi)]
               (swap! koodit assoc id koodi))))) 

(defn poista-tehtavakoodi [koodi]
  (k/post! :poista-toimenpidekoodi koodi
           (fn [ok]
             (.log js/console "poisto: " ok)
             (swap! koodit dissoc (:id koodi)))))

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
           [bs/panel {}
            "Tehtävät"
            [:table.tehtavakoodit
             [:thead
              [:tr
               [:th "Nimi"]
               [:th "Toiminnot"]
               ]]
             
             [:tbody
              (let [tehtavat (filter #(= (:emo %) emo3) (get koodit-tasoittain 4))]
                (if (empty? tehtavat)
                  [:tr [:td.eiTehtavia {:colspan 2} "Ei tehtäviä"]]
                  (for [tpk tehtavat]
                    ^{:key (:id tpk)}
                    [:tr
                     [:td (:nimi tpk)]
                     [:td
                      [:span.glyphicon.glyphicon-edit]
                      [:span.glyphicon.glyphicon-trash {:on-click #(poista-tehtavakoodi tpk)
                                                        :aria-hidden true}]]])))
              [:tr.uusitehtavakoodi
               [:td [:input {:type "text" :placeholder "Tehtävän nimi..." :value @uusi-tehtava
                             :on-change #(reset! uusi-tehtava (-> % .-target .-value))
                             :on-key-up #(when (and (= 13 (.-keyCode %))
                                                       (not (empty? @uusi-tehtava)))
                                              (lisaa-tehtavakoodi taso3 @uusi-tehtava)
                                              (reset! uusi-tehtava ""))}]]
               [:td [:button.btn.btn-primary.btn-sm {:on-click #(do (lisaa-tehtavakoodi taso3 @uusi-tehtava)
                                                                    (reset! uusi-tehtava ""))}
                     "Tallenna"]]]]]])
          ]))
    
    {:component-did-mount (fn [this]
                            (k/post! :hae-toimenpidekoodit nil
                                     #(loop [acc {}
                                             [tpk & tpkt] %]
                                        (if-not tpk
                                          (reset! koodit acc)
                                          (recur (assoc acc (:id tpk) tpk)
                                                 tpkt)))))}))

  
 
