(ns harja.views.tierekisteri
  "Tierekisterin tarkastelunäkymä. Lähinnä debug käyttöön."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tyokalut.vkm :as vkm]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as tasot]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.tierekisteri :as tr]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.tiedot.kartta :as tiedot-kartta]
            [clojure.walk :as walk])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tr (r/atom {}))
(defonce sijainti (atom nil))

(defonce kannan-geometria (atom nil))

(defonce koordinaatti (r/atom {:x nil :y nil}))
(defonce koordinaatti2 (r/atom {:x nil :y nil}))
(defonce koordinaatin-osoite (r/atom nil))

(defonce valitse-kartalla? (r/atom false))
(defonce valittu-osoite (r/atom nil))

(defonce tarkasteltava-asia (atom {:tyyppi :tarkastusajo
                                   :id nil
                                   :reitti nil
                                   :pisteet nil}))
(defonce rajoitusalue-urakka (r/atom nil))


(defn hae! []
  (tasot/poista-geometria! :tierekisteri-haettu-osoite)
  (go
    (let [tulos (<! (vkm/tieosoite->viiva @tr))]
      (reset! sijainti tulos)
      (tasot/nayta-geometria! :tierekisteri-haettu-osoite
                              {:alue (maarittele-feature
                                      (first tulos)
                                      false
                                      asioiden-ulkoasu/tr-ikoni
                                      asioiden-ulkoasu/tr-viiva)}))))

(defn kaanna! []
  (swap! tr
         (fn [{:keys [alkuosa alkuetaisyys loppuosa loppuetaisyys] :as tr}]
           (assoc tr
                  :alkuosa loppuosa
                  :alkuetaisyys loppuetaisyys
                  :loppuosa alkuosa
                  :loppuetaisyys alkuetaisyys)))
  (hae!))

(defn ajoratojen-geometriat! []
  (tasot/poista-geometria! :ajoratojen-geometriat)
  (go
    (let [tulos (<! (vkm/tieosan-ajoratojen-geometriat @tr))]
      (reset! sijainti tulos)
      (tasot/nayta-geometria! :ajoratojen-geometriat
                              {:alue (maarittele-feature
                                       (first tulos)
                                       false
                                       asioiden-ulkoasu/tr-ikoni
                                       asioiden-ulkoasu/tr-viiva)}))))

(def tr-kentat [["Tie" :numero]
                ["Aosa" :alkuosa]
                ["Aet" :alkuetaisyys]
                ["Losa" :loppuosa]
                ["Let" :loppuetaisyys]
                ["Ajorata" :ajorata]])

(defn hae-tr-osoite! []
  ;; Hae TR osoite koordinaatille
  (tasot/poista-geometria! :tierekisteri-haettu-koordinaatti)
  (reset! koordinaatin-osoite nil)
  (go
    (let [{:keys [x y]} @koordinaatti
          tulos (<! (vkm/koordinaatti->trosoite [x y]))]
      (when-let [g (:geometria tulos)]
        (tasot/nayta-geometria! :tierekisteri-haettu-osoite
                                {:alue (assoc g
                                              :type :circle
                                              :radius 50)}))
      (reset! koordinaatin-osoite tulos))))

(defn- poista-vanhat-reitit-ja-pisteet
  []
  (let [{:keys [reitti pisteet]} @tarkasteltava-asia]
    (when reitti
      (tasot/poista-geometria! :tarkasteltava-reitti))
    (doseq [piste pisteet]
      (tasot/poista-geometria! (:id piste)))))

(def reittipisteen-radius 300) ;; 50 Jos tarpeen nähdä suurella tarkkuudella
(def reittipisteen-stroke-vari "red") ;; "black" jos tarpeen nähdä yksittäiset pallot

(defn- piirra-reittipisteet
  [pisteet]
  (let [tyyppi (:tyyppi @tarkasteltava-asia)]
    (doseq [piste pisteet]
      (tasot/nayta-geometria! (:id piste)
                              {:nimi (when-let [aika (:aika piste)]
                                       (pvm/pvm-aika-sek aika))
                               :type :reittipisteet
                               :alue (assoc (:sijainti piste)
                                            :fill "red"
                                            :radius reittipisteen-radius
                                            :stroke {:color reittipisteen-stroke-vari
                                                     :width 3})}))))

(defn- piirra-reitti
  [reitti]
  (tasot/nayta-geometria! :tarkasteltava-reitti
    {:alue (assoc reitti
             :fill "black"
             :radius 40
             :stroke {:color "black"
                      :width 4})}))

(defn hae-ja-nayta-reittipisteet []
  (let [{:keys [tyyppi id]} @tarkasteltava-asia]
    (case tyyppi
      :tyokonehavainto
      (go
        (let [reitti (<!  (k/post! :debug-hae-tyokonehavainto-reittipisteet {:tyokone-id id}))
              alue (harja.geo/extent reitti)]
          (swap! tarkasteltava-asia assoc :reitti reitti)
          (piirra-reitti reitti)
          (js/setTimeout #(kartta-tiedot/keskita-kartta-alueeseen! alue) 200)))

      :tarkastusajo
      (go
        (let [pisteet (<!  (k/post! :hae-tarkastusajon-reittipisteet
                                    {:tarkastusajon-id id}))]
          (swap! tarkasteltava-asia assoc :pisteet pisteet)
          (piirra-reittipisteet pisteet)))

      :toteuma
      (go
        (let [{:keys [reitti reittipisteet]}
              (<! (k/post! :debug-hae-toteuman-reitti-ja-pisteet id))]
          (swap! tarkasteltava-asia assoc
                 :reitti reitti
                 :pisteet reittipisteet)
          (piirra-reitti reitti)
          (piirra-reittipisteet reittipisteet))))))

(defn tr-haku []
  [:div.tierekisteri-tr-haku
   [:table
    [:thead
     [:tr
      (for [[nimi _] tr-kentat]
        ^{:key nimi}
        [:th nimi])]]
    [:tbody
     [:tr
      (doall
       (for [[_ key] tr-kentat]
         ^{:key key}
         [:td [:input {:type "text" :on-change #(swap! tr assoc key (-> % .-target .-value))
                       :value (get @tr key)}]]))]]]

   [:div
    [:button {:on-click hae!} "Hae"]
    [:button {:on-click kaanna!} "Käännä alku/loppu"]
    [:button {:on-click ajoratojen-geometriat!} "Piirrä ajoradat"]
    [yleiset/vihje "Ajorata vaikuttaa vain ajoradan geometrian piirtämiseen. Ajoradan piirrossa huomioidaan tie, aosa ja ajorata (jos jätät ajoradan tyhjäksi, piirretään kaikkien ajoratojen geometriat ko. tiellä ja osalla."]
    ;; Toistaiseksi piilotettu, koska ei toimi nykyisellään.
    #_[:button {:on-click hae-vkm!} "Hae VKM polut"]]])

(defn koordinaatti-haku []
  [:div.tierekisteri-koordinaatti-haku
   [:table
    [:thead [:tr [:th "X"] [:th "Y"]]]
    [:tbody
     [:tr
      [:td
       [:input {:type :text
                :value (:x @koordinaatti)
                :on-change #(swap! koordinaatti assoc :x (-> % .-target .-value js/parseFloat))}]]
      [:td
       [:input {:type :text
                :value (:y @koordinaatti)
                :on-change #(swap! koordinaatti assoc :y (-> % .-target .-value js/parseFloat))}]]

      [:td
       [:button {:on-click hae-tr-osoite!} "Hae TR-osoite"]]]]]
   (when-let [osoite @koordinaatin-osoite]
     [:div (pr-str osoite)])])


(defn reittipisteiden-haku
  "Tarjoaa kälin jolla haetaan reitillisen asian pisteet id:llä"
  []
  (let [tyyppi (:tyyppi @tarkasteltava-asia)]
    [:div.tierekisteri-tarkastusajon-id
     [yleiset/pudotusvalikko "Hae" {:valinta tyyppi :format-fn name
                                    :valitse-fn #(swap! tarkasteltava-asia assoc :tyyppi %)}
      [:tyokonehavainto
       :tarkastusajo
       :toteuma]


      ]
     [:h5 "Hae " (case tyyppi
                   :tyokonehavainto "työkonehavainto reittipisteet"
                   :tarkastusajo "tarkastusajon reittipisteet"
                   :toteuma "toteuman reitti ja reittipisteet")]
     (case tyyppi
       :tyokonehavainto [:label  "Työkone id: "]
       :tarkastusajo [:label  "Tarkastusajo id: "]
       :toteuma [:label  "Toteuma id: "])

     [:input {:type :text
              :placeholder "tietokannassa"
              :on-change #(do
                            (poista-vanhat-reitit-ja-pisteet)
                            (swap! tarkasteltava-asia assoc
                                   :id (-> % .-target .-value js/parseInt)
                                   :reitti nil
                                   :pisteet nil))}]
     [:button.nappi-ensisijainen.btn-xs {:on-click hae-ja-nayta-reittipisteet
                                         :disabled (when (nil? (:id @tarkasteltava-asia)) "disabled")}
      "Piirrä reitti"]]))

(defn geometrian-piirto
  "Tarjoaa kälin jolla haetaan reitillisen asian pisteet id:llä"
  []
  (let []
    [:div.geometrian-piirto
     [:h5 "Anna geometria: "]

     [:textarea {:rows 10
                 :cols 80
                 :placeholder "Liitä ST_AsGeoJSON funkkarilla saatu geometria tähän. Toimii vain Multiline- ja LineStringien kanssa tällä hetkellä."
                 :value @kannan-geometria
                 :on-change #(let [v (-> % .-target .-value)]
                               (reset! kannan-geometria v))}]
     [:button.nappi-ensisijainen.btn-xs {:on-click
                                         #(do
                                            (let [annettu-geo (js->clj (js/JSON.parse @kannan-geometria))
                                                  type (get annettu-geo "type")
                                                  coordinates (get annettu-geo "coordinates")
                                                  geo {:type (cond (= "MultiLineString" type) :multiline
                                                                  (= "LineString" type) :line
                                                                  :else :multiline)}
                                                  geo (merge geo
                                                       (cond (= "MultiLineString" type)
                                                             {:lines (map (fn [c]
                                                                            {:type :line,
                                                                             :points c}) coordinates)}

                                                             (= "LineString" type)
                                                             {:points coordinates}))]
                                              (piirra-reitti geo)))
                                         :disabled (when (nil? @kannan-geometria) "disabled")}
      "Piirrä reitti"]]))

(defonce piirrettava-piste1 (r/atom nil))
(defonce piirrettava-piste2 (r/atom nil))
(defn pisteen-piirto
  "Tarjoaa kälin jolla piirretään yksittäinen piste kartalle"
  []
  (let []
    [:div.pisteen-piirto
     [:table
      [:thead [:tr [:th "X"] [:th "Y"]]]
      [:tbody
       [:tr
        [:td
         [:input {:type :text
                  :value (:x @koordinaatti2)
                  :on-change #(swap! koordinaatti2 assoc :x (-> % .-target .-value js/parseFloat))}]]
        [:td
         [:input {:type :text
                  :value (:y @koordinaatti2)
                  :on-change #(swap! koordinaatti2 assoc :y (-> % .-target .-value js/parseFloat))}]]

        [:td
         [:button {:on-click #(do
                                (let [p {:type :point
                                          :coordinates [(:x @koordinaatti2) (:y @koordinaatti2)]}
                                      ;; Lisää piirrettävä piste atomiin, jotta ne saadaan piirrettyä kaikki kartalle
                                      _ (reset! piirrettava-piste1 p)]
                                  (tasot/nayta-geometria! :piste-kartalle1
                                    {:type :piste-kartalle2
                                     :nimi "Piste kartalle"
                                     :alue (assoc p
                                             :fill "red"
                                             :radius 4
                                             :stroke {:color "blue"
                                                      :width 3})})
                                  (tiedot-kartta/keskita-kartta-alueeseen!
                                    (harja.geo/extent-monelle [p @piirrettava-piste2]))))}
          "Piirrä piste"]]]]]]))

(defn pisteen-piirto2
  "Tarjoaa kälin jolla piirretään yksittäinen piste kartalle"
  []
  (let []
    [:div.pisteen-piirto
     [:table
      [:thead [:tr [:th "X"] [:th "Y"]]]
      [:tbody
       [:tr
        [:td
         [:input {:type :text
                  :value (:x @koordinaatti)
                  :on-change #(swap! koordinaatti assoc :x (-> % .-target .-value js/parseFloat))}]]
        [:td
         [:input {:type :text
                  :value (:y @koordinaatti)
                  :on-change #(swap! koordinaatti assoc :y (-> % .-target .-value js/parseFloat))}]]

        [:td
         [:button {:on-click #(do
                                (let [p {:type :point
                                          :coordinates [(:x @koordinaatti) (:y @koordinaatti)]}
                                      ;; Lisää piirrettävä piste atomiin, jotta ne saadaan piirrettyä kaikki kartalle
                                      _ (reset! piirrettava-piste2 p)]
                                  (tasot/nayta-geometria! :piste-kartalle2
                                    {:type :piste-kartalle2
                                     :nimi "Piste kartalle"
                                     :alue (assoc p
                                                :fill "orange"
                                                :radius 4
                                                :stroke {:color "orange"
                                                         :width 3})})
                                  (tiedot-kartta/keskita-kartta-alueeseen! (harja.geo/extent-monelle [@piirrettava-piste1 p]))))}
          "Piirrä piste"]]]]]]))

(defonce reittitoteuman-piste-idt (atom nil))
(defn- nayta-reittitoteuman-pisteet [payload]
  (doseq [id @reittitoteuman-piste-idt]
    (tasot/poista-geometria! id))
  (reset! reittitoteuman-piste-idt [])
  (let [tot (or (get-in payload "reittitoteuma")
                (get-in payload ["reittitoteumat" 0 "reittitoteuma"]))
        {reitti "reitti"} tot]
    (doseq [{rp "reittipiste"} reitti
            :let [aika (get rp "aika")
                  {:strs [x y]} (get rp "koordinaatit")
                  id (str "RP_" aika)]]
      (swap! reittitoteuman-piste-idt conj id)
      (tasot/nayta-geometria! id
                              {:alue {:type :point
                                      :coordinates [(js/parseFloat x) (js/parseFloat y)]}
                               :nimi (str "RP " aika)
                               :type :reittitoteuman-piste}))))

(defn- geometrisoi-ja-nayta-reittitoteuma [json]
  (go
    (let [reitti (<! (k/post! :debug-geometrisoi-reittitoteuma
                              json))]
      (tasot/nayta-geometria! :geometrisoitu-reittitoteuma
        {:type :geometrisoitu-reittitoteuma
         :nimi "Geometrisoitu reittitoteuma"
         :alue (assoc reitti
                 :fill "blue"
                 :radius 4
                 :stroke {:color "blue"
                          :width 3})}))))

(defn- reittitoteuma-payload []
  (let [payload (atom "")]
    (fn []
      [:div
       [:h5 "Reittitoteuma JSON (API)"]
       [:textarea {:rows 10
                   :cols 80
                   :placeholder "Liitä urakoitsijan lähettämä reittitoteuma-JSON tähän"
                   :value @payload
                   :on-change #(let [v (-> % .-target .-value)]
                                 (reset! payload v))}]
       [:button {:on-click #(when-let [p (some-> @payload
                                                 js/JSON.parse
                                                 js->clj)]
                              (nayta-reittitoteuman-pisteet p)
                              (geometrisoi-ja-nayta-reittitoteuma @payload))}
        "Piirrä"]])))

(def tayttovareja
  ;; luodaan värejä järjestyksessä, jotta eri tarkastuksten alku- ja loppupisteet erottuvat toisistaan
  (vec (apply concat (repeat 10 ["red" "orange" "blue" "yellow" "black"]))))

(def tarkastuksia-piirretty (atom nil))

(defn- geometrisoi-ja-nayta-tarkastus [json]
  (go
    (let [{reitti :reitti
           alkupisteet :alkupisteet
           loppupisteet :loppupisteet} (<! (k/post! :debug-geometrisoi-tarkastus json))]
      ;; poistetaan vanhat pisteet
      (doseq [idx (range @tarkastuksia-piirretty)]
        (tasot/poista-geometria! (keyword (str "tarkastuksen-pisteet-alku-" idx)))
        (tasot/poista-geometria! (keyword (str "tarkastuksen-pisteet-loppu-" idx))))

      (tasot/poista-geometria! :tierekisteri-haettu-osoite)
      (tasot/nayta-geometria! :geometrisoitu-tarkastus
                              {:type :geometrisoitu-tarkastus
                               :nimi "Geometrisoitu tarkastus"
                               :alue (assoc reitti
                                       :fill "orange"
                                       :radius 4
                                       :stroke {:color "orange"
                                                :width 3})})
      (doseq [[idx ap] (map-indexed vector alkupisteet)]
        (reset! tarkastuksia-piirretty (count alkupisteet))
        (when-let [g (:geometria ap)]
          (tasot/nayta-geometria! (keyword (str "tarkastuksen-pisteet-alku-" idx))
                                  {:alue (assoc g
                                           :type :circle
                                           :fill (nth tayttovareja idx)
                                           :radius 10
                                           :stroke {:color "blue"
                                                    :width 3})})))
      (doseq [[idx lp] (map-indexed vector loppupisteet)]
        (when-let [g (:geometria lp)]
          (tasot/nayta-geometria! (keyword (str "tarkastuksen-pisteet-loppu-" idx))
                                  {:alue (assoc g
                                           :type :circle
                                           :fill (nth tayttovareja idx)
                                           :radius 10
                                           :stroke {:color "orange"
                                                    :width 3})}))))))

(defn- tarkastus-payload []
  (let [payload (atom "")]
    (fn []
      [:div
       [:h5 "Tarkastus JSON (API)"]
       [:textarea {:rows 10
                   :cols 80
                   :placeholder "Liitä urakoitsijan lähettämä tarkastus-JSON tähän"
                   :value @payload
                   :on-change #(let [v (-> % .-target .-value)]
                                 (reset! payload v))}]
       [:button {:on-click #(when-let [p (some-> @payload
                                                 js/JSON.parse
                                                 js->clj)]
                              (geometrisoi-ja-nayta-tarkastus @payload))}
        "Piirrä tarkastus"]
       [yleiset/vihje "Tarkastuksen alkupiste (sininen kehä) ja loppupiste (oranssi kehä) piirrettään ympyränä. Voit katsoa niiden avulla, piirtyykö oranssina näkyvä reittiviiva oikein. Joskus uuden tarkastuksen alkupiste on sama kuin edellisen loppupiste - tällöin piirtyy vain loppupiste. Asian voit tarkistaa laittamalla vain ko. tarkastuksen JSON:in payload kenttään."]])))


(defn- hae-ja-nayta-rajoitusalueet [urakka-id]
  (go
    (let [rajoitusalueet (<! (k/post! :debug-hae-rajoitusalueet urakka-id))
          rajoitusalueen-reitit (keep
                                  (fn [r]
                                    (when (:sijainti r)
                                      (:sijainti r)))
                                  rajoitusalueet)]
      (doall (for [r rajoitusalueet] (keyword (str "rajoitusalue-" (:id r)))))
      (tasot/poista-geometria! :rajoitusalueet)
      (doall (for [r rajoitusalueet]
               (when (:sijainti r)
                 (tasot/nayta-geometria! (keyword (str "rajoitusalue-" (:id r)))
                   {:type :rajoitusalueet
                    :nimi "Urakan rajoitusalueet"
                    :alue (assoc (:sijainti r)
                            :fill "orange"
                            :radius 4
                            :stroke {:color "orange"
                                     :width 3})}))))
      ;; Keskitä
      (tiedot-kartta/keskita-kartta-alueeseen! (harja.geo/extent-monelle rajoitusalueen-reitit)))))

(defonce suola-geo-nimet  (r/atom []))
(defn- hae-ja-nayta-suolat [urakka-id alkupaiva loppupaiva]
  (go
    (let [;; Poista atomista löytyvät suolageometriat
          _ (doall (for [p @suola-geo-nimet]
                     (tasot/poista-geometria! p)))
          ;; Hae uudet
          suolat (<! (k/post! :debug-hae-paivan-suolatoteumat {:urakka-id urakka-id
                                                               :alkupaiva alkupaiva
                                                               :loppupaiva loppupaiva}))
          suola-pisteet (keep (fn [s] (when (:sijainti s) (:sijainti s))) suolat)
          ;; Lisää uudet suolat atomiin
          _ (map-indexed
              (fn [idx s]
                (when (:sijainti s)
                  (swap! suola-geo-nimet conj (keyword (str "suola-" (:alkanut s) "-" idx)))))
              suolat)]
      (doseq [[idx s] (map-indexed vector suolat)]
        (when (:sijainti s)
          (tasot/nayta-geometria! (keyword (str "suola-" (:alkanut s) "-" idx))
            {:type :suolat
             :nimi "Urakan suolat"
             :alue (assoc (:sijainti s)
                     :fill "blue"
                     :radius 3
                     :stroke {:color "blue"
                              :width 4})})))
      ;; Keskitä
      (when (not (empty? suola-pisteet))
        (tiedot-kartta/keskita-kartta-alueeseen! (harja.geo/extent-monelle suola-pisteet))))))

(defn- urakan-rajoitusalueet []
  (let []
    (fn []
      [:div.lomake
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:label.control-label "Urakka-id:"]
         [:input {:type :text
                  :class "form-control"
                  :value @rajoitusalue-urakka
                  :on-change #(reset! rajoitusalue-urakka (-> % .-target .-value))}]]]
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:button {:on-click #(do
                                (let []
                                  (hae-ja-nayta-rajoitusalueet @rajoitusalue-urakka)))}
          "Piirrä rajoitusalueet"]]]])))

(defn- urakan-suolat []
  (let [suola-urakka (r/atom nil)
        suola-alkupaiva (r/atom nil)
        suola-loppupaiva (r/atom nil)]
    (fn []
      [:div.lomake
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:label.control-label "Urakka-id:"]
         [:input {:type :text
                  :class "form-control"
                  :value @suola-urakka
                  :on-change #(reset! suola-urakka (-> % .-target .-value))}]]]
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:label.control-label "Alkupäivä: (anna muodossa: 2023-01-01)"]
         [:input {:type :text
                  :class "form-control"
                  :value @suola-alkupaiva
                  :on-change #(reset! suola-alkupaiva (-> % .-target .-value))
                  :placeholder "2023-01-01"}]]]
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:label.control-label "Loppupäivä: (anna muodossa: 2023-01-01)"]
         [:input {:type :text
                  :class "form-control"
                  :value @suola-loppupaiva
                  :on-change #(reset! suola-loppupaiva (-> % .-target .-value))
                  :placeholder "2023-01-01"}]]]
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:button {:on-click #(do
                                (let []
                                  (hae-ja-nayta-suolat @suola-urakka @suola-alkupaiva @suola-loppupaiva)))}
          "Piirrä suolatoteuat kartalle"]]]])))

(defonce urakan-geo-nimet  (r/atom []))
(defn- hae-ja-nayta-urakan-geometriat [urakka-id]
  (go
    (let [;; Poista atomista löytyvät geometriat
          _ (doall (for [p @urakan-geo-nimet]
                     (tasot/poista-geometria! p)))
          ;; Hae geometriat
          urakan-geometriat (<! (k/post! :debug-hae-urakan-geometriat {:urakka-id urakka-id}))
          ;; Lisää uudet geometria atomiin
          _ (map-indexed
              (fn [idx g]
                (when (:alue g)
                  (swap! urakan-geo-nimet conj (keyword (str "urakka-" idx)))))
              urakan-geometriat)]
      ;; Piirrä geometriat kartalle
      (doseq [[idx s] (map-indexed vector urakan-geometriat)]
        (when (:alue s)
          (tasot/nayta-geometria! (keyword (str "urakka-" idx))
            {:type :urakan-geometriat
             :nimi "Urakan geometriat"
             :alue (assoc (:alue s)
                     ;:fill "black"
                     :radius 3
                     :stroke {:color "blue"
                              :width 1})})))
      ;; Keskitä - Ikävä kyllä keskittäminen ei toimi valaistusurakoille.
      ;; Keskittäminen on ilmeisesti kartalle raskastoimenpide, joten otetaan se nyt tässä vaiheessa kokonaan pois käytöstä.
      #_ (when (not (empty? urakan-geometriat))
        (tiedot-kartta/keskita-kartta-alueeseen! (harja.geo/extent-monelle urakan-geometriat))))))

(defn- urakan-geometriat []
  (let [urakka-id (r/atom nil)]
    (fn []
      [:div.lomake
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:label.control-label "Urakka-id:"]
         [:input {:type :text
                  :class "form-control"
                  :value @urakka-id
                  :on-change #(reset! urakka-id (-> % .-target .-value))}]]]


       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:button {:on-click #(do
                                (let []
                                  (hae-ja-nayta-urakan-geometriat @urakka-id)))}
          "Piirrä urakan geometriat kartalle"]]]])))

(defonce tieturvallisuus-idt  (r/atom []))
(defn- hae-ja-nayta-tieturvallisuus-geometriat []
  (go
    (let [;; Poista atomista löytyvät geometriat
          _ (doall (for [p @tieturvallisuus-idt]
                     (tasot/poista-geometria! p)))
          ;; Hae geometriat
          tieturvallisuus-geometriat (<! (k/post! :debug-hae-tieturvalliusuus-geometriat {}))
          ;; Lisää uudet geometria atomiin
          _ (map-indexed
              (fn [idx g]
                (when (:geometria g)
                  (swap! tieturvallisuus-idt conj (keyword (str "tieturvallisuus-" idx)))))
              tieturvallisuus-geometriat)]
      
      ;; Piirrä geometriat kartalle
      (doseq [[idx s] (map-indexed vector tieturvallisuus-geometriat)]
        (when (:geometria s)
          (tasot/nayta-geometria! (keyword (str "tieturvallisuus-" idx))
            {:type :tieturvallisuus-geometriat
             :nimi "Tieturvallisuusgeometriat"
             :alue (assoc (:geometria s)
                     ;:fill "black"
                     :radius 3
                     :stroke {:color "blue"
                              :width 1})}))))))

(defn- tieturvallisuus-geometriat []
  (let []
    (fn []
      [:div.lomake
       [:div.row.lomakerivi
        [:div.form-group.col-xs-12.col-sm-6.col-md-5.col-lg-4
         [:button {:on-click #(do
                                (let []
                                  (hae-ja-nayta-tieturvallisuus-geometriat)))}
          "Piirrä tieturvallisuusverkko kartalle"]]]])))


(defn tierekisteri []
  (komp/luo
   (komp/lippu-arvo false true kartta-tiedot/pida-geometriat-nakyvilla?)
   (komp/avain-lippu nav/tarvitsen-isoa-karttaa :tierekisteri)
   (fn []
     [:div.tr-debug
      [kartta/kartan-paikka]
      [:h1 "Tervetuloa salaiseen TR osioon"]
      [tr-haku]
      [:hr]
      [:h3 "Koordinaattihaku"]
      [koordinaatti-haku]
      [:hr]
      [:h3 "Valitse kartalta"]
      (if @valitse-kartalla?
        [tr/karttavalitsin {:kun-peruttu #(do
                                            (reset! valittu-osoite nil)
                                            (reset! valitse-kartalla? false))
                            :paivita #(reset! valittu-osoite %)
                            :kun-valmis #(do
                                           (when-let [g (:geometria %)]
                                             (tasot/nayta-geometria!
                                              :tierekisteri-valinta-haku
                                              {:alue g}))
                                           (reset! valittu-osoite %))}]
        [:button {:on-click #(reset! valitse-kartalla? true)}
         "Valitse kartalla"])
      (when-let [valittu @valittu-osoite]
        [:div (pr-str valittu)])
      [tarkastus-payload]
      [:hr]
      [:h3 "Reittipisteiden haku - toteumille, tyokonehavainnoille tai tarkastusajoille"]
      [reittipisteiden-haku]
      [reittitoteuma-payload]
      [:hr]
      [:h3 "Tietokannasta haetun geometrian piirto kartalle"]
      [geometrian-piirto]
      [:hr]
      [:h3 "Piirrä piste kartalle"]
      [pisteen-piirto]
      [:h3 "Piirrä toinen kartalle"]
      [pisteen-piirto2]
      [:hr]
      [:h3 "Visualisoi urakan pohjavesien rajoitusalueet kartalle"]
      [urakan-rajoitusalueet]
      [:hr]
      [:h3 "Visualisoi suolatoteumat kartalle"]
      [urakan-suolat]
      [:hr]
      [:h3 "Visualisoi urakan geometria kartalle"]
      [urakan-geometriat]
      [:h3 "Piirrä tieturvallisuusverkon geometria kartalle"]
      [tieturvallisuus-geometriat]])))

;; eism tie 20
;; x: 431418, y: 7213120
;; x: 445658, y: 7224320
