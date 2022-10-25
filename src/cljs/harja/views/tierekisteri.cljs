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
            [clojure.walk :as walk])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tr (r/atom {}))
(defonce sijainti (atom nil))

(defonce koordinaatti (r/atom {:x nil :y nil}))
(defonce koordinaatin-osoite (r/atom nil))

(defonce valitse-kartalla? (r/atom false))
(defonce valittu-osoite (r/atom nil))

(defonce tarkasteltava-asia (atom {:tyyppi :tarkastusajo
                                   :id nil
                                   :reitti nil
                                   :pisteet nil}))


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
  (let [tyyppi (:tyyppi @tarkasteltava-asia)]
    (tasot/nayta-geometria! :tarkasteltava-reitti
                            {:alue (assoc reitti
                                     :fill "black"
                                     :radius 40
                                     :stroke {:color "black"
                                              :width 4})})))

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
      (doseq [idx @tarkastuksia-piirretty]
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
                                           :text "alkupiste"
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
      [reittitoteuma-payload]])))

;; eism tie 20
;; x: 431418, y: 7213120
;; x: 445658, y: 7224320
