(ns harja.ui.taulukko-debug
  (:require [cljs.core.async :as async]
            [clojure.data :as data]
            [reagent.core :refer [atom] :as r]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(goog-define ^boolean TAULUKKO_DEBUG true)

(defonce aktiivinen-taulukko-debug (atom nil))

(defprotocol AsianDebugArvo
  (piirra [this]))

(defn nayta-kaikki-arvot [a _]
  (swap! a not))

(defn indikoi-asia!
  [taulukko asia _]
  (when (satisfies? p/Asia asia)
    (cond
      ;; special käsittelyt:
      (= jana/RiviLapsilla (type asia)) (indikoi-asia! taulukko (first (p/arvo asia :lapset)) nil)
      ;; peruskäsittely
      (satisfies? p/Jana asia) (p/paivita-rivi! taulukko
                                                (p/paivita-arvo asia :class
                                                                (fn [luokat]
                                                                  (if (nil? luokat)
                                                                    #{"taulukko-debug-rivi"}
                                                                    (conj luokat "taulukko-debug-rivi")))))
      (satisfies? p/Osa asia) (p/paivita-solu! taulukko
                                               (p/paivita-arvo asia :class
                                                               (fn [luokat]
                                                                 (if (nil? luokat)
                                                                   #{"taulukko-debug-osa"}
                                                                   (conj luokat "taulukko-debug-osa")))))
      :else nil)))
(defn deindikoi-asia!
  [taulukko asia _]
  (when (satisfies? p/Asia asia)
    (cond
      ;; special käsittelyt:
      (= jana/RiviLapsilla (type asia)) (deindikoi-asia! taulukko (first (p/arvo asia :lapset)) nil)
      ;; peruskäsittely
      (satisfies? p/Jana asia) (p/paivita-rivi! taulukko
                                                (p/paivita-arvo asia :class
                                                                (fn [luokat]
                                                                  (disj luokat "taulukko-debug-rivi"))))
      (satisfies? p/Osa asia) (p/paivita-rivi! taulukko
                                               (p/paivita-arvo asia :class
                                                               (fn [luokat]
                                                                 (disj luokat "taulukko-debug-osa"))))
      :else nil)))

(defn piirra-asia [asia]
  [:<>
   (cond
     (satisfies? AsianDebugArvo asia) [piirra asia]
     (nil? asia) [:span "nil"]
     :else [:span (str asia)])])

(defn piirra-lista [lista alku-merkki loppu-merkki avain-arvo?]
  (let [max-pituus 10
        nayta-kaikki? (atom false)]
    (fn [lista alku-merkki loppu-merkki avain-arvo?]
      (let [pituus (count lista)
            liian-pitka? (> pituus max-pituus)
            lista (if @nayta-kaikki?
                    lista
                    (take max-pituus lista))
            sulku-luokka (case alku-merkki
                           "[" "vector-sulku"
                           "{" "map-sulku"
                           "#{" "set-sulku"
                           "lazy(" "lazy-sulku"
                           "(" "list-sulku"
                           "object{" "object-sulku")]
        [:span.lista {:class (str sulku-luokka " "
                                  (if avain-arvo?
                                    "mapin-arvot"
                                    "listan-arvot"))}
         (doall
           (concat
             (map-indexed (fn [index arvo]
                            ^{:key index}
                            [piirra-asia arvo])
                          lista)
             (when (and liian-pitka? (not @nayta-kaikki?))
               [:span {:on-click (r/partial nayta-kaikki-arvot nayta-kaikki?)} "..."])))]))))

(extend-protocol AsianDebugArvo
  cljs.core/PersistentVector
  (piirra [this]
    [:<>
     [piirra-lista this "[" "]" false]])
  cljs.core/PersistentArrayMap
  (piirra [this]
    [:<>
     [piirra-lista this "{" "}" true]])
  cljs.core/MapEntry
  (piirra [this]
    [:span.avain-arvo
     [piirra-asia (first this)]
     [piirra-asia (second this)]])
  cljs.core/PersistentHashSet
  (piirra [this]
    [:<>
     [piirra-lista this "#{" "}" false]])
  cljs.core/LazySeq
  (piirra [this]
    [:<>
     [piirra-lista this "lazy(" ")" false]])
  cljs.core/List
  (piirra [this]
    [:<>
     [piirra-lista this "(" ")" false]])
  cljs.core/Keyword
  (piirra [this]
    [:span.keyword (str this)])
  reagent.ratom/RAtom
  (piirra [this]
    [:span.ratom (str this)])
  object
  (piirra [this]
    [:<>
     [piirra-lista this "object{" "}" true]])
  string
  (piirra [this]
    [:span.string this])
  function
  (piirra [this]
    [:span.funktio (print-str this)])
  number
  (piirra [this]
    [:span.number (str this)])
  boolean
  (piirra [this]
    [:span.boolean (str this)]))

(declare vanhempi-komponentti asian-arvot)

(defn nappi
  [teksti auki? nappia-klikattu-fn]
  [:button.asian-nappi {:on-click nappia-klikattu-fn}
   [:div
    (if auki?
      ^{:key :asia-auki}
      [ikonit/livicon-chevron-down]
      ^{:key :asia-kiinni}
      [ikonit/livicon-chevron-right])
    teksti]])

(defn piirra-lehti [taulukko asia sisennys vanhempi-auki?]
  (let [auki? (atom false)
        nappia-klikattu (fn [event]
                          (swap! auki? not))]
    (fn [taulukko asia sisennys vanhempi-auki?]
      (let [lehti-auki? (and @auki? vanhempi-auki?)]
        [:span.lehti {:style {:padding-left (str sisennys "px")}}
         [:span {:on-mouse-over (r/partial indikoi-asia! taulukko asia)
                 :on-mouse-leave (r/partial deindikoi-asia! taulukko asia)}
          (print-str (type asia))]
         [nappi "Tiedot" lehti-auki? nappia-klikattu]
         (when lehti-auki?
           [:span.asian-arvot
            [piirra-asia asia]])]))))

(defn lapsi-komponentti [taulukko asia sisennys vanhempi-auki?]
  [:<>
   (if-let [lapset (and (satisfies? p/Asia asia) (p/arvo asia :lapset))]
     (for [lapsi lapset
           :let [id (if-let [id (cond
                                  (satisfies? p/Jana lapsi) (p/janan-id lapsi)
                                  (satisfies? p/Osa lapsi) (p/osan-id lapsi))]
                      id
                      (gensym))
                 lapsi-on-lehti? (nil? (p/arvo lapsi :laspset))]]
       (if lapsi-on-lehti?
         ^{:key id}
         [piirra-lehti taulukko asia sisennys vanhempi-auki?]
         ^{:key id}
         [vanhempi-komponentti taulukko lapsi sisennys vanhempi-auki?]))
     [piirra-lehti taulukko asia sisennys vanhempi-auki?])])

(defn drag-nappi [ikkunan-korkeus-fn! ikkunan-aloitus-korkeus]
  (let [tila (atom {:ikkunan-korkeus ikkunan-aloitus-korkeus})
        muuta-korkeutta (fn [event]
                          (.stopPropagation event)
                          (.preventDefault event)
                          (let [{:keys [drag-menossa? viime-y ikkunan-korkeus]} @tila]
                            (when drag-menossa?
                              (let [y (.-screenY event)
                                    ikkunan-korkeus (if viime-y
                                                      (+ ikkunan-korkeus
                                                         (- viime-y y))
                                                      ikkunan-korkeus)]
                                (ikkunan-korkeus-fn! ikkunan-korkeus)
                                (swap! tila assoc
                                       :ikkunan-korkeus ikkunan-korkeus
                                       :viime-y y)))))
        alusta-drag (fn [event]
                      (.stopPropagation event)
                      (.preventDefault event)
                      (swap! tila assoc :drag-menossa? true)
                      ;; Firefoxi tarvii tällaisen alustuksen
                      ;; https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API#Identify_what_is_draggable
                      #_(-> event
                            .-dataTransfer
                            (.setData "text/plain" (.. event -target -innerText))))
        drag-lopeuts (fn [event]
                       (.stopPropagation event)
                       (.preventDefault event)
                       (swap! tila assoc :drag-menossa? false))]
    (fn [ikkunan-korkeus-fn! ikkunan-aloitus-korkeus]
      (let [{:keys [drag-menossa?]} @tila]
        ;; Wrappia sen takia, että saadaan tuolle varsinaiselle napille iso laatikko css:n avulla
        ;; Se iso laatikko on hyvä tuon dragin kannalta, kun hiirtä liikuttaa nopeasti.
        ;; Toimii vain jos vanhempi komponentti ei piilota tuota laatikkoa, esim. overflow: scroll tai hidden
        ;; avulla.
        [:div {:style {:position "relative"
                       :z-index "99999999999"}}
         (if-not drag-menossa?
           ^{:key :drag-ei-menossa}
           [:span.drag-napin-wrapper {:style {:height "40px"
                                              :width "15px"
                                              :cursor "grab"}
                                      :on-mouse-down alusta-drag}
            ;; Firefoxissa on-drag ei oikein toimi, joten käytetään on-mouse-move:a
            [:span.debug-korkeus-nappi {:style {:display (if drag-menossa?
                                                           "none"
                                                           "inline-block")}}
             "="]]
           ^{:key :drag-menossa}
           [:span.drag-napin-wrapper {:style {:height "40px"
                                              :width "15px"}}
            ;; Firefoxissa on-drag ei oikein toimi, joten käytetään on-mouse-move:a
            [:span.debug-korkeus-nappi.aktiivinen {:on-mouse-move muuta-korkeutta
                                                   :on-mouse-up drag-lopeuts
                                                   :on-mouse-down (fn [event]
                                                                    (.stopPropagation event))
                                                   :style {:cursor (if drag-menossa?
                                                                     "grabbing"
                                                                     "default")
                                                           :display (if drag-menossa?
                                                                      "inline-block"
                                                                      "none")}}
             "="]])]))))

(defn vanhempi-komponentti
  ([asia sisennys] [vanhempi-komponentti asia asia sisennys true])
  ([taulukko asia sisennys vanhempi-auki?]
   (let [auki? (atom {:tiedot false
                      :lapset false})
         nappia-klikattu (fn [avain event]
                           (swap! auki? update avain not))]
     (fn [taulukko asia sisennys vanhempi-auki?]
       (let [lapset-auki? (and (:lapset @auki?) vanhempi-auki?)
             tiedot-auki? (and (:tiedot @auki?) vanhempi-auki?)]
         [:span.asia {:style {:padding-left (str sisennys "px")}}
          (when vanhempi-auki?
            [:<>
             [:span {:on-mouse-over (r/partial indikoi-asia! taulukko asia)
                     :on-mouse-leave (r/partial deindikoi-asia! taulukko asia)}
              (print-str (type asia))]
             [:span.asian-arvot
              [nappi "Tiedot" tiedot-auki? (r/partial nappia-klikattu :tiedot)]
              (when tiedot-auki?
                [piirra-asia (if (satisfies? p/Asia asia)
                               (p/paivita-arvo asia :lapset
                                               (fn [_]
                                                 "<<EI NÄYTETÄ TÄSSÄ>>"))
                               asia)])
              [nappi "Lapset" lapset-auki? (r/partial nappia-klikattu :lapset)]
              (when lapset-auki?
                ^{:key :lapsi-komponentti}
                [lapsi-komponentti taulukko asia (+ sisennys 10) (:lapset @auki?)])]])])))))

(defn kasittele-diff
  ([vanha-taulukko uusi-taulukko] [(flatten (kasittele-diff vanha-taulukko uusi-taulukko []))
                                   (flatten (kasittele-diff uusi-taulukko vanha-taulukko []))])
  ([vanha uusi polku]
   (try
     (let [muutoksia? (fn [x y]
                        (let [[muutokset-x muutokset-y _] (data/diff x y)]
                          (or (not (nil? muutokset-y))
                              (not (nil? muutokset-x)))))
           map-diff (fn [m1 m2]
                      (keep (fn [[avain arvo]]
                              (let [arvo-uudesta (get m2 avain)]
                                (when (muutoksia? arvo arvo-uudesta)
                                  (kasittele-diff arvo arvo-uudesta (conj polku avain)))))
                            m1))
           vector-diff (fn [v1 v2]
                         (let [v1-pituus (count v1)
                               v2-pituus (count v2)]
                           (into []
                                 (concat
                                   (keep-indexed (fn [index a1]
                                                   (let [a2 (when (> v2-pituus index)
                                                              (get v2 index))]
                                                     (when (and a2 (muutoksia? a1 a2))
                                                       (kasittele-diff a1 a2 (conj polku index)))))
                                                 v1)
                                   (if (< v2-pituus v1-pituus)
                                     (map (fn [tiputettu-asia]
                                            {:polku polku
                                             :diff tiputettu-asia})
                                          (drop v1 v2-pituus))
                                     [])))))]
       (cond
         (and (map? vanha) (map? uusi)) (map-diff vanha uusi)
         (and (vector? vanha) (vector? uusi)) (vector-diff vanha uusi)
         :else {:polku polku
                :diff (first (data/diff vanha uusi))}))
     (catch :default e
       (println "error debug taulussa")
       {:polku polku
        :diff nil}))))

(defn taulukko-diff [vanha uusi]
  (kasittele-diff vanha uusi))

(defn taulukko-log-muutos! [tila vaha-taulukko uusi-taulukko]
  (go (let [[poistot lisaykset] (taulukko-diff vaha-taulukko uusi-taulukko)
            poistoissa-muutoksia? (not (empty? poistot))
            lisayksissa-muutoksia? (not (empty? lisaykset))]
        (when (or poistoissa-muutoksia?
                  lisayksissa-muutoksia?)
          (swap! tila update :loki (fn [{:keys [jarjestys] :as vanha}]
                                     (-> vanha
                                         (update :jarjestys inc)
                                         (update :lokit (fn [lokit]
                                                          (into []
                                                                (take 50
                                                                      (cons {:jarjestys jarjestys
                                                                             :poistot (when poistoissa-muutoksia?
                                                                                        poistot)
                                                                             :lisaykset (when lisayksissa-muutoksia?
                                                                                          lisaykset)}
                                                                            lokit))))))))))))

(defn taulukko-log [tila ikkunan-korkeus]
  (let [ikkunan-aloitus-korkeus 100
        ikkunan-korkeus-fn! (fn [korkeus]
                             (swap! tila assoc-in [:loki :korkeus] korkeus))]
    (fn [tila ikkunan-korkeus]
      (let [{{:keys [lokit alhaalla? korkeus]} :loki} @tila]
        (when-not alhaalla?
          [:div.taulukko-debug-log-sisalto {:style {:height (str korkeus "px")
                                                    :bottom (str ikkunan-korkeus "px")}}
           [:span.veto-nappi
            [drag-nappi ikkunan-korkeus-fn! ikkunan-aloitus-korkeus]]
           [:div {:style {:overflow-y "scroll"}}
            [:div.taulukko-debug-log
             (for [{:keys [jarjestys poistot lisaykset]} (sort-by :jarjestys lokit)]
               ^{:key jarjestys}
               [:div.loki
                [:<>
                 (for [{:keys [polku diff]} poistot]
                   ^{:key polku}
                   [:span.poistot
                    [:span (str polku)]
                    [:span (str diff)]])]
                [:<>
                 (for [{:keys [polku diff]} lisaykset]
                   ^{:key polku}
                   [:span.lisaykset
                    [:span (str polku)]
                    [:span (str diff)]])]])]]])))))

(defn debug [taulukko]
  {:pre [#(or (nil? %)
              (satisfies? p/Taulukko %))]}
  (p/paivita-taulukko! (p/paivita-arvo taulukko
                                       :class
                                       (fn [luokat]
                                         (if (nil? luokat)
                                           #{"taulukko-debug-paalla"}
                                           (conj luokat "taulukko-debug-paalla")))))
  (let [perus-korkeus (js/parseInt (* 25 (/ (.-innerHeight js/window)
                                            100)))
        tila (atom {:alhaalla? false
                    :paalla? false
                    :ikkunan-korkeus perus-korkeus
                    :viime-y nil
                    :drag-menossa? false
                    :loki {:alhaalla? true
                           :jarjestys 0
                           :lokit []
                           :korkeus 100}})
        sammuta (fn [_]
                  (reset! aktiivinen-taulukko-debug nil)
                  (swap! tila assoc
                         :paalla? false
                         :alhaalla? true))
        paalle (fn [taulukko _]
                 (reset! aktiivinen-taulukko-debug taulukko)
                 (swap! tila assoc
                        :paalla? true
                        :alhaalla? false))
        alas-toggle (fn [_]
                      (swap! tila update :alhaalla? not))
        toggle-loki (fn [_]
                      (println "TOGGLE loki")
                      (swap! tila update-in [:loki :alhaalla?] not))
        box-shadow-pois (fn [taulukko _]
                          (println "BOX SHADOW POIS")
                          (p/paivita-taulukko! (p/paivita-arvo taulukko
                                                               :class
                                                               (fn [luokat]
                                                                 (if (nil? luokat)
                                                                   #{"taulukko-debug-paalla-debug-sisalto"}
                                                                   (conj luokat "taulukko-debug-paalla-debug-sisalto"))))))
        box-shadow-paalle (fn [taulukko _]
                            (println "BOX SHADOW PÄÄLLE")
                            (p/paivita-taulukko! (p/paivita-arvo taulukko
                                                                 :class
                                                                 (fn [luokat]
                                                                   (disj luokat "taulukko-debug-paalla-debug-sisalto")))))
        ikkunan-korkeus-fn! (fn [korkeus]
                             (swap! tila assoc :ikkunan-korkeus korkeus))]
    (r/create-class
      {:display-name "Taulukko-debug"
       :should-component-update (fn [_ vanhat uudet]
                                  ;; Tässä ollaan kiinnostuttu vain logituksesta, ei niinkään siitä, että tarviiko päivittää näkymä
                                  (taulukko-log-muutos! tila (last vanhat) (last uudet))
                                  (not= vanhat uudet))
       :reagent-render
       (fn [taulukko]
         (let [{:keys [paalla? alhaalla? ikkunan-korkeus drag-menossa?]} @tila]
           [:div.taulukko-debug
            [:button.debug-nappi {:on-click (r/partial paalle taulukko)} "Taulukon debug"]
            (when (and taulukko
                       @aktiivinen-taulukko-debug
                       (p/taulukon-id? taulukko
                                       (p/taulukon-id @aktiivinen-taulukko-debug))
                       paalla?)
              ^{:key :taulukko-debug-sisalto}
              [:div.taulukon-debug-sisalto
               {:class (if alhaalla?
                         "debug-sisalto-alhaalla"
                         "debug-sisalto-ylhaalla")
                :style {:height (str ikkunan-korkeus "px")}
                ;; on-mouse-leave ja on-mouse-over lapsikomponenteissa ei toimi
                ;; jos nämä on käytössä
                #_#_:on-mouse-leave (r/partial box-shadow-paalle taulukko)
                #_#_:on-mouse-over (r/partial box-shadow-pois taulukko)}
               [:div.debug-nappiryhma
                [:div.rivi.rivi-tasaisin-valein
                 [:button.debug-toggle-nappi {:on-click alas-toggle}
                  (if alhaalla?
                    ^{:key :debug-alhaalla}
                    [ikonit/livicon-chevron-up]
                    ^{:key :debug-ylhaalla}
                    [ikonit/livicon-chevron-down])]

                 [drag-nappi ikkunan-korkeus-fn! perus-korkeus]

                 [:button.debug-sulje-nappi {:on-click sammuta}
                  [ikonit/remove-circle]]]
                [:div.rivi
                 [:div.rivi.rivi-irti
                  [:button.debug-log-nappi {:on-click toggle-loki}
                   [ikonit/list-alt]]]]]
               [taulukko-log tila ikkunan-korkeus]
               (when-not alhaalla?
                 ^{:key :taulukko-debug}
                 [:div.taulukko-debug-taulukko {:style {:height (str (- ikkunan-korkeus 60) "px")}}
                  [vanhempi-komponentti taulukko 0]])])]))})))