(ns harja.views.urakka.kulut.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [goog.string.format]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kulut :as tiedot]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.views.urakka.kulut.kululomake :as kululomake]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.fmt :as fmt])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defn toimenpide-otsikko
  [auki? toimenpiteet tpi summa erapaiva maksuera]
  [:tr.table-default-strong.klikattava
   {:on-click #(swap! auki? not)}
   [:td.col-xs-1 (str (pvm/pvm erapaiva))]
   [:td.col-xs-1.sailyta-rivilla (str "HA" maksuera)]
   [:td.col-xs-4 (get-in toimenpiteet [tpi :toimenpide])]
   [:td.col-xs-4 
    [:span.col-xs-6  "Yhteensä"]
    [:span.col-xs-6  
     (if @auki? 
       [ikonit/harja-icon-navigation-up]
       [ikonit/harja-icon-navigation-down])]]
   [:td.col-xs-1.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td.col-xs-1 ""]])

(defn koontilasku-otsikko 
  [nro summa]
  [:tr.table-default-thin.valiotsikko.table-default-strong
   [:td {:colSpan "4"}
    (str (if (zero? nro)
           "Kulut ilman koontilaskun nroa"
           (str "Koontilasku nro " nro)) " yhteensä")] 
   [:td.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td ""]])

(defn laskun-erapaiva-otsikko
  [erapaiva]
  [:tr.table-default-thin.valiotsikko.table-default-strong
   [:td {:colSpan "6"} (str erapaiva)]])

(defn kulu-rivi 
  [{:keys [e!]} {:keys [id toimenpide-nimi tehtavaryhma-nimi maksuera liitteet summa erapaiva]}]
  [:tr.klikattava 
   {:on-click (fn [] (e! (tiedot/->AvaaKulu id)))}
   [:td.col-xs-1 (str (when erapaiva (pvm/pvm erapaiva)))]
   [:td.col-xs-1.sailyta-rivilla (str "HA" maksuera)]
   [:td.col-xs-4 toimenpide-nimi]
   [:td.col-xs-4 tehtavaryhma-nimi]
   [:td.col-xs-1.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td.col-xs-1.tasaa-oikealle (when-not (empty? liitteet) [ikonit/harja-icon-action-add-attachment])]])

(defn toimenpide-expandattava
  [_ {:keys [toimenpiteet tehtavaryhmat]}]
  (let [auki? (r/atom false)]
    (fn [[_ tpi summa rivit] {:keys [e!]}]
      (if (> (count rivit) 1)
          [:<>
           [toimenpide-otsikko auki? toimenpiteet tpi summa (-> rivit first :erapaiva) (-> rivit first :maksuera-numero)] 
           (when @auki? 
             (into [:<>] 
                   (loop [[{:keys [id toimenpideinstanssi tehtavaryhma liitteet summa maksuera-numero] :as rivi} & loput] rivit
                          odd? false
                          elementit []]  
                     (if (nil? rivi) 
                       elementit
                       (recur loput
                              (not odd?)
                              ^{:key (gensym "rivi-")} 
                              (conj elementit [kulu-rivi 
                                               {:e! e! :odd? odd?} 
                                               {:toimenpide-nimi (get-in toimenpiteet [toimenpideinstanssi :toimenpide]) 
                                                :tehtavaryhma-nimi (get-in tehtavaryhmat [tehtavaryhma :tehtavaryhma])
                                                :maksuera maksuera-numero
                                                :summa summa
                                                :liitteet liitteet
                                                :erapaiva nil
                                                :id id}]))))))]
        (let [{:keys [id toimenpideinstanssi tehtavaryhma liitteet summa erapaiva maksuera-numero]} (first rivit)] 
          [kulu-rivi 
           {:e! e! :odd? false} 
           {:toimenpide-nimi (get-in toimenpiteet [toimenpideinstanssi :toimenpide]) 
            :tehtavaryhma-nimi (get-in tehtavaryhmat [tehtavaryhma :tehtavaryhma])
            :maksuera maksuera-numero
            :summa summa
            :liitteet liitteet
            :erapaiva erapaiva
            :id id}])))))

(defn taulukko-tehdas
  [{:keys [toimenpiteet tehtavaryhmat tiedot e!]} t]
  (cond 
    (and (vector? t)
         (= (first t) :pvm))
    (let [[_ erapaiva & _loput] t] 
      ^{:key (gensym "erap-")} [laskun-erapaiva-otsikko erapaiva])

    (and (vector? t)
         (= (first t) :laskun-numero))
    (let [[_ nro summa] t]
      ^{:key (gensym "kl-")} [koontilasku-otsikko nro summa])

    (and (vector? t)
         (= (first t) :tpi))
    ^{:key (gensym "tp-")} [toimenpide-expandattava t {:toimenpiteet toimenpiteet 
                                                       :tiedot tiedot
                                                       :tehtavaryhmat tehtavaryhmat 
                                                       :e! e!}]
    :else
    ^{:key (gensym "d-")} [:tr]))

(defn kulutaulukko 
  [{:keys [e! tiedot tehtavaryhmat toimenpiteet haetaan?]}]
  (let [tehtavaryhmat  (reduce #(assoc %1 (:id %2) %2) {} tehtavaryhmat)
        toimenpiteet (reduce #(assoc %1 (:toimenpideinstanssi %2) %2) {} toimenpiteet)]
    [:div.livi-grid 
     [:table.grid
      [:thead
       [:tr
        [:th.col-xs-1 "Pvm"]
        [:th.col-xs-1 "Maksuerä"]
        [:th.col-xs-4 "Toimenpide"]
        [:th.col-xs-4 "Tehtäväryhmä"]
        [:th.col-xs-1.tasaa-oikealle "Määrä"]
        [:th.col-xs-1 ""]]]
      [:tbody
       (cond 
         (and (empty? tiedot)
              (not haetaan?))
         [:tr 
          [:td {:colSpan "6"} "Annetuilla hakuehdoilla ei näytettäviä kuluja"]]

         haetaan?
         [:tr 
          [:td {:colSpan "6"} "Haku käynnissä, odota hetki"]]

         :else
         (into [:<>] (comp (map (r/partial taulukko-tehdas {:toimenpiteet toimenpiteet 
                                                            :tiedot tiedot
                                                            :tehtavaryhmat tehtavaryhmat
                                                            :e! e!}))
                           (keep identity))
               tiedot))]]]))

(defn- kohdistetut*
  [e! _app]
  (komp/luo
   (komp/piirretty (fn [_this]
                     (e! (tiedot/->HaeUrakanToimenpiteet (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])))
                     (e! (tiedot/->HaeUrakanKulut {:id (-> @tila/yleiset :urakka :id)
                                                   :alkupvm (first (pvm/kuukauden-aikavali (pvm/nyt)))
                                                   :loppupvm (second (pvm/kuukauden-aikavali (pvm/nyt)))}))
                     (e! (tiedot/->HaeUrakanValikatselmukset))
                     (e! (tiedot/->HaeUrakanRahavaraukset))))
   (komp/ulos #(e! (tiedot/->NakymastaPoistuttiin)))
   (fn [e! {kulut :kulut syottomoodi :syottomoodi 
            {:keys [haetaan haun-kuukausi haun-alkupvm haun-loppupvm]}
            :parametrit
            tehtavaryhmat :tehtavaryhmat 
            toimenpiteet :toimenpiteet :as app}]
     (let [urakan-alkupvm (-> @tila/yleiset :urakka :alkupvm)
           urakan-loppupvm (-> @tila/yleiset :urakka :loppupvm)
           ;; Varmista, että käsitellään vain valitun urakan ajalta kuluja
           aikaisin-mahdollinen-nyt (if (pvm/sama-tai-jalkeen? (pvm/nyt) urakan-alkupvm)
                                      (pvm/nyt)
                                      urakan-alkupvm)
           ;; Jos haun-kuukausi on defaulteissa asetettu pienemmäksi kuin urakan alkupäivä, niin muuta se
           haun-kuukausi (if (pvm/ennen? (first haun-kuukausi) urakan-alkupvm)
                           (pvm/kuukauden-aikavali urakan-alkupvm)
                           haun-kuukausi)
           [hk-alkupvm hk-loppupvm] (pvm/paivamaaran-hoitokausi (if (:valittu-hoitokausi app)
                                                                  (first (:valittu-hoitokausi app))
                                                                  aikaisin-mahdollinen-nyt))
           kuukaudet (pvm/aikavalin-kuukausivalit
                       [hk-alkupvm
                        hk-loppupvm])
           kuukaudet (conj kuukaudet nil)
           urakan-alkuvuosi (pvm/vuosi urakan-alkupvm)
           urakan-loppuvuosi (pvm/vuosi urakan-loppupvm)
           valittu-hoitokausi (if (nil? (:hoitokauden-alkuvuosi app))
                                (tiedot/kuluva-hoitovuosi aikaisin-mahdollinen-nyt)
                                (:hoitokauden-alkuvuosi app))
           hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
           haun-alkupvm-atom (r/atom (get-in app [:parametrit :haun-alkupvm]))
           haun-loppupvm-atom (r/atom (get-in app [:parametrit :haun-loppupvm]))
           haku-menossa (get-in app [:parametrit :haku-menossa])]
       [:div
        (if syottomoodi
          [:div.kulujen-kirjaus
           [kululomake/kululomake e! app]]
          [:div#vayla.kulujen-listaus.margin-top-16
           [:div.flex-row
            #_[debug/debug app]
            [:h1 "Kulujen kohdistus"]
            ^{:key "raporttixls"}
            [:form {:style {:margin-left "auto"}
                    :target "_blank" :method "POST"
                    :action (k/excel-url :kulut)}
             [:input {:type "hidden" :name "parametrit"
                      :value (t/clj->transit {:urakka-id (-> @tila/yleiset :urakka :id)
                                              :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                              :alkupvm (or (first haun-kuukausi) haun-alkupvm)
                                              :loppupvm (or (second haun-kuukausi) haun-loppupvm)})}]
             [napit/tallenna "Tallenna Excel" (constantly true)
              {:ikoni (ikonit/harja-icon-action-download) :luokka "nappi-toissijainen" :type "submit"
               :esta-prevent-default? true}]]
            ^{:key "raporttipdf"}
            [:form {:style {:margin-left "16px"
                            :margin-right "64px"}
                    :target "_blank" :method "POST"
                    :action (k/pdf-url :kulut)}
             [:input {:type "hidden" :name "parametrit"
                      :value (t/clj->transit {:urakka-id (-> @tila/yleiset :urakka :id)
                                              :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                              :alkupvm (or (first haun-kuukausi) haun-alkupvm)
                                              :loppupvm (or (second haun-kuukausi) haun-loppupvm)})}]
             [napit/tallenna "Tallenna PDF" (constantly true)
              {:ikoni (ikonit/harja-icon-action-download) :luokka "nappi-toissijainen" :type "submit"
               :esta-prevent-default? true}]]

            [napit/yleinen-ensisijainen
             "Uusi kulu"
             #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
             {:ikoni [ikonit/harja-icon-action-add]}]]

           [:div.flex-row {:style {:justify-content "flex-start"}}
            [:div.filtteri.label-ja-alasveto
             [:span.alasvedon-otsikko "Hoitovuosi"]
             [yleiset/livi-pudotusvalikko {:valinta valittu-hoitokausi
                                           :disabled (boolean haku-menossa)
                                           :vayla-tyyli? true
                                           :data-cy "hoitokausi-valinta"
                                           :valitse-fn #(do
                                                          ;; Nullaa mahdollinen aikaväli
                                                          (e! (tiedot/->AsetaHakuPaivamaara nil nil))
                                                          (e! (tiedot/->ValitseHoitokausi %)))
                                           :format-fn #(fmt/hoitokauden-jarjestysluku-ja-vuodet % hoitovuodet "Hoitovuosi")
                                           :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
              hoitovuodet]]
            [valinnat/kuukausi {:nil-valinta "Koko hoitokausi"
                                :vayla-tyyli? true
                                :disabled (boolean haku-menossa)
                                :valitse-fn #(do
                                               (e! (tiedot/->AsetaHakukuukausi %))
                                               (e! (tiedot/->HaeUrakanKulut
                                                     {:id (-> @tila/yleiset :urakka :id)
                                                      :alkupvm (if (nil? %) hk-alkupvm (first %))
                                                      :loppupvm (if (nil? %) hk-loppupvm (second %))})))}

             kuukaudet haun-kuukausi]
            [:span {:class "label-ja-aikavali"}
             (when-not (boolean haku-menossa)
               [:div.label-ja-alasveto.aikavali
                [:span.alasvedon-otsikko (str "Aikaväli")]
                [:div.aikavali-valinnat
                 [kentat/tee-kentta {:tyyppi :pvm
                                     :vayla-tyyli? true
                                     :on-datepicker-select #(do
                                                              (e! (tiedot/->AsetaHakuAlkuPvm %))
                                                              (when (and % @haun-loppupvm-atom)
                                                                (e! (tiedot/->HaeUrakanKulut
                                                                      {:id (-> @tila/yleiset :urakka :id)
                                                                       :alkupvm %
                                                                       :loppupvm @haun-loppupvm-atom}))))}
                  haun-alkupvm-atom]
                 [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
                 [kentat/tee-kentta {:tyyppi :pvm
                                     :vayla-tyyli? true
                                     :on-datepicker-select (fn [loppupvm]
                                                             (do
                                                               (e! (tiedot/->AsetaHakuLoppuPvm loppupvm))
                                                               (when (and (not (nil? loppupvm)) (not (nil? @haun-alkupvm-atom)))
                                                                 (e! (tiedot/->HaeUrakanKulut
                                                                       {:id (-> @tila/yleiset :urakka :id)
                                                                        :alkupvm @haun-alkupvm-atom
                                                                        :loppupvm loppupvm})))))}
                  haun-loppupvm-atom]]])]]
           (when kulut
             [:div
              (if (get-in app [:parametrit :haku-menossa])
                [yleiset/ajax-loader "Ladataan..."]
                [kulutaulukko {:e! e! :haetaan? (> haetaan 0)
                               :tiedot kulut :tehtavaryhmat tehtavaryhmat
                               :toimenpiteet toimenpiteet}])])])]))))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])

