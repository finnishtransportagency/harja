(ns harja.views.urakka.suunnittelu.kustannussuunnitelma
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]))

(defn haitari-laatikko [_ {:keys [alussa-auki? aukaise-fn]} & _]
  (let [auki? (atom alussa-auki?)
        aukaise-fn! (comp (or aukaise-fn identity)
                         (fn [event]
                           (.preventDefault event)
                           (js/console.log (.. event -target -attributes))
                           (let [auki-nyt? (= "true" (.-value (.getNamedItem (.. event -target -attributes) "data-auki")))]
                             (reset! auki? (not auki-nyt?))
                             auki-nyt?)))]
    (fn [otsikko _ & sisalto]
      [:div.haitari-laatikko
       [:span.klikattava {:on-click aukaise-fn!
               :data-auki @auki?} otsikko
        (if @auki?
          ^{:key "haitari-auki"}
          [ikonit/livicon-chevron-up]
          ^{:key "haitari-kiinni"}
          [ikonit/livicon-chevron-down])]
       (when @auki?
         (doall (map-indexed (fn [index komponentti]
                               ^{:key index}
                               [komponentti])
                             sisalto)))])))

(defn hintalaskuri-vuosi
  []
  [:div
   [:div "Vuosi 2"]
   [:div "0.0e"]])

(defn hintalaskuri
  [{:keys [otsikko selite vuodet]}]
  [:div
   [:h5 otsikko]
   [:div selite]
   [:div.hintalaskuri-vuodet
    (for [vuosi vuodet]
      [hintalaskuri-vuosi])]])

(def maksukaudet (into #{} (keys t/kaudet)))
(def lahetyspaivat #{:kuukauden-15})

(defn kuluva-hoitovuosi []
  (let [hoitovuoden-pvmt (pvm/paivamaaran-hoitokausi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        kuluva-urakan-vuosi (inc (- urakan-aloitusvuosi (pvm/vuosi (first hoitovuoden-pvmt))))]
    (fn []
      [:div#kuluva-hoitovuosi
       [:span
        (str "Kuluva hoitovuosi: " kuluva-urakan-vuosi
             ". (" (pvm/pvm (first hoitovuoden-pvmt))
             " - " (pvm/pvm (second hoitovuoden-pvmt)) ")")]
       [:div.hoitovuosi-napit
        [napit/yleinen-ensisijainen "Laskutus" #(println "Painettiin Laskutus") {:ikoni [ikonit/euro] :disabled true}]
        [napit/yleinen-ensisijainen "Kustannusten seuranta" #(println "Painettiin Kustannusten seuranta") {:ikoni [ikonit/stats] :disabled true}]]])))

(defn tavoite-ja-kattohinta []
  [:div
   [hintalaskuri {:otsikko "Tavoitehinta"
                  :selite "Tykkään puurosta"
                  :vuodet [1 2 3 4 5]}]
   [hintalaskuri {:otsikko "Kattohinta"
                  :selite "Tykkään puurosta * 1.1"
                  :vuodet [1 2 3 4 5]}]])

(defn suunnitelmien-tila
  []
  (let [avattu? (r/atom (into {}
                              (map #(assoc {} % false)
                                   t/toimenpiteet)))]
    (fn [app]
      [:div (for [[avain auki?] @avattu?]
              [:div {:on-click #(swap! avattu? assoc avain (not auki?))} (str avain auki?)])])))

(defn hankinnat-header
  [_ _ _]
  (let [auki? (r/atom nil)
        sulje-alasveto-ja (fn [funk & args]
                            (apply funk args)
                            (reset! auki? nil))]
    (fn [toimenpide lahetyspaiva maksukausi valitse]
      (log toimenpide lahetyspaiva maksukausi)
      [:div.hankinnat-header
       [:div
        [:label "Toimenpide"]
        [:div
         {:on-click #(reset! auki? :toimenpide)}
         toimenpide]
        (when (= :toimenpide @auki?)
          [:div.dropdown.livi-alasveto
           (for [t t/toimenpiteet]
             [:div
              {:on-click #(sulje-alasveto-ja valitse :toimenpide t)}
              (str t)])])]
       [:div
        [:label "Lähetyspäivä"]
        [:div
         {:on-click #(reset! auki? :lahetyspaiva)}
         lahetyspaiva]
        (when (= :lahetyspaiva @auki?)
          [:div.dropdown.livi-alasveto
           (for [l lahetyspaivat]
             [:div
              {:on-click #(sulje-alasveto-ja valitse :lahetyspaiva l)}
              (str l)])])]
       [:div
        [:label "Maksetaan"]
        [:div
         {:on-click #(reset! auki? :maksetaan)}
         maksukausi]
        (when (= :maksetaan @auki?)
          [:div.dropdown.livi-alasveto
           (for [k maksukaudet]
             [:div
              {:on-click #(sulje-alasveto-ja valitse :maksukausi k)}
              (str k)])])]])))

(defn suunnitellut-hankinnat-ja-rahavaraukset
  [_ _]
  (let [valittu-toimenpide (r/atom :talvihoito)]
    (fn [e! {suun-hank :suunnitellut-hankinnat}]
      ;; toimenpidevalinta - lähetyspäivävalinta - maksetaanvalinta
      [:div.suunnitellut-hankinnat
       [:div.suunnitellut-hankinnat-header
        [hankinnat-header
         @valittu-toimenpide
         (get-in suun-hank [@valittu-toimenpide :lahetyspaiva])
         (get-in suun-hank [@valittu-toimenpide :maksukausi])
         (fn [mode val] (case mode
                          :lahetyspaiva (e!
                                          (t/->AsetaKustannussuunnitelmassa [:suunnitellut-hankinnat @valittu-toimenpide mode] val))
                          :maksukausi (e!
                                        (t/->AsetaMaksukausi [:suunnitellut-hankinnat @valittu-toimenpide mode] val))
                          :toimenpide (reset! valittu-toimenpide val)))]]
       (for [[vuosi {:keys [auki?] :as kamat}]
             (filter (fn [[k v]] (number? k)) (@valittu-toimenpide suun-hank))]
         [:div.kustannussuunnitelma.hankinnat-kontti
          [:div.kustannussuunnitelma.hankinnat-label
           {:on-click #(e! (t/->AsetaKustannussuunnitelmassa [:suunnitellut-hankinnat @valittu-toimenpide vuosi :auki?] (not auki?)))}
           [:span (str vuosi ". hoitovuosi ")]
           [:span (str (apply + (filter number? (vals kamat))) "€")]
           [:span.livicon-chevron.livicon-chevron-down]]
          (if auki?
            (for [kk (get t/kaudet (:maksukausi (@valittu-toimenpide suun-hank)))]
              (when (number? kk)
                [:div.kustannussuunnitelma.hankinnat-rivi
                 [:span (str "15." kk ". ")]
                 [:input {:value     (get kamat kk)
                          ;:on-blur #(swap! tilat assoc-in [@valittu-toimenpide vuosi kk] (-> % .-target .-value js/parseFloat))
                          ;:on-change #(swap! tilat assoc-in [@valittu-toimenpide vuosi kk] (-> % .-target .-value))
                          :on-blur   #(e! (t/->AsetaKustannussuunnitelmassa
                                            [:suunnitellut-hankinnat @valittu-toimenpide vuosi kk]
                                            (-> % .-target .-value js/parseFloat)))
                          :on-change #(e! (t/->AsetaKustannussuunnitelmassa
                                            [:suunnitellut-hankinnat @valittu-toimenpide vuosi kk]
                                            (-> % .-target .-value)))
                          }]
                 [:span (str "Kopioi muille kuukausille")]]))
            auki?)])
       [:div (str "Suun hank " suun-hank)]])))

(defn hankintakustannukset [e! app]
  [:div
   [:span "---- TODO Hankintakustannukset ----"]
   [suunnitellut-hankinnat-ja-rahavaraukset e! app]])

(defn erillishankinnat []
  [:span "---- TODO erillishankinnat ----"])

(defn johto-ja-hallintokorvaus []
  [:span "---- TODO johto- ja hallintokorvaus ----"])

(defn hoidonjohtopalkkio []
  [:span "---- TODO hoidonjohtopalkkio ----"])

(defn hallinnolliset-toimenpiteet []
  [:div
   [hintalaskuri {:otsikko "Yhteenveto"
                  :selite "Tykkään puurosta"
                  :vuodet [1 2 3 4 5]}]
   [erillishankinnat]
   [johto-ja-hallintokorvaus]
   [hoidonjohtopalkkio]])

(defn kustannussuunnitelma*
  [e! app]
  [:div.kustannussuunnitelma
   [:h1 "Kustannussuunnitelma"]
   [:div "Kun kaikki määrät on syötetty, voit seurata kustannuksia. Sampoa varten muodostetaan automaattisesti maksusuunnitelma, jotka löydät Laskutus-osiosta. Kustannussuunnitelmaa tarkennetaan joka hoitovuoden alussa."]
   [kuluva-hoitovuosi]
   [haitari-laatikko
    "Tavoite- ja kattohinta lasketaan automaattisesti"
    {:alussa-auki? true}
    tavoite-ja-kattohinta]
   [haitari-laatikko
    "Suunnitelmien tila"
    {:alussa-auki? true}
    suunnitelmien-tila]
   [hankintakustannukset e! app]
   [hallinnolliset-toimenpiteet]])

(defn kustannussuunnitelma []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])
