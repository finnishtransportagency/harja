(ns harja.ui.taulukko.grid
  (:require [harja.ui.taulukko.grid-protokollat :as p]
            [harja.ui.taulukko.solu-protokollat :as sp]
            [harja.ui.taulukko.grid-osa-protokollat :as gop]
            [harja.ui.taulukko.datan-kasittely :as dk]
            [harja.virhekasittely :as virhekasittely]
            [harja.loki :refer [log warn]]
            [cljs.spec.alpha :as s]
            [clojure.string :as clj-str]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [clojure.walk :as walk]
            [harja.ui.grid-debug :as g-debug])
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

(def ^:dynamic *jarjesta-data?* true)

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
  ([osa etsittava-osa]
   (if (oikea-osa? osa etsittava-osa)
     osa
     (let [kaikki-osan-lapset (remove nil? (p/lapset osa))]
       (etsi-osa osa etsittava-osa kaikki-osan-lapset))))
  ([_ etsittava-osa lapset]
   (let [oikea-osa (some (fn [osa]
                           (when (oikea-osa? osa etsittava-osa)
                             osa))
                         lapset)]
     (if (or oikea-osa (empty? lapset))
       oikea-osa
       (let [kaikki-lapsen-lapset (sequence (comp (mapcat p/lapset)
                                                  (remove nil?))
                                            lapset)]
         (recur nil etsittava-osa kaikki-lapsen-lapset))))))

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

;; TODO tällä funktiolle voinee kehitellä vähän tehokkaammankin toteutuksen
(defn paivita-kaikki-lapset
  [osa pred f]
  (let [paivitetyt-lapset (loop [[lapsi & lapset] (p/lapset osa)
                                 paivitetyt-lapset []]
                            (if (nil? lapsi)
                              paivitetyt-lapset
                              (let [lapsi (if (pred lapsi)
                                            (f lapsi)
                                            lapsi)
                                    lapsen-lapset-paivitetty (if (satisfies? p/IGrid lapsi)
                                                               (paivita-kaikki-lapset lapsi pred f)
                                                               lapsi)]
                                (recur lapset
                                       (conj paivitetyt-lapset lapsen-lapset-paivitetty)))))]
    (p/aseta-lapset osa paivitetyt-lapset)))



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
    #_(println "GRIDIT JÄRJESTETTY: " )
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
                    #_(r/set-state this {:error nil})
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
                       luokat (-> grid :parametrit :class)
                       dom-id (-> grid :parametrit :id)
                       #_#_seurattava-koko (when-let [seuraa-asetukset (:seuraa (p/koko grid))]
                                             (seurattava-koko grid (:seurattava seuraa-asetukset)))
                       #_#_aseta-koko-uusiksi? (and seurattava-koko
                                                    (or (not= (:sarake @seurattava-koko) (:sarake (p/koko grid)))
                                                        (not= (:rivi @seurattava-koko) (:rivi (p/koko grid)))))
                       {:keys [korkeudet leveydet top left]} (laske-gridin-css-tiedot grid)]
                   ;; Mikälisikäli seurattavan koko vaihtuu, niin tämä tulisi renderöidä uudestaan
                   #_(when aseta-koko-uusiksi?
                       (aseta-seurattava-koko! grid))
                   [:div {:style {:overflow "hidden"}}



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
           (println "KOOT: " koot)
           (println "ID: " (gop/id grid))
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
                                          (dk/jarjesta-data jarjestys data)))))
      #_(loop [jarjestettavan-osa-polku (vec (repeat syvyys 0))
               syvyys-pointteri (dec (count jarjestettavan-osa-polku))
               osittain-jarjestetty-tulos tulos]
          (let [data-polussa (get-in osittain-jarjestetty-tulos jarjestettavan-osa-polku)]
            (if (and (nil? data-polussa)
                     (= syvyys-pointteri 0))
              osittain-jarjestetty-tulos
              (if data-polussa
                (recur (update jarjestettavan-osa-polku syvyys-pointteri inc)
                       syvyys-pointteri
                       (update-in osittain-jarjestetty-tulos jarjestettavan-osa-polku dk/jarjesta-data jarjestys))
                (recur (vec (concat (-> (take syvyys-pointteri jarjestettavan-osa-polku) vec (update (dec syvyys-pointteri) inc))
                                    (repeat (- syvyys syvyys-pointteri) 0)))
                       (dec syvyys-pointteri)
                       osittain-jarjestetty-tulos))))))))

(defn lisaa-rivi [grid solu index]
  (walk/prewalk (fn [x]
                  (if (and (satisfies? p/IGrid x)
                           (every? #(satisfies? sp/ISolu %)
                                   (p/lapset x)))
                    (-> x
                        (p/paivita-alueet (fn [alueet]
                                            (mapv (fn [alue]
                                                    (update alue :rivit (fn [[alku loppu]]
                                                                          [alku (inc loppu)])))
                                                  alueet)))
                        (p/paivita-lapset (fn [lapset]
                                            (mapv (fn [i]
                                                    (cond
                                                      (< i index) (get lapset i)
                                                      (= i index) solu
                                                      (> i index) (get lapset (inc i))))
                                                  (range (inc (count lapset)))))))
                    x))
                grid))

(defn lisaa-sarake [grid solu index]
  (walk/prewalk (fn [x]
                  (if (and (satisfies? p/IGrid x)
                           (every? #(satisfies? sp/ISolu %)
                                   (p/lapset x)))
                    (-> x
                        (p/paivita-alueet (fn [alueet]
                                            (mapv (fn [alue]
                                                    (update alue :sarakkeet (fn [[alku loppu]]
                                                                              [alku (inc loppu)])))
                                                  alueet)))
                        (p/paivita-lapset (fn [lapset]
                                            (mapv (fn [i]
                                                    (cond
                                                      (< i index) (get lapset i)
                                                      (= i index) solu
                                                      (> i index) (get lapset (inc i))))
                                                  (range (inc (count lapset)))))))
                    x))
                grid))

(defn oikea-osa-nimipolusta? [osa index polun-osa]
  (or (= (gop/nimi osa) polun-osa)
      (and (integer? polun-osa)
           (= index polun-osa))))

(defn aseta-osat
  ([grid osat] (assoc grid ::osat osat))
  ([grid [polun-osa & loput-polusta] osat]
   (if (nil? polun-osa)
     (p/aseta-lapset grid osat)
     (assoc grid
            ::osat
            (vec
              (map-indexed (fn [index lapsi]
                             (if (oikea-osa-nimipolusta? lapsi index polun-osa)
                               (p/aseta-lapset lapsi (vec loput-polusta) osat)
                               lapsi))
                           (p/lapset grid)))))))

(defn paivita-osat
  ([grid f] (update grid ::osat f))
  ([grid [polun-osa & loput-polusta] f]
   (if (nil? polun-osa)
     (p/paivita-lapset grid f)
     (update grid
             ::osat
             (fn [osat]
               (vec
                 (map-indexed (fn [index lapsi]
                                (if (oikea-osa-nimipolusta? lapsi index polun-osa)
                                  (p/paivita-lapset lapsi (vec loput-polusta) f)
                                  lapsi))
                              osat)))))))

(defn aseta-root-fn [grid f]
  (paivita-kaikki-lapset (assoc grid ::root-fn f)
                         (constantly true)
                         (fn [osa]
                           (assoc osa ::root-fn f))))

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
        (warn (str "Polun osalle " polun-osa " ei löytynyt vain yhtä osaa. Löydetyt osat: " loydetyt-osat)))
      (recur (first loydetyt-osat) polku))))

::datan-kasittelija

(defrecord Grid [id]
  p/IGrid
  (-osat [this]
    (::osat this))
  (-osat [this polku]
    (get-in (::osat this) polku))
  (-aseta-osat [this osat]
    (aseta-osat this osat))
  (-aseta-osat [this polku osat]
    (aseta-osat this polku osat))
  (-paivita-osat [this f]
    (paivita-osat this f))
  (-paivita-osat [this polku f]
    (paivita-osat this polku f))

  (-lisaa-rivi [this solu]
    (p/lisaa-rivi this solu (dec (count (p/lapset this)))))
  (-lisaa-rivi [this solu index]
    (lisaa-rivi this solu index))
  (-lisaa-sarake [this solu]
    (p/lisaa-sarake this solu (dec (count (p/lapset this)))))
  (-lisaa-sarake [this solu index]
    (lisaa-sarake this solu index))

  (-koko [this]
    (grid-koko this))
  (-koot [this]
    (grid-koot this))
  (-aseta-koko! [this koko]
    (aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (paivita-koko-grid this f))
  (-alueet [this]
    (::alueet this))
  (-aseta-alueet [this alueet]
    (assoc this ::alueet alueet))
  (-paivita-alueet [this f]
    (update this ::alueet f))
  (-aseta-root-fn [this f]
    (aseta-root-fn this f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))

  p/IGridDataYhdistaminen
  (-rajapinta-grid-yhdistaminen! [this rajapinta datan-kasittelija grid-kasittelija]
    (let [grid-rajapintakasittelijat (reduce-kv (fn [m polku {:keys [rajapinta jarjestys datan-kasittely tunnisteen-kasittely] :as kasittelija}]
                                                  (let [rajapintakasittelija (reaction (let [rajapinnan-data @(dk/rajapinnan-kuuntelija datan-kasittelija rajapinta)
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
                                                                                         (datan-kasittely rajapinnan-dataf)))
                                                        tunnisteen-kasittely (or tunnisteen-kasittely (constantly nil))]
                                                    (assoc m polku (assoc kasittelija
                                                                          :rajapintakasittelija rajapintakasittelija
                                                                          :osien-tunnisteet (tunnisteen-kasittely (osa-polussa this polku) @rajapintakasittelija)))))
                                                {}
                                                grid-kasittelija)]
      (paivita-kaikki-lapset (assoc this :lopeta-rajapinnan-kautta-kuuntelu! (fn []
                                                                               (doseq [[_ kasittelija] grid-rajapintakasittelijat]
                                                                                 (r/dispose! (:rajapintakasittelija kasittelija)))
                                                                               (dk/poista-seurannat! datan-kasittelija)
                                                                               (dk/lopeta-tilan-kuuntelu! datan-kasittelija))
                                    ::datan-kasittelija datan-kasittelija
                                    #_#_::seurannat (for [[seurannan-nimi _] (:seurannat datan-kasittelija)]
                                                  @(dk/seuranta datan-kasittelija seurannan-nimi)))
                             (constantly true)
                             (fn [osa]
                               (assoc (if-let [loydetty-osa (some (fn [[grid-polku {:keys [rajapintakasittelija osien-tunnisteet solun-polun-pituus seuranta rajapinta]}]]
                                                                    (let [grid-polku-sopii-osaan? (every? true? (map (fn [gp op]
                                                                                                                       (= gp op))
                                                                                                                     grid-polku
                                                                                                                     (::nimi-polku osa)))
                                                                          nimipolku-ilman-loppuindexeja (->> osa ::nimi-polku reverse (drop-while integer?) reverse vec)
                                                                          osan-polku-dataan (vec (drop (count nimipolku-ilman-loppuindexeja)
                                                                                                       (::index-polku osa)))
                                                                          solun-polun-pituus-oikein? (= solun-polun-pituus (count osan-polku-dataan))
                                                                          #_#__ (when (and grid-polku-sopii-osaan?
                                                                                       solun-polun-pituus-oikein?
                                                                                       (= rajapinta :yhteensa))
                                                                              (println "------->")
                                                                              (println nimipolku-ilman-loppuindexeja)
                                                                              (println osan-polku-dataan)
                                                                              (println @rajapintakasittelija)
                                                                              (println "<-------"))
                                                                          osan-derefable (r/cursor rajapintakasittelija osan-polku-dataan)]
                                                                      (when (and grid-polku-sopii-osaan?
                                                                                 solun-polun-pituus-oikein?)
                                                                        #_(when-not (or (= solun-polun-pituus (count osan-polku-dataan))
                                                                                        (nil? solun-polun-pituus))
                                                                            (warn (str "Osan " (or (gop/nimi osa) (gop/id osa)) " polku ei ole oikein."
                                                                                       " Nimi polku: " (::nimi-polku osa)
                                                                                       " Index polku: " (::index-polku osa))))
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
                                                                                                       (fn [] (dk/triggeroi-seuranta! datan-kasittelija seuranta)))))))
                                                                  grid-rajapintakasittelijat)]
                                        loydetty-osa
                                        osa)
                                      ::datan-kasittelija datan-kasittelija)))))
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
     [piirra-grid this]]))

;; data erilleen, aggregoi hommia datasta, merkkaa alueet aggregoitaviksi

;; Alue ottaa vektorin vektoreita (tai sittenkin mappeja?) dataa. Voi määritellä x- ja y-suunnissa, että kasvaako alue datan mukana vaiko ei.


(defn kopioi-grid [kopioitava-grid]
  (let [id (gensym "grid")
        gridi (assoc kopioitava-grid :id id
                     ::root-id id)
        kopioitavan-gridin-koko (get (p/koot kopioitava-grid) (gop/id kopioitava-grid))
        koko (r/atom (-> (p/koot kopioitava-grid)
                         (dissoc (gop/id kopioitava-grid))
                         (assoc id kopioitavan-gridin-koko)))
        koko-fn (fn [] koko)]
    (paivita-kaikki-lapset (assoc gridi ::koko-fn koko-fn)
                           (constantly true)
                           (fn [lapsi]
                             (assoc lapsi ::koko-fn koko-fn
                                    ::root-id id)))))

(defn grid-pohjasta [grid-pohja]
  (kopioi-grid grid-pohja))

(defn validi-grid-asetukset?
  [{:keys [nimi alueet koko jarjestys osat]}]
  (and (or (nil? nimi) (satisfies? IEquiv nimi))
       (or (nil? alueet) (s/valid? ::alueet alueet))
       (or (nil? koko) (s/valid? ::koko koko))
       (or (nil? jarjestys) (s/valid? ::jarjestys jarjestys))
       (or (nil? osat) (s/valid? ::osat osat))))

(defn grid-c [record {:keys [nimi alueet koko osat root-fn] :as asetukset}]
  (let [root-id (gensym "grid")
        koko (r/atom {root-id koko})
        koko-fn (constantly koko)
        gridi (cond-> (record root-id)
                      true (assoc ::koko-fn koko-fn
                                  ::root-fn root-fn)
                      nimi (assoc ::nimi nimi)
                      alueet (assoc ::alueet alueet)
                      osat (assoc ::osat osat))
        index-polut (grid-index-polut gridi)
        nimi-polut (grid-nimi-polut gridi)]
    (walk/postwalk (fn [x]
                    (if (satisfies? gop/IGridOsa x)
                      (let [koot (when (satisfies? p/IGrid x)
                                   (p/koot x))
                            id (gop/id x)
                            _ (when koot
                                (swap! koko (fn [koko]
                                              (merge koko koot))))]
                        (assoc x ::koko-fn koko-fn
                               ::root-id root-id
                               ::root-fn root-fn
                               ::index-polku (get index-polut id)
                               ::nimi-polku (get nimi-polut id)))
                      x))
                  gridi)))

(defn grid
  [{:keys [nimi alueet koko osat root-fn] :as asetukset}]
  {:pre [(validi-grid-asetukset? asetukset)]
   :post [(instance? Grid %)
          (symbol? (gop/id %))]}
  (grid-c ->Grid asetukset))
