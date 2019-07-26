(ns harja.views.urakka.suunnittelu.kustannussuunnitelma
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]))

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(def talvikausi [10 11 12 1 2 3 4])
(def kesakausi (into [] (range 5 10)))
(def hoitokausi (concat talvikausi kesakausi))

(def kaudet {:kesa kesakausi
             :talvi talvikausi
             :kaikki hoitokausi})

(defn tila
  [a & [kausi]]
  (assoc {} a
            (into {:auki? false}
                  (map #(assoc {} % 0) (get kaudet kausi talvikausi)))))

(defn tila-vuodet
  [a & [kausi]]
  (log "a " a " kausi " kausi)
  (assoc {} a (into {:lahetyspaiva :kuukauden-15
                     :maksukausi   (or kausi :talvi)}
                    (map #(tila % kausi) (range 1 6)))))

(defn tilat-proto
  [& [kausi]]
  (into {}
        (map #(tila-vuodet % kausi)
             toimenpiteet)))

(defn f
  [data & [not?]]
  (into {}
        (map (fn [a]
               {(first a)
                (if (map? (second a))
                  (into {}
                      (filter (fn [b]
                                (if not?
                                  (not (keyword? (first b)))
                                  (keyword? (first b))))
                              (second a)))
                  (second a))})
             data)))

(defonce mastertila
         (r/atom
           {:hankintakustannukset []
            :suunnitellut-hankinnat (tilat-proto)}))

(defrecord AsetaKustannussuunnitelmassa [polku arvo])
(defrecord AsetaMaksukausi [polku arvo])

(extend-protocol tuck/Event
  AsetaMaksukausi
  (process-event
    [{:keys [polku arvo]} app]
    (let [uudet (f (get (tilat-proto arvo) (second polku)) true)
          vanhat (f (get-in app [:suunnitellut-hankinnat (second polku)]))
          mergattu (merge vanhat uudet)]
      (log polku arvo uudet vanhat)
      (assoc-in (assoc-in app (take 2 polku) mergattu) polku arvo)))
  AsetaKustannussuunnitelmassa
  (process-event
    [{:keys [polku arvo]} app]
    (assoc-in app polku arvo)))

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

(def maksukaudet (into #{} (keys kaudet)))
(def lahetyspaivat #{:kuukauden-15})

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
            (for [t toimenpiteet]
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

(defn suunnitellut-hankinnat
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
                                        (->AsetaKustannussuunnitelmassa [:suunnitellut-hankinnat @valittu-toimenpide mode] val))
                          :maksukausi (e!
                                        (->AsetaMaksukausi [:suunnitellut-hankinnat @valittu-toimenpide mode] val))
                          :toimenpide (reset! valittu-toimenpide val)))]]
       (for [[vuosi {:keys [auki?] :as kamat}]
             (filter (fn [[k v]] (number? k)) (@valittu-toimenpide suun-hank))]
              [:div.kustannussuunnitelma.hankinnat-kontti
               [:div.kustannussuunnitelma.hankinnat-label
                {:on-click #(e! (->AsetaKustannussuunnitelmassa [:suunnitellut-hankinnat @valittu-toimenpide vuosi :auki?] (not auki?)))}
                [:span (str vuosi ". hoitovuosi ")]
                [:span (str (apply + (filter number? (vals kamat))) "€")]
                [:span.livicon-chevron.livicon-chevron-down]]
              (if auki?
                (for [kk (get kaudet (:maksukausi (@valittu-toimenpide suun-hank)))]
                  (when (number? kk)
                    [:div.kustannussuunnitelma.hankinnat-rivi
                     [:span (str "15." kk ". ")]
                     [:input {:value     (get kamat kk)
                              ;:on-blur #(swap! tilat assoc-in [@valittu-toimenpide vuosi kk] (-> % .-target .-value js/parseFloat))
                              ;:on-change #(swap! tilat assoc-in [@valittu-toimenpide vuosi kk] (-> % .-target .-value))
                              :on-blur   #(e! (->AsetaKustannussuunnitelmassa
                                                [:suunnitellut-hankinnat @valittu-toimenpide vuosi kk]
                                                (-> % .-target .-value js/parseFloat)))
                              :on-change #(e! (->AsetaKustannussuunnitelmassa
                                                [:suunnitellut-hankinnat @valittu-toimenpide vuosi kk]
                                                (-> % .-target .-value)))
                              }]
                     [:span (str "Kopioi muille kuukausille")]]))
                auki?)])
       [:div (str "Suun hank " suun-hank)]])))

(defn suunnitelmien-tila
  [app]
  (let [avattu? (r/atom (into {}
                              (map #(assoc {} % false)
                                      toimenpiteet)))]
    (fn [app]
      [:div (for [[avain auki?] @avattu?]
              [:div {:on-click #(swap! avattu? assoc avain (not auki?))} (str avain auki?)])])))

(defn kustannussuunnitelma
  [e! app urakka]
  [:div.kustannussuunnitelma
   [:h1 "Kustannussuunnitelma"]
   [:div "Kun kaikki määrät on syötetty, voit seurata kustannuksia. Sampoa varten muodostetaan automaattisesti maksusuunnitelma, jotka löydät Laskutus-osiosta. Kustannussuunnitelmaa tarkennetaan joka hoitovuoden alussa."]
   [hintalaskuri {:otsikko "Tavoitehinta"
                  :selite "Tykkään puurosta"
                  :vuodet [1 2 3 4 5]}]
   [hintalaskuri {:otsikko "Kattohinta"
                  :selite "Tykkään puurosta * 1.1"
                  :vuodet [1 2 3 4 5]}]
   [suunnitelmien-tila]
   [suunnitellut-hankinnat e! app]])
