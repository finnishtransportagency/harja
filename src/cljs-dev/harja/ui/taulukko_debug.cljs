(ns harja.ui.taulukko-debug
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.ikonit :as ikonit]))

(goog-define ^boolean TAULUKKO_DEBUG true)

(defonce aktiivinen-taulukko-debug (atom nil))

(defprotocol AsianDebugArvo
  (piirra [this]))

(defn nayta-kaikki-arvot [a _]
  (swap! a not))

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
                    (take max-pituus lista))]
        [:span.lista
         [:b alku-merkki]
         [:span {:class (if avain-arvo?
                          "mapin-arvot"
                          "listan-arvot")}
          (doall
            (concat
              (drop-last 1
                         (map-indexed (fn [index arvo]
                                        ^{:key index}
                                        [piirra-asia arvo])
                                      lista))
              [(if (and liian-pitka? (not @nayta-kaikki?))
                 ^{:key (str (count lista) "-truncated")}
                 [:<>
                  [piirra-asia (last lista)]
                  [:span.viimeinen-arvo
                   [:span {:on-click (r/partial nayta-kaikki-arvot nayta-kaikki?)} "..."]
                   [:b.sulku-merkki loppu-merkki]]]
                 ^{:key (str (count lista) "-kaikki")}
                 [:<>
                  [:span.viimeinen-arvo
                   [piirra-asia (last lista)]
                   [:b.sulku-merkki loppu-merkki]]])]))]]))))

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

(defn nappi [asia auki? nappia-klikattu-fn]
  [:button.asian-nappi {:on-click nappia-klikattu-fn}
   [:div
    (if auki?
      ^{:key :asia-auki}
      [ikonit/livicon-chevron-down]
      ^{:key :asia-kiinni}
      [ikonit/livicon-arrow-right])
    [:span (print-str (type asia))]]])

(defn piirra-lehti [asia sisennys vanhempi-auki?]
  (let [auki? (atom false)
        nappia-klikattu (fn [event]
                          (println "NAPPIA KLIKATTU")
                          (swap! auki? not))]
    (fn [asia sisennys vanhempi-auki?]
      [:span.lehti {:style {:padding-left sisennys}}
       [nappi asia (and @auki? vanhempi-auki?) nappia-klikattu]
       [:span.asian-arvot
        [piirra-asia asia]]])))

(defn lapsi-komponentti [asia sisennys vanhempi-auki?]
  [:<>
   (if-let [lapset (and (satisfies? p/Asia asia) (p/arvo asia :lapset))]
     (for [lapsi lapset
           :let [id (cond
                      (satisfies? p/Jana lapsi) (p/janan-id lapsi)
                      (satisfies? p/Osa lapsi) (p/osan-id lapsi))]]
       ^{:key id}
       [vanhempi-komponentti lapsi sisennys vanhempi-auki?])
     [piirra-lehti asia sisennys vanhempi-auki?])])

(defn vanhempi-komponentti
  ([asia sisennys] [vanhempi-komponentti asia sisennys true])
  ([asia sisennys vanhempi-auki?]
   (let [auki? (atom false)
         nappia-klikattu (fn [event]
                           (println "NAPPIA KLIKATTU")
                           (swap! auki? not))]
     (fn [asia sisennys vanhempi-auki?]
       (let [pidentaan-auki? (and @auki? vanhempi-auki?)]
         [:span.asia {:style {:padding-left (str sisennys "px")}}
          [nappi asia pidentaan-auki? nappia-klikattu]
          [:span.asian-arvot
           [piirra-asia (if (satisfies? p/Asia asia)
                          (p/paivita-arvo asia :lapset
                                          (fn [_]
                                            "<<EI NÄYTETÄ TÄSSÄ>>"))
                          asia)]
           (when pidentaan-auki?
             ^{:key :lapsi-komponentti}
             [lapsi-komponentti asia (+ sisennys 10) @auki?])]])))))

(defn debug [taulukko]
  {:pre [#(or (nil? %)
              (satisfies? p/Taulukko %))]}
  (let [perus-korkeus (js/parseInt (* 25 (/ (.-innerHeight js/window)
                                            100)))
        tila (atom {:alhaalla? false
                    :paalla? false
                    :ikkunan-korkeus perus-korkeus
                    :viime-y nil
                    :drag-menossa? false})
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
        muuta-korkeutta (fn [event]
                          (let [{:keys [drag-menossa? viime-y ikkunan-korkeus]} @tila]
                            (when drag-menossa?
                              (.preventDefault event)
                              (println "ENTINEN KORKEUS " ikkunan-korkeus)
                              (js/console.log event)
                              (let [y (.-screenY event)
                                    _ (println "Y: " y)
                                    ikkunan-korkeus (if viime-y
                                                      (do
                                                        (println "KORKEUDEN MUUTOS: " (- y viime-y))
                                                        (+ ikkunan-korkeus
                                                           (- viime-y y)))
                                                      ikkunan-korkeus)]
                                (println "IKKUNAN KORKEUS" ikkunan-korkeus)
                                (swap! tila assoc
                                       :ikkunan-korkeus ikkunan-korkeus
                                       :viime-y y)))))
        alusta-drag (fn [event]
                      (swap! tila assoc :drag-menossa? true)
                      ;; Firefoxi tarvii tällaisen alustuksen
                      ;; https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API#Identify_what_is_draggable
                      #_(-> event
                          .-dataTransfer
                          (.setData "text/plain" (.. event -target -innerText))))
        drag-lopeuts (fn [event]
                       (println "DRAG LOPETUS")
                       (swap! tila assoc :drag-menossa? false))
        nappien-zindex "-3"]
    (fn [taulukko]
      (println "RENDERING ")
      (println "PERUS KORKEUS: " perus-korkeus)
      (let [{:keys [paalla? alhaalla? ikkunan-korkeus drag-menossa?]} @tila]
        [:div.taulukko-debug
         [:button.debug-nappi {:on-click (r/partial paalle taulukko)} "Taulukon debug"]
         (when (and (= taulukko @aktiivinen-taulukko-debug)
                    paalla?)
           ^{:key :taulukko-debug-sisalto}
           [:div.taulukon-debug-sisalto
            {:class (if alhaalla?
                      "debug-sisalto-alhaalla"
                      "debug-sisalto-ylhaalla")
             :style {:height (str ikkunan-korkeus "px")}}
            [:div.debug-nappiryhma {:style {:z-index nappien-zindex}}
             [:button.debug-toggle-nappi {:on-click alas-toggle}
              (if alhaalla?
                ^{:key :debug-alhaalla}
                [ikonit/livicon-chevron-up]
                ^{:key :debug-ylhaalla}
                [ikonit/livicon-chevron-down])]

             ;; Firefoxissa on-drag ei oikein toimi, joten käytetään on-mouse-move:a
             [:span.debug-korkeus-nappi {:on-mouse-move muuta-korkeutta
                                         :on-mouse-down alusta-drag
                                         :on-mouse-up drag-lopeuts
                                         :style {:z-index (if drag-menossa?
                                                            "9999999"
                                                            nappien-zindex)} ;
                                         #_#_:on-drag-start alusta-drag
                                         #_#_:on-drag-end drag-lopeuts
                                         #_#_:draggable true}
              "="]

             [:button.debug-sulje-nappi {:on-click sammuta}
              [ikonit/remove-circle]]]
            (when-not alhaalla?
              ^{:key :taulukko-debug}
              [vanhempi-komponentti taulukko 0])])]))))