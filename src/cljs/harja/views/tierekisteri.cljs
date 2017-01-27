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
            [harja.loki :refer [log]]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tr (r/atom {}))
(defonce sijainti (atom nil))

(defonce koordinaatti (r/atom {:x nil :y nil}))
(defonce koordinaatin-osoite (r/atom nil))

(defonce valitse-kartalla? (r/atom false))
(defonce valittu-osoite (r/atom nil))

(defonce tarkastusajon-id (atom nil))
(defonce piirrettyjen-reittipisteiden-idt (atom nil))

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

(defn- poista-vanhat-reittipisteet-kartalta
  [pisteet]
  (doseq [piste pisteet]
    (tasot/poista-geometria! (:id piste))))

(defn- piirra-tarkastusajon-reittipisteet
  [pisteet]
  (poista-vanhat-reittipisteet-kartalta @piirrettyjen-reittipisteiden-idt)
  (doseq [piste pisteet]
    (tasot/nayta-geometria! (:id piste)
                            {:alue (assoc (:sijainti piste)
                                     :fill (integer? (first (:havainnot piste)))
                                     :color "red")}))
  (reset! piirrettyjen-reittipisteiden-idt pisteet))

(defn hae-ja-nayta-tarkastusajon-reittipisteet []
  (go
    (let [pisteet (<! (k/post! :hae-tarkastusajon-reittipisteet
                               {:tarkastusajon-id @tarkastusajon-id}))]
      (piirra-tarkastusajon-reittipisteet pisteet))))

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


(defn tarkastusajon-reittipisteiden-haku
  "Tarjoaa kälin jolla haetaan tarkastusajoid:llä reitti"
  []
  [:div.tierekisteri-tarkastusajon-id
   [:h5 "Hae tarkastusajon reittipisteet"]
   [:label.tarkastusajoid-label "Tarkastusajon id"]
   [:input {:type :text
            :placeholder "Tietokannasta"
            :on-change #(reset! tarkastusajon-id (-> % .-target .-value js/parseInt))}]
   [:button.nappi-ensisijainen.btn-xs {:on-click hae-ja-nayta-tarkastusajon-reittipisteet
             :disabled (when (nil? @tarkastusajon-id) "disabled")}
    "Piirrä reitti"]])

(defn tierekisteri []
  (komp/luo
   (komp/lippu-arvo false true @kartta-tiedot/pida-geometriat-nakyvilla?)
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
      [tarkastusajon-reittipisteiden-haku]])))

;; eism tie 20
;; x: 431418, y: 7213120
;; x: 445658, y: 7224320
