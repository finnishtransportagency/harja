(ns harja.views.toimenpidekoodit
  "Toimenpidekoodien ylläpitonäkymä"
  (:require [reagent.core :refer [atom wrap] :as reagent]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.toimenpidekoodit :refer [koodit tyokoneiden-reaaliaikaseuranna-tehtavat]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(comment
  (add-watch koodit ::debug (fn [_ _ old new]
                              (.log js/console "koodit: " (pr-str old) " => " (pr-str new)))))

(def uusi-tehtava "uuden tehtävän kirjoittaminen" (atom ""))

(defonce valittu-taso1 (atom nil))
(defonce valittu-taso2 (atom nil))
(defonce valittu-taso3 (atom nil))

(defn resetoi-koodit [tiedot]
  (loop [acc {}
         [tpk & tpkt] tiedot]
    (if-not tpk
      (reset! koodit acc)
      (recur (assoc acc (:id tpk) tpk)
             tpkt))))

(defn resetoi-tyokoneiden-reaaliaikaseuranna-tehtavat [tehtavat]
  (reset! tyokoneiden-reaaliaikaseuranna-tehtavat tehtavat))


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
                                        (not= true (:koskematon t)))
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

(defn hinnoittelun-nimi
  [hinnoittelu-str]
  (case hinnoittelu-str
    "kokonaishintainen"
    "kokonais"
    "yksikkohintainen"
    "yksikkö"
    "muutoshintainen"
    "muutos"))

(defn hinnoittelun-nimet
  [hinnoittelu-vec]
  (clojure.string/join ", " (map #(hinnoittelun-nimi %) hinnoittelu-vec)))

(def +hinnoittelu-valinnat+
  [["yksikkohintainen"]
   ["kokonaishintainen"]
   ["muutoshintainen"]
   ["yksikkohintainen" "muutoshintainen"]
   ["yksikkohintainen" "kokonaishintainen"]
   ["kokonaishintainen" "muutoshintainen"]
   ["kokonaishintainen" "yksikkohintainen" "muutoshintainen"]])

(defn hae-emo [kaikki-tehtavat tehtava]
  (second (first (filter #(= (:id (second %))
                             (or (:emo tehtava)
                                 (:emo (second tehtava))))
                         kaikki-tehtavat))))

(defn rakenna-tasot [kaikki-tehtavat tehtavat]
  (map
    (fn [tehtava]
      (let [taso3 (hae-emo kaikki-tehtavat tehtava)
            taso2 (hae-emo kaikki-tehtavat taso3)
            taso1 (hae-emo kaikki-tehtavat taso2)]
        (assoc tehtava :tasot (str "1. " (:nimi taso1) ", 2. " (:nimi taso2) ", 3. " (:nimi taso3)))))
    tehtavat))

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
         (if-let [emo3 (:id taso3)]
           (let [tehtavat (filter #(= (:emo %) emo3) (get koodit-tasoittain 4))
                 _ (log "tehtävät " (pr-str tehtavat))]
             [grid/grid
              {:otsikko "Tehtävät"
               :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
               :tallenna (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-tehtavat)
                           #(tallenna-tehtavat tehtavat %)
                           :ei-mahdollinen)
               :tunniste :id}

              [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :validoi [[:ei-tyhja "Anna tehtävän nimi"]] :leveys "70%"}
               {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :validoi [[:ei-tyhja "Anna yksikkö"]] :leveys "15%"}
               {:otsikko "Hinnoittelu" :nimi :hinnoittelu :tyyppi :valinta :leveys "15%"
                :valinnat +hinnoittelu-valinnat+
                :valinta-nayta hinnoittelun-nimet
                :fmt #(if % (hinnoittelun-nimet %) "Ei hinnoittelua")}
               {:otsikko "Seurataan API:n kautta" :nimi :api-seuranta :tyyppi :checkbox :leveys "15%" :fmt fmt/totuus
                :tasaa :keskita}]
              (sort-by (juxt :hinnoittelu :nimi) tehtavat)])
           [:div {:class
                  (str "inline-block lomake-vihje")}
            [:div.vihjeen-sisalto
             (harja.ui.ikonit/livicon-info-sign)
             [:span (str " Valitse taso nähdäksesi tehtävät")]]])

         [:br]
         (let [tehtavat (rakenna-tasot kaikki-koodit (filter #(true? (:api-seuranta %)) (get koodit-tasoittain 4)))
               kokonaishintaiset-tehtavat (filter #(some (fn [h] (= h "kokonaishintainen")) (:hinnoittelu %)) tehtavat)
               yksikkohintaiset-tehtavat (filter #(some (fn [h] (= h "yksikkohintainen")) (:hinnoittelu %)) tehtavat)]
           [:div
            [grid/grid
             {:otsikko "API:n kautta seurattavat kokonaishintaiset toteumatehtävät"
              :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
              :piilota-toiminnot? true
              :tunniste :id}

             [{:otsikko "Id" :nimi :id :tyyppi :string :leveys "40"}
              {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%"}
              {:otsikko "Tasot" :nimi :tasot :tyyppi :string :leveys "20%"}
              {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys "10%"}]
             (sort-by :nimi kokonaishintaiset-tehtavat)]
            [:br]
            [grid/grid
             {:otsikko "API:n kautta seurattavat yksikköhintaiset toteumatehtävät"
              :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
              :piilota-toiminnot? true
              :tunniste :id}

             [{:otsikko "Id" :nimi :id :tyyppi :string :leveys "40"}
              {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%"}
              {:otsikko "Tasot" :nimi :tasot :tyyppi :string :leveys "20%"}
              {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys "10%"}]
             (sort-by :nimi yksikkohintaiset-tehtavat)]])
         (let [tehtavat @tyokoneiden-reaaliaikaseuranna-tehtavat]
           [grid/grid
            {:otsikko "API:n kautta seurattavat työkoneiden reaaliaikaseurannan tehtävät"
             :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
             :piilota-toiminnot? true
             :tunniste :id}
            [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%"}]
            (sort-by :nimi tehtavat)])]))

    {:displayName "toimenpidekoodit"
     :component-did-mount
     (fn [this]
       (go (let [toimenpidekoodit (<! (k/get! :hae-toimenpidekoodit))
                 tyokoneiden-reaaliaikaseuranna-tehtavat (<! (k/get! :hae-reaaliaikaseurannan-tehtavat))]
             (resetoi-koodit toimenpidekoodit)
             (resetoi-tyokoneiden-reaaliaikaseuranna-tehtavat tyokoneiden-reaaliaikaseuranna-tehtavat))))}))
