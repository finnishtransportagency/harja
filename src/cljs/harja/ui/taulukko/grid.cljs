(ns harja.ui.taulukko.grid
  (:require [harja.ui.taulukko.grid-protokollat :as p]
            [harja.ui.taulukko.datan-kasittely-protokollat :as dp]
            [harja.ui.taulukko.datan-kasittely :as dk]
            [harja.loki :refer [log warn]]
            [cljs.spec.alpha :as s]
            [clojure.string :as clj-str]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [clojure.walk :as walk]))


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
        [css-top css-left] [(if (< aloitus-rivi 0)
                              (take (js/Math.abs aloitus-rivi) rivi-korkeudet)
                              ["0px"])
                            (if (< aloitus-sarake 0)
                              (take (js/Math.abs aloitus-sarake) sarake-leveydet)
                              ["0px"])]]
    {:korkeudet (apply str rivi-korkeudet)
     :leveydet (apply str sarake-leveydet)
     :top (str "calc(" (apply str (interpose " + " css-top)) ")")
     :left (str "calc(" (apply str (interpose " + " css-left)) ")")}))

(defn- oikea-osa? [osa etsittava-osa]
  (let [id? (symbol? etsittava-osa)]
    (if id?
      (p/id? osa etsittava-osa)
      (= (p/nimi osa) etsittava-osa))))

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
   (let [kaikki-gridin-lapset (filter pred (p/lapset grid))
         aloitus (if (pred grid)
                   [(f grid)]
                   [])]
     (gridin-osat-vektoriin aloitus pred f kaikki-gridin-lapset)))
  ([osat pred f lapset]
   (let [kaikki-lapsen-lapset (sequence (comp (mapcat p/lapset)
                                              (remove nil?))
                                        lapset)
         oikeat-osat (reduce (fn [osat osa]
                               (when (pred osa)
                                 (conj osat (f osa))))
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



(defn aseta-seurattava-koko! [grid]
  ;;{:seurattava :otsikko
  ;  :sarakkeet <:sama | {:foo []} | {:foo int}>
  ;  :rivit :sama}
  (let [koko-conf (p/koko grid)
        {seurattavan-koon-nimi :seurattava
         seurattava-sarakkeet :sarakkeet
         seurattava-rivit :rivit} (get koko-conf :seuraa)
        seurattava-koko (-> grid ::koko deref (get seurattavan-koon-nimi))
        muodosta-css-arvot-seurattavasta (fn [seurattavan-css-arvo suhdeluvut]
                                          (let [luku (js/Number (first (re-find #"[0-9]*(\.[0-9]+)?" seurattavan-css-arvo)))
                                                yksikko (re-find #"[^0-9\.]*$" seurattavan-css-arvo)
                                                suhdeluvut-oikein? (fn [suhdeluvut]
                                                                     (= 100 (apply + suhdeluvut)))
                                                _ (when-not (suhdeluvut-oikein? suhdeluvut)
                                                    (warn (str "Suhdelukujen summa ei ole 100 gridille"
                                                               (when-let [gridin-nimi (p/nimi grid)]
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
                                      (when-let [gridin-nimi (p/nimi grid)]
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
                                (let [grid-id (p/id grid)]
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
                            (when-let [gridin-nimi (p/nimi grid)]
                              (str " " gridin-nimi))
                            ". Käytetään oletusarvoja."))
                 oletus-koko)
               (koko-conf->koko koko-conf))]
    (p/aseta-koko! grid koko)))

(defn aseta-koot! [root-grid]
  (println "DATAGRID")
  (cljs.pprint/pprint (dp/root (::data root-grid)))
  (let [kokojen-seuranta (gridin-osat-vektoriin root-grid
                                                #(satisfies? p/IGrid %)
                                                (fn [grid]
                                                  (println "TYYPPI: " (type grid))
                                                  (let [koko (p/koko grid)
                                                        seurattavan-gridin-nimi (get-in koko [:seuraa :seurattava])
                                                        _ (println "SEURATTAVAN GRIDIN NIMI: " seurattavan-gridin-nimi)
                                                        seurattava-grid (if seurattavan-gridin-nimi
                                                                          (etsi-osa (dp/root (::data grid)) seurattavan-gridin-nimi)
                                                                          grid)]
                                                    (when-not (and (satisfies? p/IGridOsa grid)
                                                                   (satisfies? p/IGridOsa seurattava-grid))
                                                      (println "GRID: ")
                                                      (cljs.pprint/pprint grid)
                                                      (println "SEURATTAVA GRID: ")
                                                      (cljs.pprint/pprint seurattava-grid))
                                                    (println "SEURATTAVA GRID NIMI: " seurattavan-gridin-nimi)
                                                    [grid [(p/id grid) (p/id seurattava-grid)]])))
        _ (println "KOKOJEN SEURANTA: ")
        _ (cljs.pprint/pprint kokojen-seuranta)
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
                                                          (when-let [gridin-nimi (p/nimi root-grid)]
                                                            (str gridin-nimi " "))
                                                          "ja sen lasten kokojen seurannassa on kiertävä riippuvuus.")))))))]
    (doseq [[grid [id seurattava-id]] gridit-jarjestetty]
      (if (= id seurattava-id)
        (aseta-koko! grid)
        (aseta-seurattava-koko! grid)))
    #_(p/aseta-lapset root-grid root-gridin-lapset)))


(defn aseta-koko-grid [grid koko]
  (swap! (::koko grid)
         (fn [koot]
           (assoc koot (or (p/nimi grid) (p/id grid)) koko))))

(defn paivita-koko-grid [grid f]
  (swap! (::koko grid)
         (fn [koot]
           (update koot (or (p/nimi grid) (p/id grid)) f))))

(defn- piirra-gridin-osat [osat grid]
  [:<>
   (for [osa osat]
     (with-meta [p/piirra osa]
                {:key (p/id osa)}))])

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

(defn piirra-grid [grid]
  (fn [grid]
    (let [luokat (-> grid :parametrit :class)
          dom-id (-> grid :parametrit :id)
          seurattava-koko (when-let [seuraa-asetukset (:seuraa (p/koko grid))]
                            (r/cursor (::koko grid) [(:seurattava seuraa-asetukset)]))
          {:keys [korkeudet leveydet top left]} (laske-gridin-css-tiedot grid)]
      ;; Mikälisikäli seurattavan koko vaihtuu, niin tämä tulisi renderöidä uudestaan
      (when (and seurattava-koko
                 (= (:sarake @seurattava-koko) (:sarake (p/koko grid)))
                 (= (:rivi @seurattava-koko) (:rivi (p/koko grid))))
        (aseta-seurattava-koko! grid))
      (when (p/id? grid (p/id (dp/root (::data grid))))
        (println "TYYPPI: " (type (::data grid)))
        (p/gridin-pointterit! grid (::data grid)))
      (println "CSS tyylit: ")
      (cljs.pprint/pprint [korkeudet leveydet top left])
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
          [staattinen-grid grid])]])))

(defn grid-polut
  ([grid] (grid-polut grid [] [0]))
  ([grid polut taman-polku]
   (let [lapset (p/lapset grid)
         taman-polut (vec (map-indexed (fn [index lapsi]
                                         (if (satisfies? p/IGrid lapsi)
                                           (grid-polut lapsi (assoc-in polut taman-polku []) (conj taman-polku index))
                                           index))
                                       lapset))]
     (apply vector (assoc-in polut taman-polku taman-polut)))))

(defn grid-koko [grid]
  (let [koko @(::koko grid)]
    (get koko (p/nimi grid) (get koko (p/id grid)))))

(defn grid-koot [grid]
  @(::koko grid))

(defrecord Grid [id]
  p/IGrid
  (-solut [this]
    (::osat this))
  (-aseta-solut [this solut]
    (assoc this ::osat solut))
  (-paivita-solut [this f]
    (update this ::osat f))
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

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))
  p/IGridDataYhdistaminen
  (-gridin-pointterit! [this datan-kasittelija]
    ;; data : {.. ihmisen ymmärtämiä avaimia ..}
    ;; pointterit : {<:osan-id {:polku-dataan [..] :polut-pointtereihin [..]}>}

    ;; lapset on vektori osan-idtä.
    ;; Dynaamisesti generoidulle datalle pitänee generoida myös yksilöivä id sen datan löytämistä varten.
    ;; Nimipolut kaiketi pakollisia siihen asti, että lapsoset ovat struktuuriltaan identtisiä vanhemman sisäl.

    ;; ::data avaimen taakse kama tulee jostain (esim. backiltä).
    ;; ::pointterit avaimen taaksen pitäisi luoda :polku-dataan tässä.
    (let [datakasittelijan-rajapinta (dp/rajapinta datan-kasittelija)
          polut-dataan (walk/postwalk (fn [x]
                                        (if (satisfies? p/IGridOsa x)
                                          (let [id (p/id x)
                                                lapset (p/lapset x)
                                                lehti? (and id (empty? lapset))]
                                            (if lehti?
                                              [[id]]
                                              (vec (apply concat
                                                     (map-indexed (fn [index polut]
                                                                    (mapv (fn [polku]
                                                                            (conj polku index))
                                                                          polut))
                                                                  lapset)))))
                                          x))
                                      this)
          ]
      (println "POLUT DATAAN: ")
      (cljs.pprint/pprint polut-dataan)
      (doseq [[solun-id & polku] polut-dataan]
        (dp/aseta-pointteri! datan-kasittelija
                             solun-id
                             (vector (reverse polku))))))
  (-gridin-muoto! [this datan-kasittelija]
    (let [grid-polut (grid-polut this)]
      (dp/aseta-grid-polut! datan-kasittelija grid-polut)))
  (-rajapinta-grid-yhdistaminen! [this datan-kasittelija]
    (dp/aseta-grid-rajapinta! datan-kasittelija
      (into {}
            (gridin-osat-vektoriin this
                                   :rajapinnan-polku
                                   (fn [osa]
                                     [(:rajapinnan-polku osa) (p/id osa)])))))
  (-vanhempi [this datan-kasittelija solun-id]
    (let [polku-vanhempaan (vec (drop-last (get @(dp/pointterit datan-kasittelija) solun-id)))]
      (if (empty? polku-vanhempaan)
        this
        (loop [[index & loput-indexit] polku-vanhempaan
               lapset (p/lapset this)]
          (let [seuraava-solu (get lapset index)]
            (if (empty? loput-indexit)
              seuraava-solu
              (recur loput-indexit
                     (p/lapset seuraava-solu))))))))
  p/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::nimi nimi))
  p/IPiirrettava
  (-piirra [this]
    (println "PIIRRÄ grid")
    [:<>
     [piirra-grid this]]))

;; data erilleen, aggregoi hommia datasta, merkkaa alueet aggregoitaviksi

;; Alue ottaa vektorin vektoreita (tai sittenkin mappeja?) dataa. Voi määritellä x- ja y-suunnissa, että kasvaako alue datan mukana vaiko ei.



(defn validi-grid-asetukset?
  [{:keys [nimi alueet koko jarjestys osat]}]
  (and (or (nil? nimi) (satisfies? IEquiv nimi))
       (or (nil? alueet) (s/valid? ::alueet alueet))
       (or (nil? koko) (s/valid? ::koko koko))
       (or (nil? jarjestys) (s/valid? ::jarjestys jarjestys))
       (or (nil? osat) (s/valid? ::osat osat))))

(defn grid
  [{:keys [nimi alueet koko osat] :as asetukset}]
  {:pre [(validi-grid-asetukset? asetukset)]
   :post [(instance? Grid %)
          (symbol? (p/id %))]}
  (let [id (gensym "grid")
        koko (r/atom {id koko})
        gridi (cond-> (->Grid id)
                      nimi (assoc ::nimi nimi)
                      alueet (assoc ::alueet alueet)
                      osat (assoc ::osat osat))
        gridi (paivita-kaikki-lapset (assoc gridi ::koko koko)
                                     (fn [& _] true)
                                     (fn [lapsi]
                                       (let [koot (when (satisfies? p/IGrid lapsi)
                                                    (p/koot lapsi))
                                             _ (when koot
                                                 (swap! koko (fn [koko]
                                                               (merge koko koot))))
                                             lapsi (assoc lapsi ::koko koko)]
                                         lapsi)))]
    gridi))

(defn paa-grid!
  [data-atom data-atom-polku grid-asetukset lapset rajapinta datajarjestys]
  {:pre [(satisfies? ratom/IReactiveAtom data-atom)
         (vector? data-atom-polku)
         (every? #(satisfies? p/IGridOsa %) lapset)
         ;;TODO datajarjestys oikeellisuus
         ]
   :post [(instance? Grid %)]}
  (let [root-grid-ilman-datakasittelijaa (grid (assoc grid-asetukset :osat lapset))
        datan-kasittelija (dk/datan-kasittelija data-atom data-atom-polku datajarjestys rajapinta)
        root-grid (paivita-kaikki-lapset root-grid-ilman-datakasittelijaa
                                         (fn [& _] true)
                                         (fn [lapsi]
                                           (assoc lapsi ::data datan-kasittelija)))
        root-grid (assoc root-grid ::data datan-kasittelija)]
    (dp/aseta-root! datan-kasittelija root-grid-ilman-datakasittelijaa)
    (aseta-koot! root-grid)
    (println "ROOT GRID: " )
    (cljs.pprint/pprint root-grid)
    root-grid))

#_(let [header-jarjestys (atom [:header-1 :header-2 :header-3])]
  (paa-grid
    data-atom
    data-atom-polku
    {:nimi "FOOBAR"
     ;; Alueet
     :alueet [{:sarakkeet [3 6] :rivit [0 0]}
              {:sarakkeet [3 6] :rivit [1 10]}]
     ;; Koko
     :koko {:class "foo"}
     :jarjestys nil}

    ;; Gridin ei tulisi välittää minkälaista dataa sille annetaan. Se vain näyttää sen mitä sille välitetään.
    ;; Ongelmana kumminkin järjestäminen. Osien järjestäminen riippuu hyvinkin pitkälti datasta.
    ;; Jos dataa järjestetään niin itse osankin pitäisi silloin lähtä liikkeelle (kaiketi?). Jos näin
    ;; niin kun data liitetään osaan ensimmäisen kerran, niin sen pitäisi leechata siihen.


    ;; Osat
          [(rivi
             ;;Nimi
             :otsikko
             ;;Alueet
             [{:sarakkeet [0 3] :rivit [0 0]}]
             ;;Koko
             {:sarake {:oletus-leveys "5px"
                       :leveydet {1 "3px"}
                       :nimet {:foo 0}}
              :rivi {:oletus-korkeus "3px"
                     :korkeudet {2 "3px"}
                     :nimet {:bar 0}}}
             ;;Järjestys
             {:sarakkeet [:foo :bar :asd]
              :rivit ::ei-sorttia}
             ;;Osat
                   [(->Osa :header-1
                           ;;Datapolku
                           ;; Jos esim. aggregoidaan dataa, niin halutaan joku muu kasa dataa, kuin perus
                           )
                    (->Osa :header-2)
                    (->Osa :header-3)])
           (->Taulukko
             ;;Nimi
             :data
             ;;Alueet
             [{:sarakkeet [0 3] :rivit [0 0]}]
             ;;Koko
             {:seuraa {:seurattava :otsikko
                       :sarakkeet :sama
                       :rivit :sama}}
             ;;Järjestys
             {:sarakkeet (fn [])}
             ;;Osat

                       ^::dynaaminen? [(->Rivi :hederi-id
                                {:alue {:sarakkeet [0 3] :rivit [0 0]}}
                                [(->Osa :header-1
                                        )
                                 (->Osa :header-2)
                                 (->Osa :header-3)])])]
          #_[(->Alue :hederi-id
                   {:alue {:sarakkeet [3 6] :rivit [0 0]}
                    :jarjestys header-jarjestys}
                   [(->Osa :header-1
                           )
                    (->Osa :header-2)
                    (->Osa :header-3)])
           (->Alue :data-id
                   {:alue {:sarakkeet [3 6] :rivit [1 10]}
                    :jarjestys @header-jarjestys})]))
