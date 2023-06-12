(ns harja.ui.kartta-debug
  "Näyttää kartan tilan. Defaulttina näytetään layerit, jotka on aktiivisiksi merkattu.
   Layereiden checkboxit on disabloitu, mikäli niiden näyttämää dataa ei ole haettu.
   Infopaneelista näytetään näkee linkki funktiot ja kartalle näytettävät asiat. Infopaneelin
   tiedot näyttävät nappulat on näkyvissä vain, kun infopaneelikin on näkyvissä."
  (:require [harja.views.kartta.tasot :as tasot]
            [harja.ui.dom :as dom]
            [reagent.core :as r :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.kartta :as kartta]
            [harja.fmt :as fmt]
            [harja.ui.kentat :as kentat]
            [harja.ui.kartta.infopaneelin-sisalto :as info]
            [clojure.string :as clj-str]
            [clojure.set :as set])
  (:require-macros [reagent.ratom :refer [reaction]]))

(declare aseta-kartta-debug-sijainti pakota-paivitys)

(defonce tila (atom {:nayta-kartan-debug? true
                     :nayta-kaikki-layerit? false
                     :nayta-kartan-ylaosassa? true
                     :nayta-infopaneelin-tiedot? true
                     :kartan-paikka []}))
(defonce layers (reaction (into {} (map (fn [[kerros kerroksen-tila-atom]]
                                          [kerros @kerroksen-tila-atom])
                                        tasot/tasojen-nakyvyys-atomit))))

(defonce geometriat (reaction (into {} (map (fn [[kerros kerroksen-geometria]]
                                              [kerros @kerroksen-geometria])
                                            tasot/geometrioiden-atomit))))

(defn- varita-mappi
  "Tän vois refaktoroida nätimmäksi ja nimenkin muuttaa. Tässä consoliin
   logitetaan vektoretia ja mappeja ryhmitettynä ja muutamia keywordeja
   varitetaan."
  [geometriat tietosisalto]
  (let [kasittele-map-fn (fn [mappi]
                           (let [pakolliset-kentat (case tietosisalto
                                                     :layer #{:tyyppi-kartalla :alue}
                                                     :linkki-funktiot #{}
                                                     :asiat-raaka #{:tyyppi-kartalla}
                                                     :asiat-kasitelty #{:otsikko :tiedot :data :tyyppi :jarjesta-fn})
                                 mielenkiintoiset-kentat-ylin-taso (case tietosisalto
                                                                     :layer #{:sijainti :type :selite}
                                                                     :linkki-funktiot #{:teksti :teksti-fn :ikoni :tooltip :toiminto :when}
                                                                     :asiat-raaka #{}
                                                                     :asiat-kasitelty #{})]
                             (when (:ylin-taso? (meta mappi))
                               (doseq [varitettava-kentta (set/union pakolliset-kentat mielenkiintoiset-kentat-ylin-taso)]
                                 (when (not (contains? mappi varitettava-kentta))
                                   (cond
                                     (pakolliset-kentat varitettava-kentta) (console.log (str "%c" varitettava-kentta) "color: red")
                                     (mielenkiintoiset-kentat-ylin-taso varitettava-kentta) (console.log (str "%c" varitettava-kentta) "color: orange")))))
                             (doseq [[avain arvo] mappi
                                     :let [varita-sininen? (pakolliset-kentat avain)
                                           varita-ruskea? (mielenkiintoiset-kentat-ylin-taso avain)
                                           avain-printtaus (if (:ylin-taso? (meta mappi))
                                                             (cond
                                                               varita-sininen? [(str "%c" avain) "color: blue"]
                                                               varita-ruskea? [(str "%c" avain) "color: darkturquoise "]
                                                               :else (str avain))
                                                             (str avain))
                                           varitetaan? (and (or varita-sininen? varita-ruskea?)
                                                            (:ylin-taso? (meta mappi)))]]
                               (if (or (vector? arvo) (map? arvo))
                                 (do (if varitetaan?
                                       (apply console.log avain-printtaus)
                                       (console.log avain-printtaus))
                                     (varita-mappi arvo tietosisalto))
                                 (if varitetaan?
                                   (apply console.log [(str (first avain-printtaus) " %c" arvo)
                                                       (str (last avain-printtaus))
                                                       "color: black"])
                                   (console.log (str avain-printtaus " " arvo)))))))]
    (cond
      (vector? geometriat) (do
                             (console.group "VECTOR")
                             (doseq [geometry geometriat]
                               (varita-mappi geometry tietosisalto))
                             (console.groupEnd))
      (map? geometriat) (do
                          (console.group "HASH-MAP")
                          (kasittele-map-fn geometriat)
                          (console.groupEnd))
      :else (console.log (str geometriat)))))

(defn- checkbox-kentta
  [{:keys [teksti checked? disabled? on-change]}]
  [:div.checkbox {:style {:pointer-events "auto"}}
   [:label {:on-click #(.stopPropagation %)}
    [:input {:type "checkbox"
             :checked checked?
             :disabled disabled?
             :on-change on-change
             :data-tooltip "foo"}]
    teksti]])

(defn- nayta-asetukset []
  (let [optiot-fn (fn [teksti avain & toimintoja]
                    {:teksti teksti :checked? (avain @tila) :disabled? false
                     :on-change #(let [valittu? (-> % .-target .-checked)]
                                   (swap! tila assoc avain valittu?)
                                   (when (not-empty toimintoja)
                                     (doseq [toiminto toimintoja]
                                       (toiminto))))})]
    [:div
     [checkbox-kentta (optiot-fn "Nayta kartan debug?" :nayta-kartan-debug?)]
     [checkbox-kentta (optiot-fn "Nayta kaikki layerit?" :nayta-kaikki-layerit?)]
     [checkbox-kentta (optiot-fn "Nayta infopaneelin tiedot? " :nayta-infopaneelin-tiedot?)]
     [checkbox-kentta (optiot-fn "Nayta kartan yläosassa?" :nayta-kartan-ylaosassa? #(apply aseta-kartta-debug-sijainti (:kartan-paikka @tila)))]]))

(defn- nayta-layersit []
  [:div {:style {:display "flex"
                 :flex-flow "column wrap"
                 :pointer-events "none"}}
   (doall
     (sort-by #(-> % last :jarjestys)
              (keep (fn [[taso paalla?]]
                      (when (or paalla?
                                (:nayta-kaikki-layerit? @tila))
                        (let [tason-geometria (taso @geometriat)
                              z-index (-> @tasot/geometriat-kartalle taso meta :zindex)
                              geometria? (some? tason-geometria)
                              on-change #(reset! (taso tasot/tasojen-nakyvyys-atomit) (-> % .-target .-checked))]
                          ^{:key taso}
                          [checkbox-kentta {:teksti [:span
                                                     [:span (str (name taso) "(" z-index ")")]
                                                     [:button {:on-click #(do (.stopPropagation %)
                                                                              (varita-mappi (cond
                                                                                              (vector? tason-geometria) (mapv (fn [geometria]
                                                                                                                                (with-meta
                                                                                                                                  geometria
                                                                                                                                  {:ylin-taso? true}))
                                                                                                                              tason-geometria)
                                                                                              :else tason-geometria)
                                                                                            :layer))}
                                                      (str " geometriat")]]
                                            :checked? paalla?
                                            :disabled? (not geometria?)
                                            :on-change on-change
                                            :jarjestys (name taso)}])))
                    @layers)))])

(defn- nayta-infopaneelin-tiedot []
  (when (and (:nayta-infopaneelin-tiedot? @tila)
             @kartta/infopaneeli-nakyvissa?)
    (let [linkki-funkitot-vektori? (vector? @kartta/infopaneelin-linkkifunktiot)
          metan-asettaminen-linkkifunktioille #(into {} (map (fn [[avain arvo]]
                                                               [avain (if (vector? arvo)
                                                                        (mapv (fn [mappi] (with-meta mappi {:ylin-taso? true})) arvo)
                                                                        (with-meta arvo {:ylin-taso? true}))])
                                                             @kartta/infopaneelin-linkkifunktiot))
          ;; harja.views.kartta viittauksesta tulee warningia käännösvaiheessa,
          ;; mutta ilmeisesti ajon aikana toimii
          metan-asettaminen-asioille-raaka #(update @harja.views.kartta/asiat-pisteessa :asiat (fn [asiat]
                                                                                                 (mapv (fn [asia]
                                                                                                         (with-meta asia {:ylin-taso? true}))
                                                                                                       asiat)))
          metan-asettaminen-asioille-kasitelty #(->> @harja.views.kartta/asiat-pisteessa
                                                     :asiat
                                                     info/skeemamuodossa
                                                     (mapv (fn [asia]
                                                             (with-meta asia {:ylin-taso? true}))))]
      [:div {:style {:display "flex"
                     :flex-flow "column wrap"
                     :pointer-events "none"}}
       "Infopaneelin tiedot"
       [:button {:style {:pointer-events "auto"}
                 :on-click #(do (.stopPropagation %)
                                (varita-mappi (metan-asettaminen-linkkifunktioille)
                                              :linkki-funktiot))}
        (str "Linkki funktiot")]

       [:button {:style {:pointer-events "auto"}
                 :on-click #(do (.stopPropagation %)
                                (varita-mappi (metan-asettaminen-asioille-raaka) :asiat-raaka))}
        (str "Asiat Pisteessä (raaka)")]

       [:button {:style {:pointer-events "auto"}
                 :on-click #(do (.stopPropagation %)
                                (varita-mappi (metan-asettaminen-asioille-kasitelty) :asiat-kasitelty))}
        (str "Asiat Pisteessä (käsitelty)")]])))

(defn kartta-layers*
  [korkeus {:keys [nayta-kartan-debug? top left]}]
  (let [kartta-containerin-top (try (-> (dom/elementti-idlla "kartta-container") .-style .-top)
                                    (catch :default e nil))
        tilannekuvan-valinnat-leveys (try (-> (.getElementsByClassName js/document "haitari-tilannekuva") (aget 0) .-clientWidth)
                                          (catch :default e 0))
        kartta-containerin-left (try (-> (dom/elementti-idlla "kartta-container") .-style .-left (+ 30) tilannekuvan-valinnat-leveys)
                                     (catch :default e nil))
        asetettava-top (or kartta-containerin-top top (- korkeus))
        asetettava-left (or kartta-containerin-left left)]
    (when nayta-kartan-debug?
      [:div#kartta-debug {:style {:position "absolute"
                                  :z-index "901"
                                  :top asetettava-top
                                  :left asetettava-left
                                  :pointer-events "none"}}
       [:div {:style {:height "inherit"
                      :pointer-events "none"
                      :display "flex"
                      :overflow (if @nav/kartta-nakyvissa?
                                  "visible"
                                  "hidden")}}
        [nayta-asetukset]
        [nayta-layersit]
        [nayta-infopaneelin-tiedot]]])))

(defn kartta-layers
  [korkeus]
  (r/create-class
    {:reagent-render (fn [korkeus]
                       [kartta-layers* korkeus @tila])

     ;; Tämä manuaalinen on-click käsittelijän lisäys on valitettavasti pakko tehdä,
     ;; koska ol3/React eventtien käsittelyeroista johtuen :on-click asetetut
     ;; handlerit eivät koskaan laukea kontrollien stopevent parentin takia.
     :component-did-mount (fn [this]
                            (swap! tila assoc :komponentti this))}))

(defn aseta-kartta-debug-sijainti
  [x y w h naulattu?]
  (swap! tila assoc
         :kartan-paikka [x y w h naulattu?]
         :top (if (:nayta-kartan-ylaosassa? @tila)
                    y
                    (+ y h))
         :left (fmt/pikseleina (+ x 30 (try (-> (.getElementsByClassName js/document "haitari-tilannekuva") (aget 0) .-clientWidth)
                                            (catch :default e 0)))))
  (when (:nayta-kartan-debug? @tila)
    (when-let
      [karttasailio (dom/elementti-idlla "kartta-debug")]
      (let [tyyli (.-style karttasailio)]
        ;;(log "ASETA-KARTAN-SIJAINTI: " x ", " y ", " w ", " h ", " naulattu?)
        (if naulattu?
          (do
            (set! (.-position tyyli) "fixed")
            (set! (.-height tyyli) (fmt/pikseleina h)))
          (do
            (set! (.-position tyyli) "absolute")
            (set! (.-height tyyli) (fmt/pikseleina h))))
        (set! (.-position tyyli) (-> "kartta-debug" dom/elementti-idlla .-style .-position str))
        (when (= :S @nav/kartan-koko)
          (set! (.-left tyyli) "")
          (set! (.-right tyyli) (fmt/pikseleina 20)))))))

(defn pakota-paivitys []
  (println "PÄIVITETÄÄN")
  (r/force-update (:komponentti @tila) true))

(defn nayta-kartan-debug []
  (swap! tila assoc :nayta-kartan-debug? true)
  (apply aseta-kartta-debug-sijainti (:kartan-paikka @tila)))
