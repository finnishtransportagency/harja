(ns harja.ui.taulukko.impl.grid
  (:require [harja.ui.taulukko.protokollat.grid :as p]
            [harja.ui.taulukko.protokollat.solu :as sp]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]
            [harja.ui.taulukko.impl.datan-kasittely :as dk]
            [harja.virhekasittely :as virhekasittely]
            [harja.loki :refer [log warn]]
            [cljs.spec.alpha :as s]
            [clojure.string :as clj-str]
            [reagent.core :as r]
            [harja.ui.grid-debug :as g-debug]
            [reagent.ratom :as ratom])
  (:require-macros [reagent.ratom :refer [reaction]]))


;; TODO tarkenna string? arvot
;(s/def ::leveydet (s/map-of pos-int? string?))
;(s/def ::oletus-leveys string?)
;(s/def ::oletus-korkeus string?)
;(s/def ::sarake (s/keys :req-un [::oletus-leveys ::leveydet]))
;(s/def ::rivi (s/keys :req-un [::oletus-korkeus ::korkeudet]))
;
;(s/def ::koko (s/keys :req-un [::sarake ::rivi]))


(s/def ::alueet any?)
(s/def ::koko any?)
(s/def ::jarjestys any?)
(s/def ::osat any?)

(defonce oletus-koko {:sarake {:oletus-leveys "1fr"}
                      :rivi {:oletus-korkeus "1fr"}})

(def ^:dynamic *ajetaan-tapahtuma?* true)
(def ^:dynamic *jarjesta-data?* true)

(defprotocol ISeuranta
  (seuranta-derefable! [this polku])
  (poista-seuranta-derefable! [this]))

(defrecord Seuranta [r ^:mutable n]
  ISeuranta
  (seuranta-derefable! [this polku]
    (set! n (inc n))
    (r/cursor (:r this) polku))
  (poista-seuranta-derefable! [this]
    (set! n (dec n))
    (when (= 0 n)
      (println "--- DISPOSATAAN ---")
      (cljs.pprint/pprint (:r this))
      (r/dispose! r))
    this)
  IDeref
  (-deref [this]
    @(:r this)))

(defn seuranta [r]
  {:pre [(instance? ratom/Reaction r)]
   :post [(instance? Seuranta %)]}
  (->Seuranta r 0))

(defn get-in-grid [osa [polun-osa & polku]]
  (let [valitse-osa (fn [osat]
                      (cond
                        (integer? polun-osa) (get osat polun-osa)
                        (symbol? polun-osa) (some #(when (= (gop/id %) polun-osa)
                                                     %)
                                                  osat)
                        :else (some #(when (= (gop/nimi %) polun-osa)
                                       %)
                                    osat)))
        loydetty-osa (when-not (nil? polun-osa)
                       (valitse-osa (p/lapset osa)))]
    (if (nil? polun-osa)
      osa
      (recur loydetty-osa polku))))

(defn- laske-gridin-css-tiedot [grid]
  (let [grid-koko (p/koko grid)
        gridin-alueet (p/alueet grid)
        pienempi (fn [x y]
                   (if (< x y) x y))
        suurempi (fn [x y]
                   (if (> x y) x y))
        ;; gridin-alueet -> [alue ..] -> alue = {:sarakkeet [int int] :rivit [int int]}

        ;; sarakkeet ja rivit tässä oikeasti reunoja/viivoja, ei niinkään varsinaisia grid trackejä
        [aloitus-sarake lopetus-sarake
         aloitus-rivi lopetus-rivi] (reduce (fn [[min-sarake max-sarake min-rivi max-rivi]
                                                 {[sarake-alku sarake-loppu] :sarakkeet
                                                  [rivi-alku rivi-loppu] :rivit}]
                                              [(pienempi min-sarake sarake-alku)
                                               (suurempi max-sarake sarake-loppu)
                                               (pienempi min-rivi rivi-alku)
                                               (suurempi max-rivi rivi-loppu)])
                                            [0 0 0 0]
                                            gridin-alueet)
        [css-aloitus-sarake css-lopetus-sarake
         css-aloitus-rivi css-lopetus-rivi] [(inc (pienempi 0 aloitus-sarake))
                                             (inc (- lopetus-sarake aloitus-sarake))
                                             (inc (pienempi 0 aloitus-rivi))
                                             (inc (- lopetus-rivi aloitus-rivi))]
        #_#__ (println "---------------")
        #_#__ (println "NIMI: " (gop/nimi grid))
        #_#__ (println "KOKO: " grid-koko)
        #_#__ (when (nil? grid-koko) (println ((::koko-fn grid))))
        #_#__ (println "CSS ARVOT")
        #_#__ (cljs.pprint/pprint [css-aloitus-sarake css-lopetus-sarake
                                   css-aloitus-rivi css-lopetus-rivi])
        sarake-leveydet (map (fn [sarake-track-numero]
                               (if-let [maaritetty-leveys (get-in grid-koko [:sarake :leveydet (dec sarake-track-numero)])]
                                 maaritetty-leveys
                                 (get-in grid-koko [:sarake :oletus-leveys])))
                             (range css-aloitus-sarake css-lopetus-sarake))
        rivi-korkeudet (map (fn [rivi-track-numero]
                              (if-let [maaritetty-korkeus (get-in grid-koko [:rivi :korkeudet (dec rivi-track-numero)])]
                                maaritetty-korkeus
                                (get-in grid-koko [:rivi :oletus-korkeus])))
                            (range css-aloitus-rivi css-lopetus-rivi))
        #_#__ (println "SARAKE LEVEYDET")
        #_#__ (cljs.pprint/pprint sarake-leveydet)
        #_#__ (println "RIVI KORKEUDET")
        #_#__ (cljs.pprint/pprint rivi-korkeudet)
        [css-top css-left] [(if (< aloitus-rivi 0)
                              (take (js/Math.abs aloitus-rivi) rivi-korkeudet)
                              ["0px"])
                            (if (< aloitus-sarake 0)
                              (take (js/Math.abs aloitus-sarake) sarake-leveydet)
                              ["0px"])]]
    {:korkeudet (clj-str/join " " rivi-korkeudet)
     :leveydet (clj-str/join " " sarake-leveydet)
     :top (str "calc(" (clj-str/join " + " css-top) ")")
     :left (str "calc(" (clj-str/join " + " css-left) ")")}))

(defn- oikea-osa? [osa etsittava-osa]
  (let [id? (symbol? etsittava-osa)]
    (if id?
      (gop/id? osa etsittava-osa)
      (= (gop/nimi osa) etsittava-osa))))

(defn- etsi-osa
  ([osa etsittavan-osan-tunniste]
   (if (oikea-osa? osa etsittavan-osan-tunniste)
     osa
     (let [kaikki-osan-lapset (remove nil? (p/lapset osa))]
       (etsi-osa osa etsittavan-osan-tunniste kaikki-osan-lapset))))
  ([_ etsittavan-osan-tunniste lapset]
   (let [oikea-osa (some (fn [osa]
                           (when (oikea-osa? osa etsittavan-osan-tunniste)
                             osa))
                         lapset)]
     (if (or oikea-osa (empty? lapset))
       oikea-osa
       (let [kaikki-lapsen-lapset (sequence (comp (mapcat p/lapset)
                                                  (remove nil?))
                                            lapset)]
         (recur nil etsittavan-osan-tunniste kaikki-lapsen-lapset))))))

(defn- gridin-osat-vektoriin
  ([grid pred f]
   (let [kaikki-gridin-lapset (p/lapset grid)
         aloitus (if (pred grid)
                   [(f grid)]
                   [])]
     (gridin-osat-vektoriin aloitus pred f kaikki-gridin-lapset)))
  ([osat pred f lapset]
   (let [kaikki-lapsen-lapset (sequence (comp (mapcat p/lapset)
                                              (remove nil?))
                                        lapset)
         oikeat-osat (reduce (fn [osat osa]
                               (if (pred osa)
                                 (conj osat (f osa))
                                 osat))
                             []
                             lapset)]
     (if (empty? lapset)
       (vec osat)
       (recur (concat osat oikeat-osat) pred f kaikki-lapsen-lapset)))))

(defn paivita-kaikki-lapset!
  [osa pred f]
  (let [paivitetyt-lapset (transduce (comp
                                       (map (fn [osa]
                                              [osa (pred osa)]))
                                       (map (fn [[lapsi ajetaan-f?]]
                                              (when (satisfies? p/IGrid lapsi)
                                                (paivita-kaikki-lapset! lapsi pred f))
                                              (if ajetaan-f?
                                                (f lapsi)
                                                lapsi))))
                                     conj
                                     []
                                     (p/lapset osa))]
    (p/aseta-lapset! osa paivitetyt-lapset)))

(defn pre-walk-grid! [grid f!]
  (f! grid)
  (doseq [lapsi (p/lapset grid)]
    (if (satisfies? p/IGrid lapsi)
      (pre-walk-grid! lapsi f!)
      (f! lapsi))))

(defn post-walk-grid! [grid f!]
  (doseq [lapsi (p/lapset grid)]
    (if (satisfies? p/IGrid lapsi)
      (post-walk-grid! lapsi f!)
      (f! lapsi)))
  (f! grid)
  nil)

(defn root [osa]
  ((::root-fn osa)))

(defn paivita-root! [osa f]
  ((::paivita-root! osa) f)
  (root osa))

(defn vanhempi [osa]
  (get-in-grid (root osa) (vec (drop-last (::index-polku osa)))))


(defn aseta-seurattava-koko! [grid seurattava-id]
  ;;{:seurattava :otsikko
  ;  :sarakkeet <:sama | {:foo []} | {:foo int}>
  ;  :rivit :sama}
  ;(println "KOOT: " )
  ;(cljs.pprint/pprint (p/koot grid))
  (let [koko-conf (p/koko grid)
        #_#_{seurattavan-koon-nimi :seurattava
             seurattava-sarakkeet :sarakkeet
             seurattava-rivit :rivit} (get koko-conf :seuraa)
        seurattava-koko (get @((::koko-fn grid)) seurattava-id)
        muodosta-css-arvot-seurattavasta (fn [seurattavan-css-arvo suhdeluvut]
                                           (let [luku (js/Number (first (re-find #"[0-9]*(\.[0-9]+)?" seurattavan-css-arvo)))
                                                 yksikko (re-find #"[^0-9\.]*$" seurattavan-css-arvo)
                                                 suhdeluvut-oikein? (fn [suhdeluvut]
                                                                      (= 100 (apply + suhdeluvut)))
                                                 _ (when-not (suhdeluvut-oikein? suhdeluvut)
                                                     (warn (str "Suhdelukujen summa ei ole 100 gridille"
                                                                (when-let [gridin-nimi (gop/nimi grid)]
                                                                  (str " " gridin-nimi)))))
                                                 suhdeluvut (loop [suhdeluvut suhdeluvut]
                                                              (if (suhdeluvut-oikein? suhdeluvut)
                                                                suhdeluvut
                                                                (let [viimeisin-poistettu (vec (butlast suhdeluvut))
                                                                      summatut-suhdeluvut (apply + viimeisin-poistettu)
                                                                      alle-sata? (> 100 summatut-suhdeluvut)
                                                                      muokatut (if alle-sata?
                                                                                 (conj viimeisin-poistettu (- 100 summatut-suhdeluvut))
                                                                                 viimeisin-poistettu)]
                                                                  (recur muokatut))))]
                                             (mapv (fn [suhdeluku]
                                                     (str (/ luku suhdeluku) yksikko))
                                                   suhdeluvut)))
        kasittele-asetukset (fn [asetukset leveydet-tai-korkeudet seuraajan-asetukset]
                              (let [seuraajan-asetukset-suunnalle (get seuraajan-asetukset
                                                                       (if (= :leveydet leveydet-tai-korkeudet)
                                                                         :sarakkeet
                                                                         :rivit))]
                                (if (= :sama seuraajan-asetukset-suunnalle)
                                  asetukset
                                  (let [nimet (get-in asetukset
                                                      [(if (= :leveydet leveydet-tai-korkeudet)
                                                         :sarake
                                                         :rivi)
                                                       :nimet])
                                        seuraajalla-useampi-solu? (vector? (val (first seuraajan-asetukset-suunnalle)))
                                        seuraajalla-vahemman-soluja? (integer? (val (first seuraajan-asetukset-suunnalle)))]
                                    (cond
                                      seuraajalla-useampi-solu? (update asetukset
                                                                        leveydet-tai-korkeudet
                                                                        (fn [leveydet-tai-korkeudet]
                                                                          ;; Seurattavan conf
                                                                          ;{:sarake {:leveydet {0 "3px"
                                                                          ;                     1 "3px"
                                                                          ;                     2 "5px"
                                                                          ;                     3 "3px"}
                                                                          ;          :nimet {:foo 1
                                                                          ;                  :bar 2}}}
                                                                          ;; seuraajan conf
                                                                          ;{:seuraa {:sarakkeet {:foo [30 70]
                                                                          ;                      :bar [80 20]}}
                                                                          (let [numerosta-suhteet (into {}
                                                                                                        (map (fn [[nimi suhdeluvut]]
                                                                                                               [(get nimet nimi) suhdeluvut])
                                                                                                             seuraajan-asetukset-suunnalle))]
                                                                            (reduce (fn [[seuraava-index seuraavan-osan-mitat] [i seurattavan-css-arvo]]
                                                                                      (if-let [suhdeluvut (get numerosta-suhteet i)]
                                                                                        (let [lasketut-css-arvot (muodosta-css-arvot-seurattavasta seurattavan-css-arvo suhdeluvut)
                                                                                              seuraavan-iteraation-index (inc (count lasketut-css-arvot))]
                                                                                          [seuraavan-iteraation-index
                                                                                           (merge seuraavan-osan-mitat
                                                                                                  (zipmap (range seuraava-index seuraavan-iteraation-index)
                                                                                                          lasketut-css-arvot))])
                                                                                        [(inc seuraava-index) (assoc seuraavan-osan-mitat seuraava-index seurattavan-css-arvo)]))
                                                                                    [0 {}]
                                                                                    (sort-by key leveydet-tai-korkeudet)))))
                                      ;; TODO toteuta tämä
                                      seuraajalla-vahemman-soluja? asetukset)))))
        koko (-> (merge seurattava-koko
                        koko-conf)
                 (update :sarake kasittele-asetukset :leveydet (get koko-conf :seuraa))
                 (update :rivi kasittele-asetukset :korkeudet (get koko-conf :seuraa)))]
    ;(println (str "GRIDIN " (gop/nimi grid) "(" (gop/id grid) ") koko konf: " koko-conf " ja laskettu koko: " koko))
    (p/aseta-koko! grid koko)))

(defn aseta-koko! [grid]
  ;:koko {<:sarake {:oletus-leveys "5px"
  ;                 :leveydet {1 "3px"}
  ;                 <:nimet {:foo 1}>}
  ;       :rivi {:oletus-korkeus "3px"
  ;              :korkeudet {2 "3px"}}>
  ;       <:class "foo">}
  {:pre [(->> grid p/koko (s/valid? ::koko))]}
  (let [kasittele-grid-arvo (fn [maarritelma suunta selector]
                              (if (nil? maarritelma)
                                (warn "css määrritelmä gridin"
                                      (when-let [gridin-nimi (gop/nimi grid)]
                                        (str " " gridin-nimi))
                                      " " (name suunta) " arvoksi"
                                      " ei oltu määritetty selectorilla "
                                      selector "."
                                      " Käytä "
                                      (if (= suunta :sarake)
                                        "grid-template-columns"
                                        "grid-template-rows")
                                      " arvoa.")
                                ;;TODO käsittele muutkin vaithoehdot kuin "arvo arvo arvo .."
                                (let [grid-arvot (clj-str/split maarritelma #" ")]
                                  (into {}
                                        (map-indexed (fn [index arvo]
                                                       [index arvo])
                                                     grid-arvot)))))
        css-luokat->css-arvot (fn [luokka]
                                (let [grid-id (gop/id grid)]
                                  (loop [[style-sheet & style-sheets] (js->clj (.-styleSheets js/document))
                                         rule ""]
                                    (if (nil? style-sheet)
                                      rule
                                      (let [all-css-rules (js->clj (or (.-cssRules style-sheet)
                                                                       (.-rules style-sheet)))
                                            rule (loop [[css-rule & css-rules] all-css-rules
                                                        rules []]
                                                   (if (nil? css-rule)
                                                     rule
                                                     ;; TODO käsittele muunkin tyyppisiä sääntöjä
                                                     (do (when-not (= (.-type css-rule) 1)
                                                           (log (str "css tiedosto "
                                                                     (.-title style-sheet)
                                                                     " sisältää säännön "
                                                                     (.-cssText css-rule)
                                                                     ", jonka tyyppi on "
                                                                     (.-type css-rule)
                                                                     " eikä sitä sen vuoksi käsitellä gridin kokoa asettaessa.")))
                                                         (let [selector (.-selectorText css-rule)
                                                               luokka-re (re-pattern (str "." luokka))
                                                               oikea-selector? (re-find luokka-re selector)]
                                                           (if oikea-selector?
                                                             (recur css-rules
                                                                    rules)
                                                             (let [uusi-selector (if oikea-selector?
                                                                                   (conj rules (clj-str/replace selector luokka-re (str "#" grid-id)))
                                                                                   rules)
                                                                   css-maarritelmat (js->clj (.-style css-rule))
                                                                   rule (-> oletus-koko
                                                                            (assoc-in [:sarake :leveydet] (kasittele-grid-arvo (:grid-template-columns css-maarritelmat) :sarake selector))
                                                                            (assoc-in [:rivi :korkeudet] (kasittele-grid-arvo (:grid-template-rows css-maarritelmat) :rivi selector)))]
                                                               (recur css-rules
                                                                      (conj rules
                                                                            (with-meta rule
                                                                                       {:selector uusi-selector})))))))))])))))
        koko-conf->koko (fn [koko-conf]
                          (if (s/valid? ::koko koko-conf)
                            (select-keys koko-conf #{:sarake :rivi})
                            (css-luokat->css-arvot (:class koko-conf))))
        koko-conf (p/koko grid)
        koko (if (nil? koko-conf)
               (do
                 (warn (str "Kokoa ei annettu gridille"
                            (when-let [gridin-nimi (gop/nimi grid)]
                              (str " " gridin-nimi))
                            ". Käytetään oletusarvoja."))
                 oletus-koko)
               (koko-conf->koko koko-conf))]
    ;(println (str (gop/nimi grid) " gridin koko on " koko))
    (p/aseta-koko! grid koko)))

(defn aseta-koot! [root-grid]
  (let [kokojen-seuranta (gridin-osat-vektoriin root-grid
                                                #(satisfies? p/IGrid %)
                                                (fn [grid]
                                                  (let [koko (p/koko grid)
                                                        seurattavan-gridin-nimi (get-in koko [:seuraa :seurattava])
                                                        seurattava-grid (if seurattavan-gridin-nimi
                                                                          (etsi-osa ((::root-fn grid)) seurattavan-gridin-nimi)
                                                                          grid)]
                                                    [grid [(gop/id grid) (gop/id seurattava-grid)]])))
        gridit-jarjestetty (loop [jarjestetyt-gridit (filterv (fn [[grid [id seurattavan-id]]]
                                                                (= id seurattavan-id))
                                                              kokojen-seuranta)
                                  jarjestettavat-gridit (remove (fn [[grid [id seurattavan-id]]]
                                                                  (= id seurattavan-id))
                                                                kokojen-seuranta)]
                             (if (empty? jarjestettavat-gridit)
                               jarjestetyt-gridit
                               (let [seuraava-grid (some (fn [[m-jarjestettava [_ seurattava-id :as idt]]]
                                                           (some (fn [[m-jarjestetty [jarjestetty-id _]]]
                                                                   (when (= seurattava-id jarjestetty-id)
                                                                     [m-jarjestettava idt]))
                                                                 jarjestetyt-gridit))
                                                         jarjestettavat-gridit)]
                                 (if (vector? seuraava-grid)
                                   (recur (conj jarjestetyt-gridit seuraava-grid)
                                          (remove (fn [[_ [id _]]]
                                                    (= id (-> seuraava-grid last first)))
                                                  jarjestettavat-gridit))
                                   (throw (js/Error. (str "Gridin "
                                                          (when-let [gridin-nimi (gop/nimi root-grid)]
                                                            (str gridin-nimi " "))
                                                          "ja sen lasten kokojen seurannassa on kiertävä riippuvuus.")))))))]
    #_(println "GRIDIT JÄRJESTETTY: ")
    #_(cljs.pprint/pprint gridit-jarjestetty)
    (doseq [[grid [id seurattava-id]] gridit-jarjestetty]
      (if (= id seurattava-id)
        (aseta-koko! grid)
        (aseta-seurattava-koko! grid seurattava-id)))
    #_(p/aseta-lapset root-grid root-gridin-lapset)))


(defn- piirra-gridin-osat [osat grid]
  [:<>
   (doall
     (for [osa osat]
       (with-meta [gop/piirra osa]
                  {:key (gop/id osa)})))])

#_(defn- dynaaminen-grid [grid]
    (let [data (p/pointteri (::p/data grid) grid)
          osa (first (::osat grid))
          osat (map (fn [osan-id]
                      (p/aseta-id osa osan-id))
                    data)]
      [piirra-gridin-osat osat grid]))

(defn- staattinen-grid [grid]
  (let [osat (p/lapset grid)]
    [:<>
     [piirra-gridin-osat osat grid]]))

(defn seurattava-koko [grid seurattava]
  (let [seurattavan-id (gop/id (etsi-osa ((::root-fn grid)) seurattava))
        kursori (r/cursor ((::koko-fn grid)) [seurattavan-id])]
    (reaction @kursori)))

(defn piirra-grid [grid]
  (r/create-class
    {:constructor (fn [this props]
                    (when (= (gop/id grid) (::root-id grid))
                      (aseta-koot! grid))
                    (set! (.-state this) #js {:error nil}))
     :get-derived-state-from-error (fn [error]
                                     #js {:error error})
     :component-did-catch (fn [error error-info]
                            (warn (str "Komponentti kaatui virheeseen: "
                                       error-info
                                       (when g-debug/GRID_DEBUG
                                         (apply str "\n---- VIIMEISIMMÄT DATA MUUTOKSET ----"
                                                (str "\nGRID: " (or (gop/nimi grid) "Nimetön") "(" (gop/id grid) ")")
                                                (for [lapsi (p/lapset grid)]
                                                  (let [data (conj (get-in @g-debug/debug [:rajapinnat (get-in @g-debug/debug [:osat (gop/id lapsi) :rajapinta])])
                                                                   (get-in @g-debug/debug [:osat (gop/id lapsi) :derefable]))]
                                                    (str "\n--> " (or (gop/nimi lapsi) (gop/id lapsi))
                                                         "\n" (with-out-str (cljs.pprint/pprint
                                                                              {:data-rajapinnasta (first data)
                                                                               :grid-data (second data)
                                                                               :osan-derefable (get data 2)}))))))))))
     :display-name (str (or (gop/nimi grid) "Nimetön") " (" (gop/id grid) ")")
     :render (fn [this]
               (if-let [error (.. this -state -error)]
                 [virhekasittely/rendaa-virhe error]
                 (let [[_ grid] (r/argv this)
                       {luokat :class dom-id :id} @(:parametrit grid)
                       #_#__ (when-let [koko-fn (:koko-fn grid)]
                               @(koko-fn))
                       #_#_seurattava-koko (when-let [seuraa-asetukset (:seuraa (p/koko grid))]
                                             (seurattava-koko grid (:seurattava seuraa-asetukset)))
                       #_#_aseta-koko-uusiksi? (and seurattava-koko
                                                    (or (not= (:sarake @seurattava-koko) (:sarake (p/koko grid)))
                                                        (not= (:rivi @seurattava-koko) (:rivi (p/koko grid)))))
                       {:keys [korkeudet leveydet top left]} (laske-gridin-css-tiedot grid)]
                   ;; Mikälisikäli seurattavan koko vaihtuu, niin tämä tulisi renderöidä uudestaan
                   #_(when aseta-koko-uusiksi?
                       (aseta-seurattava-koko! grid))
                   [:div.grid-taulukko {:class (apply str (interpose " " luokat))
                                        :id dom-id}



                    ;; Järjestys ja koko erilleen (relevantteja vain laps'taulukoille)
                    ;; Järjestyksessä joku osa on toisen osan alisteinen
                    ;; Järjestyksessä rivien/sarakkeiden määrä voi poiketa
                    ;; Järjestyksen täytynee olla atomi. Sen takia, että yhdestä osiosta voidaan aiheuttaa sivuvaikutuksena
                    ;; Toisen osan järjestyksen muuttuminen

                    ;; data manageri, joka kertoo osille mistä lukea data ja raakadatan munklaa oikeaan muotoon.
                    [:div {:style {:display "grid"
                                   :position "relative"
                                   :top top
                                   :left left
                                   :grid-template-columns leveydet
                                   :grid-template-rows korkeudet}}
                     (if (-> grid meta ::dynaaminen?)
                       ;[dynaaminen-grid grid]
                       ^{:key (str (:id grid) "-dynamic")}
                       [:div "DYNAMIIIIC"]
                       ^{:key (str (:id grid) "-static")}
                       [staattinen-grid grid])]])))}))

(defn grid-index-polut
  ([grid] (grid-index-polut (p/lapset grid) [] {}))
  ([osat polku polut]
   (apply merge
          (map-indexed (fn [index osa]
                         (let [grid? (satisfies? p/IGrid osa)
                               uusi-polku (conj polku index)
                               id (gop/id osa)
                               polku-osalle (assoc polut id uusi-polku)]
                           (if grid?
                             (grid-index-polut (p/lapset osa) uusi-polku polku-osalle)
                             polku-osalle)))
                       osat))))

(defn grid-nimi-polut
  ([grid] (grid-nimi-polut (p/lapset grid) [] {}))
  ([osat polku polut]
   (apply merge
          (map-indexed (fn [index osa]
                         (let [grid? (satisfies? p/IGrid osa)
                               uusi-polku (conj polku (or (gop/nimi osa) index))
                               id (gop/id osa)
                               polku-osalle (assoc polut id uusi-polku)]
                           (if grid?
                             (grid-nimi-polut (p/lapset osa) uusi-polku polku-osalle)
                             polku-osalle)))
                       osat))))

(defn aseta-koko-grid [grid koko]
  (swap! ((::koko-fn grid))
         (fn [koot]
           (assoc koot (gop/id grid) koko))))

(defn paivita-koko-grid [grid f]
  (swap! ((::koko-fn grid))
         (fn [koot]
           (update koot (gop/id grid) f))))

(defn grid-koko [grid]
  (get @((::koko-fn grid)) (gop/id grid)))

(defn grid-koot [grid]
  @((::koko-fn grid)))

(defn muuta-vektoreiksi [data syvyys]
  (if (= 1 syvyys)
    data
    (let [m? (map? data)
          data (mapv (fn [solu]
                       (if m?
                         [(key solu) (muuta-vektoreiksi (val solu) (dec syvyys))]
                         (muuta-vektoreiksi solu (dec syvyys))))
                     data)]
      (if m?
        (into {} data)
        data))))

(defn muokkaa-data-syvyydessa [syvyys data f]
  (let [data (muuta-vektoreiksi data syvyys)]
    (loop [datan-index-polku (vec (repeat syvyys 0))
           syvyys-pointteri (dec (count datan-index-polku))
           osittain-muokattu-tulos data]
      (let [[data-polussa datan-polku] (reduce (fn [[data polku-dataan] index]
                                                 (cond
                                                   (or (not (coll? data))
                                                       (<= (count data) index)) [nil polku-dataan]
                                                   (map? data) (let [map-entry (-> data seq (nth index))]
                                                                 [(val map-entry) (conj polku-dataan (key map-entry))])
                                                   (sequential? data) [(nth data index) (conj polku-dataan index)]))
                                               [osittain-muokattu-tulos []]
                                               datan-index-polku)]
        (if (and (nil? data-polussa)
                 (= syvyys-pointteri 0))
          osittain-muokattu-tulos
          (if data-polussa
            (recur (update datan-index-polku (dec (count datan-index-polku)) inc)
                   syvyys-pointteri
                   (update-in osittain-muokattu-tulos datan-polku f datan-polku))
            (recur (vec (concat (-> (take syvyys-pointteri datan-index-polku) vec (update (dec syvyys-pointteri) inc))
                                (repeat (- syvyys syvyys-pointteri) 0)))
                   (dec syvyys-pointteri)
                   osittain-muokattu-tulos)))))))


(defn jarjesta-data [data jarjestys]
  (loop [[jarjestys & loput-jarjestykset] jarjestys
         syvyys 0
         tulos data]
    (if (nil? jarjestys)
      tulos
      (recur loput-jarjestykset
             (inc syvyys)
             (if (= 0 syvyys)
               (dk/jarjesta-data jarjestys tulos)
               (muokkaa-data-syvyydessa syvyys
                                        tulos
                                        (fn [data _]
                                          (dk/jarjesta-data jarjestys data))))))))

(defn lisaa-rivi! [grid solu index]
  (pre-walk-grid! grid
                  (fn [osa]
                    (when (and (satisfies? p/IGrid osa)
                               (every? #(satisfies? sp/ISolu %)
                                       (p/lapset osa)))
                      (p/paivita-alueet! osa (fn [alueet]
                                               (mapv (fn [alue]
                                                       (update alue :rivit (fn [[alku loppu]]
                                                                             [alku (inc loppu)])))
                                                     alueet)))
                      (p/paivita-lapset! osa (fn [lapset]
                                               (mapv (fn [i]
                                                       (cond
                                                         (< i index) (get lapset i)
                                                         (= i index) solu
                                                         (> i index) (get lapset (inc i))))
                                                     (range (inc (count lapset))))))))))

(defn lisaa-sarake! [grid solu index]
  (pre-walk-grid! grid
                  (fn [osa]
                    (when (and (satisfies? p/IGrid osa)
                               (every? #(satisfies? sp/ISolu %)
                                       (p/lapset osa)))
                      (p/paivita-alueet! osa (fn [alueet]
                                               (mapv (fn [alue]
                                                       (update alue :sarakkeet (fn [[alku loppu]]
                                                                                 [alku (inc loppu)])))
                                                     alueet)))
                      (p/paivita-lapset! osa (fn [lapset]
                                               (mapv (fn [i]
                                                       (cond
                                                         (< i index) (get lapset i)
                                                         (= i index) solu
                                                         (> i index) (get lapset (dec i))))
                                                     (range (inc (count lapset))))))))))

(defn aseta-osat!
  ([grid osat] (swap! (:osat grid) (fn [_] osat)) grid)
  ([grid polku osat]
   (let [osa (get-in-grid grid polku)]
     (p/aseta-lapset! osa osat)
     grid)))

(defn paivita-osat!
  ([grid f]
   (swap! (:osat grid)
          (fn [osat]
            (f osat)))
   grid)
  ([grid polku f]
   (let [osa (get-in-grid grid polku)]
     (p/paivita-lapset! osa f)
     grid)))

(defn aseta-root-fn [grid {:keys [haku paivita!]}]
  (paivita-kaikki-lapset! (assoc grid ::root-fn haku
                                 ::paivita-root! paivita!)
                          (constantly true)
                          (fn [osa]
                            (assoc osa ::root-fn haku
                                   ::paivita-root! paivita!))))

(defn osa-polussa [grid [polun-osa & polku]]
  (if (nil? polun-osa)
    grid
    (let [loydetyt-osat (keep-indexed (fn [index lapsi]
                                        (when (or (= (gop/nimi lapsi) polun-osa)
                                                  (and (integer? polun-osa)
                                                       (= index polun-osa)))
                                          lapsi))
                                      (p/lapset grid))]
      (when-not (= 1 (count loydetyt-osat))
        (warn (str "Polun osalle " polun-osa " ei löytynyt vain yhtä osaa. Löydetyt osat: " loydetyt-osat "\n"
                   "OSA: " (or (gop/nimi grid) "Nimetön") " (" (gop/id grid) ")" "\n"
                   "-> " (apply str (interpose "\n-> " (map (fn [lapsi]
                                                              (str (or (gop/nimi lapsi) "Nimetön") " (" (gop/id lapsi) ")"))
                                                            (p/lapset grid)))))))
      (recur (first loydetyt-osat) polku))))

(defn piillota! [grid]
  (p/paivita-parametrit! grid (fn [parametrit]
                                (update parametrit :class conj "piillotettu"))))

(defn nayta! [grid]
  (p/paivita-parametrit! grid (fn [parametrit]
                                (update parametrit :class disj "piillotettu"))))

(defn piillotettu? [grid]
  (contains? (get-in (p/parametrit grid) :class) "piillotettu"))

(defn grid-osat
  ([grid] @(:osat grid))
  ([grid polku] (get-in @(:osat grid) polku)))

(defn grid-alueet
  [grid]
  @(:alueet grid))

(defn aseta-alueet! [grid alueet]
  (swap! (:alueet grid)
         (fn [_]
           alueet)))
(defn paivita-alueet! [grid f]
  (swap! (:alueet grid)
         (fn [alueet]
           (f alueet))))

(defn grid-parametrit [grid]
  @(:parametrit grid))

(defn aseta-parametrit! [grid parametrit]
  (swap! (:parametrit grid)
         (fn [_]
           parametrit)))
(defn paivita-parametrit! [grid f]
  (swap! (:parametrit grid)
         (fn [parametrit]
           (f parametrit))))

(defn grid-kopioi [kopioitava-grid konstruktori]
  (let [koko (r/atom (p/koot kopioitava-grid))
        koko-fn (constantly koko)
        grid (konstruktori (:id kopioitava-grid)
                           (r/atom (p/alueet kopioitava-grid))
                           koko
                           (r/atom (p/lapset kopioitava-grid))
                           (r/atom (p/parametrit kopioitava-grid)))
        grid (paivita-kaikki-lapset! (assoc grid ::koko-fn koko-fn)
                                     (constantly true)
                                     (fn [lapsi]
                                       (cond-> lapsi
                                               (satisfies? gop/IGridOsa lapsi) (assoc ::koko-fn koko-fn)
                                               (satisfies? gop/IGridOsa lapsi) (dissoc ::root-fn)
                                               (satisfies? p/IGrid lapsi) (gop/kopioi))))]
    (merge (assoc grid :osat (r/atom (p/lapset grid)))
           (dissoc kopioitava-grid ::root-fn :id :alueet :koko :osat :parametrit))))

(defn- grid-polku-sopii-osaan?
  [grid-polku osa]
  (let [nimi-polku (::nimi-polku osa)]
    (and (every? true? (map (fn [gp op]
                              (= gp op))
                            grid-polku
                            nimi-polku))
         (>= (count nimi-polku) (count grid-polku)))))

(defn- solun-polun-pituus-oikein?
  [solun-polun-pituus osa]
  (let [nimipolku-ilman-loppuindexeja (->> osa ::nimi-polku reverse (drop-while integer?) reverse vec)
        osan-polku-dataan (vec (drop (count nimipolku-ilman-loppuindexeja)
                                     (::index-polku osa)))]
    (= solun-polun-pituus (count osan-polku-dataan))))

(defn- rajapinnan-grid-kasittelija
  [datan-kasittelija grid grid-kasittelijan-polku {:keys [rajapinta jarjestys datan-kasittely tunnisteen-kasittely] :as kasittelija}]
  (let [rajapintakasittelija (seuranta (reaction (let [rajapinnan-data @(dk/rajapinnan-kuuntelija datan-kasittelija rajapinta)
                                                       rajapinnan-dataf (if *jarjesta-data?*
                                                                          (do (println "JÄRJESTETÄÄN")
                                                                              (jarjesta-data rajapinnan-data jarjestys))
                                                                          (do (println "EI JÄRJESTETÄ")
                                                                              rajapinnan-data))]
                                                   (when g-debug/GRID_DEBUG
                                                     (swap! g-debug/debug
                                                            (fn [tila]
                                                              (update-in tila
                                                                         [:rajapinnat rajapinta]
                                                                         (fn [taman-tila]
                                                                           (let [taman-tila (or taman-tila
                                                                                                (vec (repeat 2 nil)))]
                                                                             (assoc taman-tila
                                                                                    0 rajapinnan-data
                                                                                    1 rajapinnan-dataf)))))))
                                                   (datan-kasittely rajapinnan-dataf))))
        tunnisteen-kasittely (or tunnisteen-kasittely (constantly nil))]
    (assoc kasittelija
           :rajapintakasittelija rajapintakasittelija
           :osien-tunnisteet (tunnisteen-kasittely (osa-polussa grid grid-kasittelijan-polku) @rajapintakasittelija))))

(defn- osan-data-yhdistaminen [datan-kasittelija grid-rajapintakasittelijat osa]
  (assoc (if-let [loydetty-osa (some (fn [[grid-polku {:keys [rajapintakasittelija osien-tunnisteet solun-polun-pituus seuranta rajapinta]}]]
                                       (let [grid-polku-sopii-osaan? (grid-polku-sopii-osaan? grid-polku osa)
                                             nimipolku-ilman-loppuindexeja (->> osa ::nimi-polku reverse (drop-while integer?) reverse vec)
                                             osan-polku-dataan (vec (drop (count nimipolku-ilman-loppuindexeja)
                                                                          (::index-polku osa)))
                                             solun-polun-pituus-oikein? (solun-polun-pituus-oikein? solun-polun-pituus osa)]
                                         (when (and grid-polku-sopii-osaan?
                                                    solun-polun-pituus-oikein?)
                                           #_(when-not (or (= solun-polun-pituus (count osan-polku-dataan))
                                                           (nil? solun-polun-pituus))
                                               (warn (str "Osan " (or (gop/nimi osa) (gop/id osa)) " polku ei ole oikein."
                                                          " Nimi polku: " (::nimi-polku osa)
                                                          " Index polku: " (::index-polku osa))))
                                           (let [osan-derefable (seuranta-derefable! rajapintakasittelija osan-polku-dataan)]
                                             (when g-debug/GRID_DEBUG
                                               (swap! g-debug/debug
                                                      (fn [tila]
                                                        (update-in tila
                                                                   [:osat (gop/id osa)]
                                                                   (fn [taman-tila]
                                                                     (assoc taman-tila
                                                                            :derefable osan-derefable
                                                                            :rajapinta rajapinta))))))
                                             (assoc osa ::osan-derefable osan-derefable
                                                    ::tunniste-rajapinnan-dataan (get-in osien-tunnisteet osan-polku-dataan)
                                                    ::triggeroi-seuranta! (when seuranta
                                                                            (fn [] (dk/triggeroi-seuranta! datan-kasittelija seuranta))))))))
                                     grid-rajapintakasittelijat)]
           loydetty-osa
           osa)
         ::datan-kasittelija datan-kasittelija))

(declare ->Grid)

(defrecord Grid [id alueet koko osat parametrit]
  p/IGrid
  (-osat [this]
    (grid-osat this))
  (-osat [this polku]
    (grid-osat this polku))
  (-aseta-osat! [this osat]
    (aseta-osat! this osat))
  (-aseta-osat! [this polku osat]
    (aseta-osat! this polku osat))
  (-paivita-osat! [this f]
    (paivita-osat! this f))
  (-paivita-osat! [this polku f]
    (paivita-osat! this polku f))

  (-lisaa-rivi! [this solu]
    (p/lisaa-rivi! this solu (count (p/lapset this))))
  (-lisaa-rivi! [this solu index]
    (lisaa-rivi! this solu index))
  (-lisaa-sarake! [this solu]
    (p/lisaa-sarake! this solu (count (p/lapset this))))
  (-lisaa-sarake! [this solu index]
    (lisaa-sarake! this solu index))

  (-koko [this]
    (grid-koko this))
  (-koot [this]
    (grid-koot this))
  (-aseta-koko! [this koko]
    (aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (paivita-koko-grid this f))
  (-alueet [this]
    (grid-alueet this))
  (-aseta-alueet! [this alueet]
    (aseta-alueet! this alueet))
  (-paivita-alueet! [this f]
    (paivita-alueet! this f))
  (-aseta-root-fn [this f]
    (aseta-root-fn this f))

  (-parametrit [this]
    (grid-parametrit this))
  (-aseta-parametrit! [this parametrit]
    (aseta-parametrit! this parametrit))
  (-paivita-parametrit! [this f]
    (paivita-parametrit! this f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))

  p/IGridDataYhdistaminen
  (-rajapinta-grid-yhdistaminen! [this rajapinta datan-kasittelija grid-kasittelija]
    (let [grid-rajapintakasittelijat (reduce-kv (fn [m polku {:keys [rajapinta jarjestys datan-kasittely tunnisteen-kasittely] :as kasittelija}]
                                                  (assoc m polku (rajapinnan-grid-kasittelija datan-kasittelija this polku kasittelija)))
                                                {}
                                                grid-kasittelija)]
      (paivita-kaikki-lapset! (assoc this ::lopeta-rajapinnan-kautta-kuuntelu! (fn []
                                                                                 (dk/poista-seurannat! datan-kasittelija)
                                                                                 (dk/lopeta-tilan-kuuntelu! datan-kasittelija))
                                     ::grid-rajapintakasittelijat grid-rajapintakasittelijat
                                     ::datan-kasittelija datan-kasittelija)
                              (constantly true)
                              (partial osan-data-yhdistaminen datan-kasittelija grid-rajapintakasittelijat))))
  (-grid-tapahtumat [this data-atom tapahtumat]
    (assoc this ::grid-tapahtumat (into {}
                                        (map (fn [[tapahtuman-nimi {:keys [polut toiminto!] :as tapahtuma}]]
                                               (let [kasittely-fn (fn [uusi-data]
                                                                    (r/next-tick (fn []
                                                                                   (apply toiminto! (root this) @data-atom uusi-data))))]
                                                 (add-watch data-atom
                                                            tapahtuman-nimi
                                                            (fn [_ _ vanha uusi]
                                                              (let [vanha-data (map #(get-in vanha %) polut)
                                                                    uusi-data (map #(get-in uusi %) polut)
                                                                    seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                                                    ajetaan-tapahtuma? (and seurattava-data-muuttunut?
                                                                                            *ajetaan-tapahtuma?*)]
                                                                (when ajetaan-tapahtuma?
                                                                  (kasittely-fn uusi-data)))))
                                                 [tapahtuman-nimi {:seurannan-lopetus! (fn []
                                                                                         (remove-watch data-atom tapahtuman-nimi))
                                                                   :tapahtuma-trigger! (fn []
                                                                                         (kasittely-fn (map #(get-in @data-atom %) polut)))}]))
                                             tapahtumat))))
  gop/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::nimi nimi))
  gop/IPiirrettava
  (-piirra [this]
    [:<>
     [piirra-grid this]])
  gop/IPiillota
  (-piillota! [this]
    (piillota! this))
  (-nayta! [this]
    (nayta! this))
  (-piillotettu? [this]
    (piillotettu? this))
  gop/IKopioi
  (-kopioi [this]
    (grid-kopioi this ->Grid)))

(defn- muodosta-uusi-polku
  ([annettu-polku polun-alku] (muodosta-uusi-polku annettu-polku polun-alku false))
  ([annettu-polku polun-alku muodostetaan-polkua?]
   (let [ensimmainen-osa (first annettu-polku)]
     (cond
       (= :. ensimmainen-osa) (vec (concat polun-alku (rest annettu-polku)))
       (= :.. ensimmainen-osa) (recur (rest annettu-polku) (butlast polun-alku) true)
       muodostetaan-polkua? (vec (concat polun-alku annettu-polku))
       :else annettu-polku))))

(defn vaihda-osa! [vaihdettava-osa vaihto-fn & datan-kasittelyt]
  (let [root-grid (root vaihdettava-osa)
        vaihdettavan-osan-id (gop/id vaihdettava-osa)
        ;; OSAN PÄIVITTÄMINEN
        uusi-grid (paivita-kaikki-lapset! root-grid
                                          (fn [osa]
                                            (gop/id? osa vaihdettavan-osan-id))
                                          (fn [vaihdettava-osa]
                                            (let [uusi-osa (vaihto-fn vaihdettava-osa)
                                                  uusi-osa-grid? (satisfies? p/IGrid uusi-osa)
                                                  vaihdettava-osa-grid? (satisfies? p/IGrid vaihdettava-osa)
                                                  index-polut (when uusi-osa-grid? (grid-index-polut uusi-osa))
                                                  nimi-polut (when uusi-osa-grid? (grid-nimi-polut uusi-osa))
                                                  uuden-osan-nimi (gop/nimi uusi-osa)
                                                  uuden-osan-koot (when uusi-osa-grid?
                                                                    (p/koot uusi-osa))
                                                  vaihdettavan-osan-koot (when vaihdettava-osa-grid?
                                                                           (select-keys (p/koot vaihdettava-osa)
                                                                                        (into #{}
                                                                                              (gridin-osat-vektoriin vaihdettava-osa #(satisfies? p/IGrid %) gop/id))))

                                                  lisattavat-tiedot (select-keys vaihdettava-osa #{::root-fn ::paivita-root! ::root-id ::koko-fn ::datan-kasittelija})
                                                  osan-kasittely (fn [osa]
                                                                   (let [osa (-> osa
                                                                                 (merge lisattavat-tiedot)
                                                                                 (update ::index-polku
                                                                                         (fn [vanha-index-polku]
                                                                                           (vec (concat (::index-polku vaihdettava-osa)
                                                                                                        (get index-polut (gop/id osa))))))
                                                                                 (update ::nimi-polku
                                                                                         (fn [vanha-nimi-polku]
                                                                                           (vec (concat (butlast (::nimi-polku vaihdettava-osa))
                                                                                                        [(or uuden-osan-nimi (last (::index-polku vaihdettava-osa)))]
                                                                                                        (get nimi-polut (gop/id osa)))))))
                                                                         osa (if (and (satisfies? p/IGrid osa)
                                                                                      (not (= (gop/id osa) (::root-id osa))))
                                                                               (assoc osa :koko nil)
                                                                               osa)]
                                                                     (if-let [kaytetyn-osan-id (some (fn [vaihdettavan-osan-osan-id]
                                                                                                       (when (= vaihdettavan-osan-osan-id (gop/id osa))
                                                                                                         vaihdettavan-osan-osan-id))
                                                                                                     (keys vaihdettavan-osan-koot))]
                                                                       (let [id (gensym "muutettu")]
                                                                         (swap! ((::koko-fn root-grid))
                                                                                (fn [koot]
                                                                                  (assoc koot id (get vaihdettavan-osan-koot kaytetyn-osan-id))))
                                                                         (assoc osa :id id))
                                                                       osa)))
                                                  _ (when uusi-osa-grid?
                                                      (pre-walk-grid! uusi-osa
                                                                      (fn [osa]
                                                                        (when (satisfies? p/IGrid osa)
                                                                          (swap! ((::koko-fn root-grid))
                                                                                 (fn [koot]
                                                                                   (assoc koot (gop/id osa) (get uuden-osan-koot (gop/id osa)))))))))
                                                  paivitetty-osa (if uusi-osa-grid?
                                                                   (paivita-kaikki-lapset! (osan-kasittely uusi-osa)
                                                                                           (constantly true)
                                                                                           osan-kasittely)
                                                                   (osan-kasittely uusi-osa))]
                                              (when-let [poistuvat-osat (and vaihdettava-osa-grid?
                                                                             (gridin-osat-vektoriin vaihdettava-osa #(satisfies? p/IGrid %) gop/id))]
                                                (swap! ((::koko-fn root-grid))
                                                       (fn [koot]
                                                         (apply dissoc koot poistuvat-osat))))
                                              (when uusi-osa-grid?
                                                (swap! ((::koko-fn root-grid))
                                                       (fn [koot]
                                                         (assoc koot (gop/id vaihdettava-osa) (get uuden-osan-koot (gop/id uusi-osa))))))
                                              (assoc paivitetty-osa :id vaihdettavan-osan-id))))]
    (aseta-koot! uusi-grid)

    ;; GRIDKÄSITTELIJÄN HOITAMINEN
    (let [polun-alku (::nimi-polku vaihdettava-osa)
          vaihdettavan-osan-polun-osa (vec (butlast polun-alku))
          uusi-osa (get-in-grid uusi-grid (conj vaihdettavan-osan-polun-osa vaihdettavan-osan-id))
          uuden-osan-nimi (gop/nimi uusi-osa)
          polun-alku (conj vaihdettavan-osan-polun-osa (or uuden-osan-nimi
                                                           (first (keep-indexed (fn [index osa]
                                                                                  (when (= (gop/id osa) vaihdettavan-osan-id)
                                                                                    index))
                                                                                (p/lapset (get-in-grid uusi-grid vaihdettavan-osan-polun-osa))))))
          datan-kasittelija (::datan-kasittelija uusi-grid)
          rajapintakasittelijat (::grid-rajapintakasittelijat uusi-grid)
          vanhat-osat (if (satisfies? p/IGrid vaihdettava-osa)
                        (gridin-osat-vektoriin vaihdettava-osa (constantly true) identity)
                        [vaihdettava-osa])
          uudet-gridkasittelijat (reduce (fn [m [polku kasittelija]]
                                           (let [polku (muodosta-uusi-polku polku polun-alku)]
                                             (assoc m polku (rajapinnan-grid-kasittelija datan-kasittelija uusi-grid polku kasittelija))))
                                         {}
                                         (partition 2 datan-kasittelyt))
          osan-kasittely (fn [osa]
                           (osan-data-yhdistaminen datan-kasittelija uudet-gridkasittelijat osa))
          _ (doseq [vanha-osa vanhat-osat]
              (let [rajapintakasittelija (some (fn [[grid-polku {:keys [rajapintakasittelija solun-polun-pituus]}]]
                                                 (when (and (grid-polku-sopii-osaan? grid-polku vanha-osa)
                                                            (solun-polun-pituus-oikein? solun-polun-pituus vanha-osa))
                                                   rajapintakasittelija))
                                               rajapintakasittelijat)]
                (when rajapintakasittelija
                  (poista-seuranta-derefable! rajapintakasittelija))))
          uusi-grid (paivita-root! uusi-grid (fn [vanha-grid]
                                               (update vanha-grid ::grid-rajapintakasittelijat (fn [rk]
                                                                                                 (merge rk
                                                                                                        uudet-gridkasittelijat)))))]
      (paivita-kaikki-lapset! uusi-grid
                              (fn [osa]
                                (gop/id? osa vaihdettavan-osan-id))
                              (fn [uusi-osa]
                                (if (satisfies? p/IGrid uusi-osa)
                                  (paivita-kaikki-lapset! (osan-kasittely uusi-osa)
                                                          (constantly true)
                                                          osan-kasittely)
                                  (osan-kasittely uusi-osa)))))))

(defn paivita-osa! [osa f]
  {:pre [(satisfies? gop/IGridOsa osa)
         (fn? f)]
   :post [(gop/id? % (gop/id osa))]}
  (if (gop/id? (root osa) (gop/id osa))
    (paivita-root! osa f)
    (p/paivita-lapset! (vanhempi osa)
                       (fn [lapset]
                         (mapv (fn [lapsi]
                                 (if (gop/id? lapsi (gop/id osa))
                                   (f lapsi)
                                   lapsi))
                               lapset))))
  (some #(when (gop/id? % (gop/id osa))
           %)
        (p/lapset (vanhempi osa))))

;; data erilleen, aggregoi hommia datasta, merkkaa alueet aggregoitaviksi

;; Alue ottaa vektorin vektoreita (tai sittenkin mappeja?) dataa. Voi määritellä x- ja y-suunnissa, että kasvaako alue datan mukana vaiko ei.


(defn muuta-id [kopio]
  (let [id (gensym "grid")
        gridi (assoc kopio :id id
                     ::root-id id)
        kopioitavan-gridin-koko (get (p/koot kopio) (gop/id kopio))
        koko (r/atom (-> (p/koot kopio)
                         (dissoc (gop/id kopio))
                         (assoc id kopioitavan-gridin-koko)))
        koko-fn (constantly koko)]
    (paivita-kaikki-lapset! (assoc gridi ::koko-fn koko-fn)
                            (constantly true)
                            (fn [lapsi]
                              (assoc lapsi ::koko-fn koko-fn
                                     ::root-id id)))))

(defn validi-grid-asetukset?
  [{:keys [nimi alueet koko jarjestys osat]}]
  (and (or (nil? nimi) (satisfies? IEquiv nimi))
       (or (nil? alueet) (s/valid? ::alueet alueet))
       (or (nil? koko) (s/valid? ::koko koko))
       (or (nil? jarjestys) (s/valid? ::jarjestys jarjestys))
       (or (nil? osat) (s/valid? ::osat osat))))

(defn grid-c [record {:keys [nimi alueet koko osat root-fn luokat] :as asetukset}]
  (let [root-id (gensym "grid")
        koko (r/atom {root-id koko})
        osat (r/atom osat)
        alueet (r/atom alueet)
        parametrit (r/atom {:class luokat})
        koko-fn (constantly koko)
        gridi (cond-> (record root-id alueet koko osat parametrit)
                      true (assoc ::koko-fn koko-fn
                                  ::root-fn root-fn)
                      nimi (assoc ::nimi nimi))
        index-polut (grid-index-polut gridi)
        nimi-polut (grid-nimi-polut gridi)]
    (paivita-kaikki-lapset! gridi
                            (constantly true)
                            (fn [osa]
                              (let [grid? (satisfies? p/IGrid osa)
                                    koot (when grid?
                                           (p/koot osa))
                                    id (gop/id osa)
                                    _ (when koot
                                        (swap! koko (fn [koko]
                                                      (merge koko koot))))]
                                (assoc osa
                                       :koko nil
                                       ::koko-fn koko-fn
                                       ::root-id root-id
                                       ::root-fn root-fn
                                       ::index-polku (get index-polut id)
                                       ::nimi-polku (get nimi-polut id)))))
    gridi))
