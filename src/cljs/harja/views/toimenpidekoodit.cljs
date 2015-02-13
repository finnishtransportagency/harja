(ns harja.views.toimenpidekoodit
  "Toimenpidekoodien ylläpitonäkymä"
  (:require [reagent.core :refer [atom wrap] :as reagent]
            [harja.asiakas.kommunikaatio :as k]
            [clojure.string :as str]
            [bootstrap :as bs]
            [harja.ui.ikonit :as ikonit]
            ))


 
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
  (when (js/confirm (str "Poistetaanko tehtäväkoodi " (:nimi koodi) "?")) 
    (k/post! :poista-toimenpidekoodi koodi
             (fn [ok]
               (.log js/console "poisto: " ok)
               (swap! koodit dissoc (:id koodi))))))

(defn muokkaa-tehtavakoodi [koodi uusi-tehtavakoodi]
  (.log js/console "muokkaa " (pr-str koodi) " => " uusi-tehtavakoodi)
  (let [ok (fn [& _]            
             (swap! koodit update-in [(:id koodi)]
                    #(assoc %
                       :muokattu nil
                       :nimi uusi-tehtavakoodi)))]
    (if (= (:nimi koodi) uusi-tehtavakoodi)
      ;; ei tarvitse muokata, sama koodi
      (ok)

      ;; pyydetään palvelinta vaihtamaan koodi
      (k/post! :muokkaa-toimenpidekoodi (assoc koodi :nimi uusi-tehtavakoodi)
               ok))))

(def tehtavakoodin-muokkausrivi
  (with-meta
    (fn [koodi muokattu]
      [:tr
       [:td [:input {:type "text"
                     :on-change #(reset! muokattu (-> % .-target .-value))
                     :on-key-down #(case (.-keyCode %)
                                     13 (muokkaa-tehtavakoodi koodi @muokattu)
                                     27 (reset! muokattu nil)
                                     nil)
                     :value @muokattu}]]
       [:td
        [:span
         [:span.pull-left 
          {:on-click #(muokkaa-tehtavakoodi koodi @muokattu)}
          (ikonit/ok-sign)]
        [:span.pull-right ]
         {:on-click #(reset! muokattu nil)}
         (ikonit/remove-sign)]]])
    {:component-did-mount #(-> (reagent/dom-node %)
                               (.getElementsByTagName "input")
                               (aget 0)
                               .focus)}))
                

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
                    (if-let [muokattu (:muokattu tpk)]
                      ;; Tätä riviä muokataan
                      [tehtavakoodin-muokkausrivi tpk (wrap muokattu
                                                            swap! koodit assoc-in [(:id tpk) :muokattu])]
                      ;; Normaali rivi
                      [:tr
                       [:td 
                        [:span.tehtavakoodi (:nimi tpk)]]
                       [:td
                        [:span {:on-click #(swap! koodit update-in [(:id tpk) :muokattu]
                                                  (fn [_] (:nimi tpk)))}
                         (ikonit/edit)]
                        [:span {:on-click #(poista-tehtavakoodi tpk)
                                :aria-hidden true}
                         (ikonit/trash)]]]))))
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
    
    {:displayName  "toimenpidekoodit"
     :component-did-mount (fn [this]
                            (k/post! :hae-toimenpidekoodit nil
                                     #(loop [acc {}
                                             [tpk & tpkt] %]
                                        (if-not tpk
                                          (reset! koodit acc)
                                          (recur (assoc acc (:id tpk) tpk)
                                                 tpkt)))))}))

  
 
