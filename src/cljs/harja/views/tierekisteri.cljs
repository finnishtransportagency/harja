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
            [harja.pvm :as pvm])
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


(defn hae-vkm! []
  (dotimes [i 10]
    (tasot/poista-geometria! (keyword (str "vkm-tr-osoite-" i))))
  (go
    (let [tulos
          (<! (vkm/tieosoite @tr))
          polut (get-in tulos ["lines" "lines"])]
      (log "POLKUJA " (count polut))
      (doseq [ajr (range (count polut))]
        (tasot/nayta-geometria! (keyword (str "vkm-tr-osoite-" ajr))
                                {:alue (maarittele-feature
                                        {:type :line
                                         :points (get-in polut [ajr "paths" 0])}
                                        false
                                        asioiden-ulkoasu/tr-ikoni
                                        asioiden-ulkoasu/tr-viiva)})))))

(defn kaanna! []
  (swap! tr
         (fn [{:keys [alkuosa alkuetaisyys loppuosa loppuetaisyys] :as tr}]
           (assoc tr
                  :alkuosa loppuosa
                  :alkuetaisyys loppuetaisyys
                  :loppuosa alkuosa
                  :loppuetaisyys alkuetaisyys)))
  (hae!))

(def tr-kentat [["Tie" :numero]
                ["Aosa" :alkuosa]
                ["Aet" :alkuetaisyys]
                ["Losa" :loppuosa]
                ["Let" :loppuetaisyys]])

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

(defn- piirra-reittipisteet
  [pisteet]
  (let [tyyppi (:tyyppi @tarkasteltava-asia)]
    (doseq [piste pisteet]
      (tasot/nayta-geometria! (:id piste)
                              {:nimi (when-let [aika (:aika piste)]
                                       (pvm/pvm-aika-sek aika))
                               :type :reittipisteet
                               :alue (assoc (:sijainti piste)
                                            :fill (and (= tyyppi :tarkastusajo)
                                                       (integer? (first (:havainnot piste))))
                                            :color "red")}))))

(defn- piirra-reitti
  [reitti]
  (let [tyyppi (:tyyppi @tarkasteltava-asia)]
    (tasot/nayta-geometria! :tarkasteltava-reitti
                            {:alue reitti})))

(defn hae-ja-nayta-reittipisteet []
  (let [{:keys [tyyppi id]} @tarkasteltava-asia]
    (case tyyppi
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
    [:button {:on-click hae-vkm!} "Hae VKM polut"]]])

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
      [:tarkastusajo
       :toteuma]


      ]
     [:h5 "Hae " (case tyyppi
                   :tarkastusajo "tarkastusajon reittipisteet"
                   :toteuma "toteuman reitti ja reittipisteet")]
     [:label.tarkastusajoid-label tyyppi " id"]
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
                               :alue reitti}))))

(defn- reittitoteuma-payload []
  (let [payload (atom "")]
    (fn []
      [:div
       [:h5 "Reittitoteuma JSON pisteet"]
       [:textarea {:rows 10
                   :cols 80
                   :placeholder "Pasteappa urakoitsijan lähettämä reittitoteuma JSON tähän"
                   :value @payload
                   :on-change #(let [v (-> % .-target .-value)]
                                 (reset! payload v))}]
       [:button {:on-click #(when-let [p (some-> @payload
                                                 js/JSON.parse
                                                 js->clj)]
                              (nayta-reittitoteuman-pisteet p)
                              (geometrisoi-ja-nayta-reittitoteuma @payload))}
        "piirteleppä!"]])))

(defn tierekisteri []
  (komp/luo
   (komp/lippu-arvo false true kartta-tiedot/pida-geometriat-nakyvilla?)
   (komp/avain-lippu nav/tarvitsen-isoa-karttaa :tierekisteri)
   (fn []
     [:div.tr-debug
      [kartta/kartan-paikka]
      [:div "Tervetuloa salaiseen TR osioon"]
      [tr-haku]
      [:hr]
      [koordinaatti-haku]
      [:hr]
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
      [:hr]
      [reittipisteiden-haku]
      [reittitoteuma-payload]])))

;; eism tie 20
;; x: 431418, y: 7213120
;; x: 445658, y: 7224320
