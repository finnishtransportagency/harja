(ns harja.views.toimenpidekoodit
  "Toimenpidekoodien ylläpitonäkymä"
  (:require [reagent.core :refer [atom wrap] :as reagent]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.tiedot.toimenpidekoodit :refer
             [koodit
              koodit-tasoittain
              tyokoneiden-reaaliaikaseuranna-tehtavat
              tehtavaryhmat]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.ui.kentat :refer [tee-kentta]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

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

(defonce nayta-poistetut? (atom false))

;; Hinnoitteluvalinta, nil näyttää kaikki
(defonce nayta-hinnoittelu (atom nil))

(defonce tehtavat
  (reaction
   (let [emo3 (:id @valittu-taso3)
         koodit-tasoittain @koodit-tasoittain
         nayta-poistetut? @nayta-poistetut?
         nayta-hinnoittelu @nayta-hinnoittelu]
     (or
      (and emo3
           (sort-by (juxt :hinnoittelu :nimi)
                    (into []
                          (comp
                           (filter (fn [tpk]
                                     (and (= (:emo tpk) emo3)
                                          (or nayta-poistetut?
                                              (not (:poistettu tpk)))
                                          (or (nil? nayta-hinnoittelu)
                                              (some #(= nayta-hinnoittelu %)
                                                    (:hinnoittelu tpk))))))
                           (map #(assoc %
                                        :passivoitu? (:poistettu %)
                                        :poistettu nil)))
                          (get koodit-tasoittain 4))))
      []))))

(defn resetoi-tehtavaryhmat [ryhmat]
      (reset! tehtavaryhmat ryhmat))

(defn resetoi-tyokoneiden-reaaliaikaseuranna-tehtavat [tehtavat]
  (reset! tyokoneiden-reaaliaikaseuranna-tehtavat tehtavat))

(defn tallenna-tehtavat [tehtavat uudet-tehtavat]
  (go (let [lisattavat
            (mapv #(assoc % :emo (:id @valittu-taso3))
                  (into []
                        (comp (filter
                                #(and
                                  (not (:passivoitu? %))
                                  (< (:id %) 0))))
                        uudet-tehtavat))
            muokattavat (into []
                              (filter (fn [t]
                                        ;; vain muuttuneet "vanhat" rivit
                                        (not= true (:koskematon t)))
                                      (into []
                                            (comp (filter
                                                    #(> (:id %) 0)))
                                            uudet-tehtavat)))
            res (<! (k/post! :tallenna-tehtavat
                             {:lisattavat lisattavat
                              :muokattavat muokattavat}))]
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
  (str/join ", " (map #(hinnoittelun-nimi %) hinnoittelu-vec)))

(def +hinnoittelu-valinnat+
  [["yksikkohintainen"]
   ["kokonaishintainen"]
   ["muutoshintainen"]
   ["yksikkohintainen" "muutoshintainen"]
   ["yksikkohintainen" "kokonaishintainen"]
   ["kokonaishintainen" "muutoshintainen"]
   ["kokonaishintainen" "yksikkohintainen" "muutoshintainen"]])

(def vuosi-valinnat
       (range 2020 2034 1))

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

(defn api-seuranta [kaikki-koodit koodit-tasoittain]
  (let [auki (atom #{})]
    (fn [kaikki-koodit koodit-tasoittain]
      (let [tehtavat (rakenna-tasot kaikki-koodit (filter #(and (true? (:api-seuranta %))
                                                                (not= "Ei yksilöity" (:nimi %)))
                                                          (get koodit-tasoittain 4)))

            kokonaishintaiset-tehtavat (filter #(some (fn [h] (= h "kokonaishintainen")) (:hinnoittelu %)) tehtavat)
            yksikkohintaiset-tehtavat (filter #(some (fn [h] (= h "yksikkohintainen")) (:hinnoittelu %)) tehtavat)
            auki-nyt @auki]
        [yleiset/haitari
         (wrap
          (into {}
                (map (juxt :id #(assoc %
                                       :auki (boolean (auki-nyt (:id %))))))

                [{:otsikko "API: Kokonaishintaiset toteumatehtävät"
                  :id :api-kokonaishintaiset
                  :sisalto [grid/grid
                            {:otsikko "API:n kautta seurattavat kokonaishintaiset toteumatehtävät"
                             :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
                             :piilota-toiminnot? true
                             :tunniste #(str "kht" (:id %))}

                            [{:otsikko "Id" :nimi :id :tyyppi :string :leveys "40"}
                             {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%"}
                             {:otsikko "Tasot" :nimi :tasot :tyyppi :string :leveys "20%"}
                             {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys "10%"}]
                            (sort-by (juxt :tasot :nimi) kokonaishintaiset-tehtavat)]}

                 {:otsikko "API: Yksikköhintaiset toteumatehtävät"
                  :id :api-yksikkohintaiset
                  :sisalto [grid/grid
                            {:otsikko "API:n kautta seurattavat yksikköhintaiset toteumatehtävät"
                             :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
                             :piilota-toiminnot? true
                             :tunniste #(str "yht" (:id %))}

                            [{:otsikko "Id" :nimi :id :tyyppi :string :leveys "40"}
                             {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%"}
                             {:otsikko "Tasot" :nimi :tasot :tyyppi :string :leveys "20%"}
                             {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys "10%"}]
                            (sort-by (juxt :tasot :nimi) yksikkohintaiset-tehtavat)]}

                 {:otsikko "API: Työkoneiden reaaliaikaseuranta"
                  :id :api-reaaliaika
                  :sisalto (let [tehtavat @tyokoneiden-reaaliaikaseuranna-tehtavat]
                             [grid/grid
                              {:otsikko "API:n kautta seurattavat työkoneiden reaaliaikaseurannan tehtävät"
                               :tyhja (if (nil? tehtavat) [yleiset/ajax-loader "Tehtäviä haetaan..."] "Ei tehtävätietoja")
                               :piilota-toiminnot? true
                               :tunniste #(str "ras" (:nimi %))}
                              [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%"}]
                              (sort-by :nimi tehtavat)])}])
          (fn [rivit]
            (reset! auki (into #{}
                               (comp (filter :auki)
                                     (map :id))
                               (vals rivit)))))
         {:luokka "haitari-levea"}]))))

(def toimenpidekoodit
  "Toimenpidekoodien hallinnan pääkomponentti"
  (with-meta
    (fn []
      (let [kaikki-koodit @koodit
            koodit-tasoittain @koodit-tasoittain
            taso1 @valittu-taso1
            taso2 @valittu-taso2
            taso3 @valittu-taso3
            valinnan-koodi #(get kaikki-koodit (-> % .-target .-value js/parseInt))
            tehtavaryhmat-jarjestyksessa (sort-by :jarjestys @tehtavaryhmat)]

        [:div.container-fluid.toimenpidekoodit
         [:h3 "Tehtävien hallinta"]
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
          [:select#taso2 {:on-change #(let [taso2 (valinnan-koodi %)]
                                        (reset! valittu-taso2 taso2)
                                        (reset! valittu-taso3
                                                (first
                                                 (filter (fn [k]
                                                           (and (= "Laaja toimenpide" (:nimi k))
                                                                (= (:id taso2) (:emo k))))
                                                         (get koodit-tasoittain 3)))))
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

         [tee-kentta {:teksti "Näytä myös passivoidut"
                      :tyyppi :checkbox
                      :lomake? true}
          nayta-poistetut?]

         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Näytä hinnoittelutyyppi"]
          [yleiset/livi-pudotusvalikko {:valinta @nayta-hinnoittelu
                                        :format-fn #(if % (hinnoittelun-nimi %) "Kaikki")
                                        :valitse-fn #(reset! nayta-hinnoittelu %)}
           [nil "kokonaishintainen" "yksikkohintainen" "muutoshintainen"]]]

         [:br]
         (let [emo3 (:id taso3)

               _ (log "tehtävät " (pr-str tehtavat))]
           [grid/grid
            {:otsikko "Tehtävät"
             :tyhja (cond
                      (nil? (:id taso3))
                      [yleiset/vihje "Valitse taso nähdäksesi tehtävät"]

                      (nil? tehtavat)
                      [yleiset/ajax-loader "Tehtäviä haetaan..."]

                      :default "Ei tehtävätietoja")
             :tallenna (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-tehtavat)
                         #(tallenna-tehtavat tehtavat %)
                         :ei-mahdollinen)
             :tunniste :id
             :rivin-luokka #(when (:passivoitu? %)
                              "tehtava-poistettu")
             :piilota-toiminnot? true}

            [{:otsikko "Nimi" :nimi :nimi :tyyppi :string
              :validoi [[:ei-tyhja "Anna tehtävän nimi"]]
              :leveys 8}
             {:otsikko "Voimassaolo alkaa" :nimi :voimassaolon-alkuvuosi :tyyppi :valinta :leveys 2
              :valinnat vuosi-valinnat}
             {:otsikko "Voimassaolo päättyy" :nimi :voimassaolon-loppuvuosi :tyyppi :valinta :leveys 2
              :valinnat vuosi-valinnat}
             {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :validoi [[:ei-tyhja "Anna yksikkö"]]
              :leveys 2}
             {:otsikko "Hinnoittelu" :nimi :hinnoittelu :tyyppi :valinta :leveys 2
              :valinnat +hinnoittelu-valinnat+
              :valinta-nayta hinnoittelun-nimet
              :fmt #(if % (hinnoittelun-nimet %) "Ei hinnoittelua")}
             {:otsikko "Seurataan API:n kautta" :nimi :api-seuranta :tyyppi :checkbox
              :leveys 2
              :fmt fmt/totuus
              :tasaa :keskita
              ;; todo: jos muutetaan arvo esim. muutoshintaiseksi, pitää arvo asettaa nilliksi
              :muokattava? (fn [rivi _]
                             (some (fn [h] (or (= h "kokonaishintainen")
                                               (= h "yksikkohintainen")))
                                   (:hinnoittelu rivi)))}
             {:otsikko "Passivoitu"
              :nimi :passivoitu?
              :tyyppi :checkbox
              :tasaa :keskita
              :fmt fmt/totuus
              :leveys 2}
             {:otsikko "Tehtäväryhmä"
              :nimi :tehtavaryhma
              :tyyppi :valinta
              :valinnat tehtavaryhmat-jarjestyksessa
              :valinta-nayta :nimi
              :leveys 3}
             {:otsikko "Luoja"
              :nimi :luoja
              :tyyppi :string
              :leveys 2
              :muokattava? (constantly false)
              :fmt fmt/kayttaja-opt}]
            @tehtavat])

         [api-seuranta kaikki-koodit koodit-tasoittain]]))

    {:displayName "toimenpidekoodit"
     :component-did-mount
                  (fn [this]
                      (go (let [toimenpidekoodit (<! (k/get! :hae-toimenpidekoodit))
                                tyokoneiden-reaaliaikaseuranna-tehtavat (<! (k/get! :hae-reaaliaikaseurannan-tehtavat))
                                tehtavaryhmat (<! (k/get! :hae-tehtavaryhmat))]
                               (resetoi-koodit toimenpidekoodit)
                               (resetoi-tyokoneiden-reaaliaikaseuranna-tehtavat tyokoneiden-reaaliaikaseuranna-tehtavat)
                               (resetoi-tehtavaryhmat tehtavaryhmat))))}))
