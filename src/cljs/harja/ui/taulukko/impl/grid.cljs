(ns harja.ui.taulukko.impl.grid
  (:require [harja.ui.taulukko.protokollat.grid :as p]
            [harja.ui.taulukko.protokollat.solu :as sp]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]
            [harja.ui.taulukko.impl.datan-kasittely :as dk]
            [harja.virhekasittely :as virhekasittely]
            [harja.loki :refer [log warn error]]
            [cljs.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.string :as clj-str]
            [clojure.set :as clj-set]
            [reagent.core :as r]
            [harja.ui.grid-debug :as g-debug]
            [reagent.ratom :as ratom]
            [reagent.dom :as dom])
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
(s/def ::osat any?)

(defonce oletus-koko {:sarake {:oletus-leveys "1fr"}
                      :rivi {:oletus-korkeus "1fr"}})

(defonce taulukko-konteksti (atom nil))

(def ^:dynamic *ajetaan-tapahtuma?* true)
(def ^:dynamic *jarjesta-data* true)

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

(defn lue-gridin-koko-ilman-renderointia
  "Kaikki koot on yhden atomin takana. Kun sitä muuttaa, niin kaikki lapsigridit
   renderöidään, jos ne dereffaa kyseistä atomia. Tässä luetaan atomin arvo ilman, että
   siitä ilmoitetaan mitään reagentille."
  [grid]
  (get (.-state ((get-in @taulukko-konteksti [(::root-id grid) :koko-fn]))) (gop/id grid)))

(defn- muodosta-uusi-polku
  ([annettu-polku polun-alku] (muodosta-uusi-polku annettu-polku polun-alku false))
  ([annettu-polku polun-alku muodostetaan-polkua?]
   (let [ensimmainen-osa (first annettu-polku)]
     (cond
       (= :. ensimmainen-osa) (vec (concat polun-alku (rest annettu-polku)))
       (= :.. ensimmainen-osa) (recur (rest annettu-polku) (butlast polun-alku) true)
       muodostetaan-polkua? (vec (concat polun-alku annettu-polku))
       :else annettu-polku))))

(defn- laske-gridin-css-tiedot [grid]
  (let [grid-koko (lue-gridin-koko-ilman-renderointia grid)
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

(defn gridin-osat-vektoriin
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
  (let [lapset (p/lapset osa)
        paivitetyt-lapset (transduce (comp
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
                                     lapset)]
    (when-not (= paivitetyt-lapset lapset)
      (p/aseta-lapset! osa paivitetyt-lapset))
    osa))

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
  ((get-in @taulukko-konteksti [(::root-id osa) :root-fn])))

(defn paivita-root! [osa f]
  ((get-in @taulukko-konteksti [(::root-id osa) :paivita-root!]) f)
  (root osa))

(defn vanhempi [osa]
  (let [polku (::index-polku osa)]
    (when-not (empty? polku)
      (get-in-grid (root osa) (vec (drop-last polku))))))

(defn saman-muotoinen? [osa-a osa-b]
  (let [osa-a-runko (if (satisfies? p/IGrid osa-a)
                      (gridin-osat-vektoriin osa-a (constantly true) #(type %))
                      [(type osa-a)])
        osa-b-runko (if (satisfies? p/IGrid osa-b)
                      (gridin-osat-vektoriin osa-b (constantly true) #(type %))
                      [(type osa-b)])]
    (= osa-a-runko osa-b-runko)))


(defn aseta-seurattava-koko! [grid seurattava-id]
  (let [koko-conf (p/koko grid)
        seurattava-koko (get @((get-in @taulukko-konteksti [(::root-id grid) :koko-fn])) seurattava-id)
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
    (p/aseta-koko! grid koko)))

(defn muuta-id! [kopio]
  (let [id (gensym "muuta-id")
        osa (assoc kopio :id id)
        grid? (satisfies? p/IGrid kopio)
        root-grid? (gop/id? kopio (::root-id kopio))
        paivita-grid (fn [gridi]
                       (swap! ((get-in @taulukko-konteksti [(::root-id gridi) :koko-fn])) (fn [koot]
                                                                                            (assoc koot id (get koot (gop/id kopio)))))
                       (if root-grid?
                         (paivita-kaikki-lapset! (assoc gridi ::root-id id)
                                                 (constantly true)
                                                 (fn [lapsi]
                                                   (assoc lapsi ::root-id id)))
                         gridi))]
    (when root-grid?
      (swap! taulukko-konteksti
             (fn [konteksti]
               (-> konteksti
                   (assoc id (get konteksti (::root-id kopio)))
                   (dissoc (::root-id kopio))))))
    (if grid?
      (paivita-grid osa)
      osa)))

(defn aseta-koot! [root-grid]
  (let [kokojen-seuranta (gridin-osat-vektoriin root-grid
                                                #(satisfies? p/IGrid %)
                                                (fn [grid]
                                                  (let [koko (p/koko grid)
                                                        seurattavan-gridin-nimi (get-in koko [:seuraa :seurattava])
                                                        seurattava-grid (if seurattavan-gridin-nimi
                                                                          (etsi-osa ((get-in @taulukko-konteksti [(::root-id grid) :root-fn])) seurattavan-gridin-nimi)
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
    (doseq [[grid [id seurattava-id]] gridit-jarjestetty]
      (if (= id seurattava-id)
        (aseta-koko! grid)
        (aseta-seurattava-koko! grid seurattava-id)))))

(defn osa-polussa
  ([grid koko-polku] (osa-polussa grid koko-polku koko-polku))
  ([grid [polun-osa & polku] koko-polku]
   (if (nil? polun-osa)
     grid
     (let [loydetyt-osat (keep-indexed (fn [index lapsi]
                                         (when (or (= (gop/nimi lapsi) polun-osa)
                                                   (and (integer? polun-osa)
                                                        (= index polun-osa)))
                                           lapsi))
                                       (p/lapset grid))]
       (when-not (= 1 (count loydetyt-osat))
         (warn (str "Polun " koko-polku " osalle " polun-osa " ei löytynyt vain yhtä osaa. Löydetyt osat: " loydetyt-osat "\n"
                    "OSA: " (or (gop/nimi grid) "Nimetön") " (" (gop/id grid) ")" "\n"
                    "-> " (apply str (interpose "\n-> " (map (fn [lapsi]
                                                               (str (or (gop/nimi lapsi) "Nimetön") " (" (gop/id lapsi) ")"))
                                                             (p/lapset grid)))))))
       (recur (first loydetyt-osat) polku koko-polku)))))

(defn- grid-polku-sopii-osaan?
  ([grid-polku osa] (grid-polku-sopii-osaan? grid-polku osa true))
  ([grid-polku osa osan-polku-pitempi?]
   (let [nimi-polku (::nimi-polku osa)
         index-polku (::index-polku osa)
         osan-polun-pituus-poikein? (fn []
                                      (if osan-polku-pitempi?
                                        (>= (count nimi-polku) (count grid-polku))
                                        (<= (count nimi-polku) (count grid-polku))))]
     (and (every? true? (map (fn [gp np ip]
                               (or (= gp np)
                                   (= gp ip)))
                             grid-polku
                             nimi-polku
                             index-polku))
          (osan-polun-pituus-poikein?)))))

(defn- solun-polun-pituus-oikein?
  [grid-polku solun-polun-pituus osa]
  (= (+ (count grid-polku) solun-polun-pituus) (count (::nimi-polku osa))))

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

(defn muokkaa-data-syvyydessa
  ([syvyys data f] (muokkaa-data-syvyydessa syvyys data f []))
  ([syvyys data f datan-polku]
   (cond
     (= 0 syvyys) (f data datan-polku)
     (map-entry? data) (muokkaa-data-syvyydessa syvyys (val data) f datan-polku)
     (sequential? data) (map-indexed #(muokkaa-data-syvyydessa (dec syvyys) %2 f (conj datan-polku %1))
                                     data)
     (or (map? data)
         (record? data)) (reduce (fn [m me]
                                        (let [[k v] me]
                                          (assoc m k (muokkaa-data-syvyydessa (dec syvyys) me f (conj datan-polku k)))))
                                 data
                                 (seq data))
     :else data)))


(defn jarjesta-data
  "jarjestys: Oletusjärjstys, joka vastaa ::dk/jarjestys spekkiin.
   jarjestykset: Setti järjestyksistä, jotka pitäisi ajaa tai boolean joka kertoo, että ajetaanko kaikki tai ei mitään
   jarjestys-fns: Käyttäjän antama kustomijärjestys eri tasoille."
  [data jarjestys jarjestykset jarjestys-fns]
  (loop [[jarjestys & loput-jarjestykset] jarjestys
         syvyys 0
         tulos data]
    (if (nil? jarjestys)
      tulos
      (let [perusjarjestys? (or (true? jarjestykset)
                                (and (set? jarjestykset) (contains? jarjestykset (:nimi (meta jarjestys)))))
            kustomijarjestys? (fn? (get jarjestys-fns syvyys))]
        (cond
          kustomijarjestys? (recur loput-jarjestykset
                                   (inc syvyys)
                                   (muokkaa-data-syvyydessa syvyys
                                                            tulos
                                                            (fn [data _]
                                                              ((get jarjestys-fns syvyys) data))))
          perusjarjestys? (recur loput-jarjestykset
                                 (inc syvyys)
                                 (muokkaa-data-syvyydessa syvyys
                                                          tulos
                                                          (fn [data _]
                                                            (dk/jarjesta-data jarjestys data))))
          :else (recur loput-jarjestykset
                       (inc syvyys)
                       data))))))

(defn osan-jalkelainen?
  [vanhempi-osa-id osa]
  (let [vanhempi (vanhempi osa)
        vanhempi-osa? (and (not (nil? vanhempi))
                           (gop/id? vanhempi vanhempi-osa-id))]
    (if (and vanhempi (false? vanhempi-osa?))
      (recur vanhempi-osa-id vanhempi)
      vanhempi-osa?)))

(defn muuta-osan-root [osa tiedot-osa]
  (let [osa-grid? (satisfies? p/IGrid osa)]
    (if osa-grid?
      (do
        (swap! ((get-in @taulukko-konteksti [(::root-id tiedot-osa) :koko-fn]))
               (fn [koot]
                 (merge koot (p/koot osa))))
        (swap! taulukko-konteksti dissoc (:id osa))
        (paivita-kaikki-lapset! (assoc osa :koko nil ::root-id (::root-id tiedot-osa))
                                (constantly true)
                                (fn [lapsi]
                                  (assoc lapsi ::root-id (::root-id tiedot-osa)))))
      (assoc osa ::root-id (::root-id tiedot-osa)))))

(defn samanlainen-osa
  ([osa] (samanlainen-osa osa osa))
  ([osa tiedot-osa]
   (let [kopio (gop/kopioi osa)
         kopioitava-grid? (satisfies? p/IGrid osa)
         kopio-eri-idlla (if kopioitava-grid?
                           (paivita-kaikki-lapset! (muuta-id! kopio)
                                                   (constantly true)
                                                   (fn [lapsi]
                                                     (muuta-id! lapsi)))
                           (muuta-id! kopio))]
     (if kopioitava-grid?
       kopio-eri-idlla
       (assoc kopio-eri-idlla ::root-id (::root-id tiedot-osa))))))

(defn- jarjesta-cachen-mukaan [data cache identiteetti]
  (println "jarjesta-cachen-mukaan")
  (println "-> data: " data)
  (println "-> cache: " cache)
  (println "-> tulos: " (reduce-kv (fn [data syvyys jarjestykset]
                                     (muokkaa-data-syvyydessa (dec syvyys)
                                                              data
                                                              (fn [data _]
                                                                (let [order-map (if (= 1 syvyys)
                                                                                  jarjestykset
                                                                                  (first jarjestykset))
                                                                      data (if (map? data)
                                                                             (merge (zipmap (keys order-map)
                                                                                            (repeat nil))
                                                                                    data)
                                                                             data)]
                                                                  (sort-by (fn [x]
                                                                             (get order-map ((get identiteetti syvyys) x)))
                                                                           data)))))
                                   data
                                   cache))
  (reduce-kv (fn [data syvyys jarjestykset]
               (muokkaa-data-syvyydessa (dec syvyys)
                                        data
                                        (fn [data _]
                                          (let [order-map (if (= 1 syvyys)
                                                            jarjestykset
                                                            (first jarjestykset))
                                                data (if (map? data)
                                                       (merge (zipmap (keys order-map)
                                                                      (repeat nil))
                                                              data)
                                                       data)]
                                            (sort-by (fn [x]
                                                       (get order-map ((get identiteetti syvyys) x)))
                                                     data)))))
             data
             cache))

(defn- jarjestys-mappiin [tulos cache-atom]
  (println "jarjestys-mappiin: " (reduce-kv (fn [m syvyys jarjestykset]
                                              (assoc m
                                                     syvyys
                                                     (muokkaa-data-syvyydessa (dec syvyys)
                                                                              jarjestykset
                                                                              (fn [jarjestys _]
                                                                                (zipmap jarjestys
                                                                                        (range))))))
                                            {}
                                            tulos))
  (reset! cache-atom
          (reduce-kv (fn [m syvyys jarjestykset]
                       (assoc m
                              syvyys
                              (muokkaa-data-syvyydessa (dec syvyys)
                                                       jarjestykset
                                                       (fn [jarjestys _]
                                                         (zipmap jarjestys
                                                                 (range))))))
                     {}
                     tulos)))

(defn- tallenna-jarjestys-cacheen [jarjestetty-data cache-atom identiteetti]
  (loop [[[syvyys identity-fn] & loput] (into (sorted-map) identiteetti)
         tulos {}]
    (if (nil? syvyys)
      (do (println "tallenna-jarjestys-cacheen TULOS: " tulos)
          (jarjestys-mappiin tulos cache-atom))
      (let [identity-fn (fn [arvo _] (identity-fn arvo))
            muokattu-tulos (muokkaa-data-syvyydessa syvyys
                                                    jarjestetty-data
                                                    identity-fn)]
        (recur loput
               (assoc tulos
                      syvyys (walk/postwalk (fn [x]
                                              (if (or (map? x)
                                                      (record? x))
                                                (vals x)
                                                x))
                                            muokattu-tulos)))))))

(defn- muuta-mapit-sequksi [x]
  (walk/postwalk (fn [x]
                   (if (or (map? x)
                           (record? x))
                     (seq x)
                     x))
                 x))

(defn jarjesta!
  ([grid] (jarjesta! grid nil))
  ([grid polku]
   (let [rajapintakasittelijat (::grid-rajapintakasittelijat (root grid))
         gridin-nimipolku (or polku (::nimi-polku grid))]
     (println "##################################")
     (println "jarjesta!")
     (println "-> rajapintakasittelijat " (keys rajapintakasittelijat))
     (println "-> gridin-nimipolku " gridin-nimipolku)
     (println "-> jarjesty-trigger: " (get-in rajapintakasittelijat [gridin-nimipolku ::jarjestys-trigger]))
     (when-let [trigger-atom (get-in rajapintakasittelijat [gridin-nimipolku ::jarjestys-trigger])]
       (swap! trigger-atom
              (fn [entinenen-arvo]
                (try (inc entinenen-arvo) (catch :default _ 0))))))))

(defn- rajapinnan-grid-kasittelija
  [grid grid-kasittelijan-polku {:keys [rajapinta jarjestys datan-kasittely tunnisteen-kasittely] :as kasittelija}]
  (let [datan-kasittelija (get-in @taulukko-konteksti [(gop/id grid) :datan-kasittelija])]
    (when g-debug/GRID_DEBUG
      (when (nil? (dk/rajapinnan-kuuntelija datan-kasittelija rajapinta))
        (warn (str "Rajapinalle " rajapinta " ei ole määritetty kuuntelijaa datan käsittlijään!\n"
                   "Annettu rajapinta: " rajapinta ". Olemassa olevat:\n"
                   (pr-str (keys (:kuuntelijat datan-kasittelija)))))))
    (let [jarjestyksen-cache (atom nil)
          jarjestys-fns (r/atom {})
          ;; Tällä ei ole muuta tarkoitusta kuin triggeröidä jarjestetty-rajapinta
          jarjestys-trigger (r/atom 0)
          jarjestetty? (atom false)
          jarjestetty-rajapinta (reaction (let [{rajapinnan-data :data rajapinnan-meta :meta identiteetti :identiteetti} (when-let [kuuntelija (dk/rajapinnan-kuuntelija datan-kasittelija rajapinta)]
                                                                                                                           @kuuntelija)
                                                jarjestys-fns @jarjestys-fns
                                                jarjestetaan? (boolean (and *jarjesta-data* (or jarjestys (not (empty? jarjestys-fns)))))
                                                _ (println "*jarjesta-data* " *jarjesta-data*)
                                                _ (println "jarjestys: " jarjestys)
                                                rajapinnan-dataf (cond
                                                                   jarjestetaan? (jarjesta-data rajapinnan-data jarjestys *jarjesta-data* jarjestys-fns)
                                                                   (not (nil? @jarjestyksen-cache)) (jarjesta-cachen-mukaan rajapinnan-data @jarjestyksen-cache identiteetti)
                                                                   :else rajapinnan-data)]
                                            (when (= :deep *jarjesta-data*)
                                              (loop [polku (vec (butlast grid-kasittelijan-polku))]
                                                (when-not (empty? polku)
                                                  (binding [*jarjesta-data* true]
                                                    (jarjesta! grid polku))
                                                  (recur (vec (butlast polku))))))
                                            @jarjestys-trigger
                                            (println "rajapinnan-data: " rajapinnan-data)
                                            (println (cond
                                                       jarjestetaan? "jarjestetaan?"
                                                       (not (nil? @jarjestyksen-cache)) "@jarjestyksen-cache"
                                                       :else ":else"))
                                            (reset! jarjestetty? jarjestetaan?)
                                            (when (and jarjestetaan? identiteetti)
                                              (tallenna-jarjestys-cacheen rajapinnan-dataf jarjestyksen-cache identiteetti))
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
                                            (if (and rajapinnan-meta (satisfies? IWithMeta rajapinnan-dataf))
                                              (with-meta (muuta-mapit-sequksi rajapinnan-dataf)
                                                         rajapinnan-meta)
                                              (muuta-mapit-sequksi rajapinnan-dataf))))
          rajapintakasittelija (seuranta (reaction (datan-kasittely @jarjestetty-rajapinta)))
          tunnisteen-kasittely (or tunnisteen-kasittely (constantly nil))]
      (assoc kasittelija
             ::jarjestetty? jarjestetty?
             ::jarjestetty-rajapinta jarjestetty-rajapinta
             ::jarjestys-fns jarjestys-fns
             ::jarjestys-trigger jarjestys-trigger
             :rajapintakasittelija rajapintakasittelija
             :osien-tunnisteet (reaction (println "@jarjestetty-rajapinta " @jarjestetty-rajapinta) (tunnisteen-kasittely (osa-polussa grid grid-kasittelijan-polku) @jarjestetty-rajapinta))))))

(defn- osan-data-yhdistaminen [grid-rajapintakasittelijat osa]
  (let [datan-kasittelija (get-in @taulukko-konteksti [(::root-id osa) :datan-kasittelija])]
    (if-let [loydetty-osa (some (fn [[grid-polku {:keys [rajapintakasittelija osien-tunnisteet solun-polun-pituus seuranta rajapinta]}]]
                                  (let [grid-polku-sopii-osaan? (grid-polku-sopii-osaan? grid-polku osa)
                                        osan-polku-dataan (vec (take-last solun-polun-pituus (::index-polku osa)))
                                        solun-polun-pituus-oikein? (solun-polun-pituus-oikein? grid-polku solun-polun-pituus osa)
                                        yhdista-derefable-tahan-osaan? (or (and grid-polku-sopii-osaan?
                                                                                solun-polun-pituus-oikein?)
                                                                           (and (:derefable (meta grid-polku))
                                                                                grid-polku-sopii-osaan?))]
                                    (when yhdista-derefable-tahan-osaan?
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
                                               ::tunniste-rajapinnan-dataan (fn [] (get-in @osien-tunnisteet osan-polku-dataan))
                                               ::triggeroi-seuranta! (when seuranta
                                                                       (fn [] (dk/triggeroi-seuranta! datan-kasittelija seuranta))))))))
                                grid-rajapintakasittelijat)]
      loydetty-osa
      osa)))

(declare aseta-gridin-polut)

(defn uudet-gridkasittelijat-dynaaminen [grid grid-kasittelijat]
  (reduce-kv (fn [m polku kasittelija]
               (assoc m polku (rajapinnan-grid-kasittelija (root grid) polku kasittelija)))
             {}
             grid-kasittelijat))

(defn lisaa-osia! [grid uudet-grid-kasittelijat toistettavan-osan-data]
  (p/paivita-lapset! grid
                     (fn [_]
                       (mapv (fn [osa]
                               (with-meta (muuta-osan-root osa (root grid))
                                          (meta osa)))
                             ((:toistettava-osa grid) toistettavan-osan-data))))
  (paivita-root! grid (fn [vanha-grid]
                        (aseta-gridin-polut vanha-grid)))
  (let [rajapintakasittelijat (::grid-rajapintakasittelijat (root grid))
        lisattavat-kasittelijat (into {}
                                      (keep (fn [[polku kasittelija]]
                                              (when-not (some (fn [[olemassa-oleva-polku olemassa-oleva-kasittelija]]
                                                                (and (= olemassa-oleva-polku polku)
                                                                     (= (:rajapinta olemassa-oleva-kasittelija)
                                                                        (:rajapinta kasittelija))))
                                                              rajapintakasittelijat)
                                                [polku kasittelija]))
                                            uudet-grid-kasittelijat))
        uudet-rajapintakasittelijat (when-not (empty? lisattavat-kasittelijat)
                                      (uudet-gridkasittelijat-dynaaminen grid lisattavat-kasittelijat))
        _ (when uudet-rajapintakasittelijat
            (paivita-root! grid (fn [vanha-grid]
                                  (update vanha-grid ::grid-rajapintakasittelijat (fn [rk]
                                                                                    (merge rk
                                                                                           uudet-rajapintakasittelijat))))))
        osan-kasittely (fn [osa]
                         (osan-data-yhdistaminen uudet-rajapintakasittelijat osa))]
    (if uudet-rajapintakasittelijat
      (paivita-kaikki-lapset! (osan-kasittely grid)
                              (constantly true)
                              osan-kasittely)
      (when g-debug/GRID_DEBUG
        (warn (str "Osalle " (gop/nimi grid) " (" (gop/id grid) ") polussa: " (::nimi-polku grid) " ei määritetty uusia rajapintakäsittelijöitä vaikka osat muuttui!\n"))))))

(defn poista-osia! [grid luodut-grid-kasittelijat]
  (let [polun-alku (::nimi-polku grid)
        rajapintakasittelijat (::grid-rajapintakasittelijat (root grid))
        poistettavat-kasittelijat (keep (fn [[polku kasittelija]]
                                          (let [dynaamisesti-luotu-kasittelija? (and (= (take (count polun-alku) polku) polun-alku)
                                                                                     (not (= polku polun-alku)))]
                                            (when (and dynaamisesti-luotu-kasittelija?
                                                       (not (some (fn [[luotu-polku luotu-conf]]
                                                                    (and (= luotu-polku polku)
                                                                         (= (:rajapinta luotu-conf)
                                                                            (:rajapinta kasittelija))))
                                                                  luodut-grid-kasittelijat)))
                                              [polku kasittelija])))
                                       rajapintakasittelijat)]
    (when-not (empty? poistettavat-kasittelijat)
      (paivita-kaikki-lapset! grid
                              (fn [osa]
                                (when-let [osan-derefable (::osan-derefable osa)]
                                  (some #(= (:r (:rajapintakasittelija (second %))) (.-ratom osan-derefable))
                                        poistettavat-kasittelijat)))
                              (fn [osa]
                                (assoc osa ::osan-derefable (r/atom nil)))))
    (when-not (empty? poistettavat-kasittelijat)
      (doseq [[_ {poistettava-kasittelija :rajapintakasittelija}] poistettavat-kasittelijat]
        (if (= 0 (:n poistettava-kasittelija))
          (ratom/dispose! (:r poistettava-kasittelija))
          (while (> (:n poistettava-kasittelija) 0)
            (poista-seuranta-derefable! poistettava-kasittelija))))
      (paivita-root! grid (fn [vanha-grid]
                            (update vanha-grid ::grid-rajapintakasittelijat (fn [rk]
                                                                              (apply dissoc rk (map first poistettavat-kasittelijat)))))))))

(defn- piirra-gridin-osat [osat grid]
  [:<>
   (doall
     (for [osa osat]
       (with-meta [gop/piirra osa]
                  {:key (or (-> osa meta :key) (gop/id osa))})))])

(defn- piirra-dynamic-gridin-osat [_ _]
  (r/create-class
    {:should-component-update (fn [_ vanhat uudet]
                                (let [[_ _ piirra?] uudet]
                                  piirra?))
     :display-name "piirra-dynamic-gridin-osat"
     :reagent-render (fn [grid _]
                       [:<>
                        (doall
                          (for [osa (p/lapset grid)]
                            ^{:key (gop/id osa)}
                            [gop/piirra osa]))])}))

(defn lisaa-uuden-osan-rajapintakasittelijat [uusi-osa & gridkasittelijat]
  (let [polun-alku (::index-polku uusi-osa)
        uudet-gridkasittelijat (reduce (fn [m [polku kasittelija]]
                                         (let [polku (muodosta-uusi-polku polku polun-alku)]
                                           (assoc m polku (rajapinnan-grid-kasittelija (root uusi-osa) polku kasittelija))))
                                       {}
                                       (partition 2 gridkasittelijat))
        osan-kasittely (fn [osa]
                         (osan-data-yhdistaminen uudet-gridkasittelijat osa))
        uusi-grid (paivita-root! uusi-osa (fn [vanha-grid]
                                             (update vanha-grid ::grid-rajapintakasittelijat (fn [rk]
                                                                                               (merge rk
                                                                                                      uudet-gridkasittelijat)))))]
    (paivita-kaikki-lapset! uusi-grid
                            (fn [osa]
                              (gop/id? osa (gop/id uusi-osa)))
                            (fn [uusi-osa]
                              (if (satisfies? p/IGrid uusi-osa)
                                (paivita-kaikki-lapset! (osan-kasittely uusi-osa)
                                                        (constantly true)
                                                        osan-kasittely)
                                (osan-kasittely uusi-osa))))))

(defn poista-osan-rajapintakasittelijat [osa]
  (let [root-grid (root osa)
        poistettavat-kasittelijat (keep (fn [[polku kasittelija]]
                                          (let [oikea-polku? (grid-polku-sopii-osaan? polku osa false)]
                                            (when oikea-polku?
                                              [polku kasittelija])))
                                        (::grid-rajapintakasittelijat root-grid))]
    (when-not (empty? poistettavat-kasittelijat)
      (paivita-kaikki-lapset! (assoc osa ::osan-derefable (r/atom nil))
                              (constantly true)
                              (fn [osa]
                                (assoc osa ::osan-derefable (r/atom nil))))
      (doseq [[_ {poistettava-kasittelija :rajapintakasittelija}] poistettavat-kasittelijat]
        (if (= 0 (:n poistettava-kasittelija))
          (ratom/dispose! (:r poistettava-kasittelija))
          (while (> (:n poistettava-kasittelija) 0)
            (poista-seuranta-derefable! poistettava-kasittelija))))
      (paivita-root! root-grid
                     (fn [vanha-grid]
                       (update vanha-grid ::grid-rajapintakasittelijat (fn [rk]
                                                                         (apply dissoc rk (map first poistettavat-kasittelijat)))))))))

(defn kasittele-osien-ja-rajapintakasittelijoiden-muutos! [grid toistettavan-osan-data toistettava-data-muuttunut? uudet-grid-kasittelijat]
  (let [polun-alku (::nimi-polku grid)
        datan-maara (count uudet-grid-kasittelijat)
        osien-maara (count (p/lapset grid))
        luodut-grid-kasittelijat (reduce-kv (fn [m polku kasittelija]
                                              (let [polku (muodosta-uusi-polku polku polun-alku)]
                                                (when g-debug/GRID_DEBUG
                                                  (when (some #(and (integer? %) (< % 0)) polku)
                                                    (warn "Grid käsittelijän luonnissa on uudessa polussa arvo nil:\n"
                                                          (str polku kasittelija))))
                                                (assoc m polku kasittelija)))
                                            {}
                                            (apply merge uudet-grid-kasittelijat))]
    (when toistettava-data-muuttunut?
      (poista-osia! grid luodut-grid-kasittelijat)
      (lisaa-osia! grid luodut-grid-kasittelijat toistettavan-osan-data)
      (aseta-koot! (root grid))
      (when-let [f (and (js/Math.max 0 (- osien-maara datan-maara)) (get-in grid [:osien-maara-muuttui :osien-maara-muuttui!]))]
        (f grid))
      (swap! (get-in grid [:osien-maara-muuttui :trigger]) not))))

(defn- kasittele-jarjestyksen-muutos [grid uudet-grid-kasittelijat rajapintakasittelijan-tiedot]
  (let [rajapintakasittelijat (::grid-rajapintakasittelijat (root grid))
        polun-alku (::nimi-polku grid)
        rajapintakasittelijat (merge rajapintakasittelijat
                                     (reduce (fn [gks-uudet gks]
                                               (merge gks-uudet
                                                      (into {}
                                                            (map (fn [[polku kasittelija]]
                                                                   [(muodosta-uusi-polku polku polun-alku) (some (fn [[_ {:keys [rajapinta] :as rajapintakasittelija}]]
                                                                                                                   (when (= rajapinta (:rajapinta kasittelija))
                                                                                                                     rajapintakasittelija))
                                                                                                                 rajapintakasittelijat)]))
                                                            gks)))
                                             {}
                                             uudet-grid-kasittelijat))
        osan-kasittely (fn [osa]
                         (osan-data-yhdistaminen rajapintakasittelijat osa))]
    (paivita-root! grid (fn [vanha-grid]
                          (assoc vanha-grid ::grid-rajapintakasittelijat rajapintakasittelijat)))
    ;; TODO ei tarvitse kaikkia oikeasti päivittää vaan vain tarvittava osa
    (paivita-kaikki-lapset! (osan-kasittely grid)
                            (constantly true)
                            osan-kasittely)
    (reset! (get rajapintakasittelijan-tiedot ::jarjestetty?) false)))

(defn- dynaaminen-grid [grid]
  (let [rajapintakasittelijan-tiedot (get (::grid-rajapintakasittelijat (root grid))
                                          (::nimi-polku grid))
        grid-kasittelijoiden-luonti (or (:luonti rajapintakasittelijan-tiedot)
                                        (constantly nil))
        entinen-toistettavan-osan-data (atom nil)]
    (fn [grid]
      (let [gridin-derefable (if-let [gridin-derefable (::osan-derefable grid)]
                               gridin-derefable
                               (do
                                 (warn (str "Dynaamiselle gridille  ") (pr-str (type grid)) (str " (" (:id grid) ") ei ole annettu osan-derefablea!"))
                                 (r/atom nil)))
            gridin-data @gridin-derefable
            rajapinnat-muuttunut? (if (and (contains? (meta grid-kasittelijoiden-luonti) :rajapinta-riippuu-datan-arvosta?)
                                           (-> grid-kasittelijoiden-luonti meta :rajapinta-riippuu-datan-arvosta? not))
                                    (not= (count @entinen-toistettavan-osan-data)
                                          (count gridin-data))
                                    true)
            data-jarjestetty? @(get rajapintakasittelijan-tiedot ::jarjestetty?)]
        (if rajapinnat-muuttunut?
          (let [uudet-grid-kasittelijat (grid-kasittelijoiden-luonti gridin-data)
                rajapintakasittelijat-muuttunut? (not (clj-set/subset? (transduce (map #(-> % vals first :rajapinta))
                                                                                  conj
                                                                                  #{}
                                                                                  uudet-grid-kasittelijat)
                                                                       (transduce (map #(:rajapinta %))
                                                                                  conj
                                                                                  #{}
                                                                                  (vals (::grid-rajapintakasittelijat (root grid))))))
                toistettavan-osan-data (:toistettavan-osan-data grid)
                toistettavan-osan-data (toistettavan-osan-data gridin-data)
                toistettava-data-muuttunut? (not= toistettavan-osan-data @entinen-toistettavan-osan-data)]
            (when rajapintakasittelijat-muuttunut?
              (kasittele-osien-ja-rajapintakasittelijoiden-muutos! grid toistettavan-osan-data toistettava-data-muuttunut? uudet-grid-kasittelijat))
            (when (or rajapintakasittelijat-muuttunut?
                       data-jarjestetty?)
              (kasittele-jarjestyksen-muutos grid uudet-grid-kasittelijat rajapintakasittelijan-tiedot))
            (when toistettava-data-muuttunut?
              (reset! entinen-toistettavan-osan-data toistettavan-osan-data))
            ^{:key (or (-> grid meta :key) (gop/id grid))}
            [piirra-dynamic-gridin-osat grid rajapintakasittelijat-muuttunut?])
          ^{:key (or (-> grid meta :key) (gop/id grid))}
          [piirra-dynamic-gridin-osat grid false])))))


(defn- staattinen-grid [grid]
  (let [osat (p/lapset grid)]
    [:<>
     (for [osa osat]
       ^{:key (or (-> osa meta :key) (str (gop/id osa)))}
       [gop/piirra osa])]))

(defn seurattava-koko [grid seurattava]
  (let [seurattavan-id (gop/id (etsi-osa ((get-in @taulukko-konteksti [(::root-id grid) :root-fn])) seurattava))
        kursori (r/cursor ((get-in @taulukko-konteksti [(::root-id grid) :koko-fn])) [seurattavan-id])]
    (reaction @kursori)))


(defn grid-index-polut
  ([grid] (grid-index-polut (p/lapset grid) [] {}))
  ([osat polku polut]
   (apply merge
          (map-indexed (fn [index osa]
                         (let [grid? (satisfies? p/IGrid osa)
                               uusi-polku (conj polku index)
                               id (gop/id osa)
                               polku-osalle (assoc polut id uusi-polku)]
                           (if (and grid? (not (empty? (p/lapset osa))))
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
                           (if (and grid? (not (empty? (p/lapset osa))))
                             (grid-nimi-polut (p/lapset osa) uusi-polku polku-osalle)
                             polku-osalle)))
                       osat))))

(defn aseta-koko-grid [grid koko]
  (swap! ((get-in @taulukko-konteksti [(::root-id grid) :koko-fn]))
         (fn [koot]
           (assoc koot (gop/id grid) koko))))

(defn paivita-koko-grid [grid f]
  (swap! ((get-in @taulukko-konteksti [(::root-id grid) :koko-fn]))
         (fn [koot]
           (update koot (gop/id grid) f))))

(defn grid-koko [grid]
  (get @((get-in @taulukko-konteksti [(::root-id grid) :koko-fn])) (gop/id grid)))

(defn grid-koot [grid]
  @((get-in @taulukko-konteksti [(::root-id grid) :koko-fn])))

(defn lisaa-rivi!
  ([root-grid rivi]
   (let [index-polku-viimeiseen (->> (gridin-osat-vektoriin root-grid (constantly true) identity)
                                     (sort-by ::index-polku compare)
                                     last
                                     butlast
                                     vec)]
     (lisaa-rivi! root-grid rivi (update index-polku-viimeiseen (dec (count index-polku-viimeiseen)) inc))))
  ([root-grid rivi index-vector]
   {:pre [(satisfies? p/IGrid root-grid)
          (every? #(satisfies? p/IGrid %)
                  (p/lapset root-grid))
          (satisfies? p/IGrid rivi)
          (every? #(integer? %) index-vector)]}
   (let [g (get-in-grid root-grid (vec (butlast index-vector)))
         index (last index-vector)
         paivita-koko-fn (fn [osa]
                           (let [grid? (satisfies? p/IGrid osa)
                                 osan-koko (when grid?
                                             (p/koko osa))
                                 id (gop/id osa)
                                 _ (when osan-koko
                                     (swap! ((get-in @taulukko-konteksti [(::root-id root-grid) :koko-fn]))
                                            (fn [koko]
                                              (assoc koko id osan-koko))))]
                             (assoc osa :koko nil)))
         rivi (paivita-kaikki-lapset! (paivita-koko-fn rivi)
                                      (constantly true)
                                      (fn [osa]
                                        (paivita-koko-fn osa)))
         rivi (paivita-kaikki-lapset! (assoc rivi ::root-id (::root-id root-grid))
                                      (constantly true)
                                      (fn [lapsi]
                                        (assoc lapsi ::root-id (::root-id root-grid))))]
     (p/paivita-alueet! g (fn [alueet]
                            (mapv (fn [alue]
                                    (update alue :rivit (fn [[alku loppu]]
                                                          [alku (inc loppu)])))
                                  alueet)))
     (p/paivita-lapset! g (fn [lapset]
                            (mapv (fn [i]
                                    (cond
                                      (< i index) (get lapset i)
                                      (= i index) rivi
                                      (> i index) (get lapset (inc i))))
                                  (range (inc (count lapset))))))
     (aseta-gridin-polut root-grid))))

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


(defn aseta-gridin-polut [gridi]
  (let [index-polut (grid-index-polut gridi)
        nimi-polut (grid-nimi-polut gridi)]
    (paivita-kaikki-lapset! (assoc gridi
                                   ::index-polku []
                                   ::nimi-polku [])
                            (constantly true)
                            (fn [osa]
                              (let [grid? (satisfies? p/IGrid osa)
                                    id (gop/id osa)]
                                (assoc osa
                                       ::index-polku (get index-polut id)
                                       ::nimi-polku (get nimi-polut id)))))))

(defn poista-rivi! [grid rivi]
  {:pre [(satisfies? p/IGrid grid)
         (satisfies? p/IGrid rivi)]
   :post [(nil? (etsi-osa grid (gop/id rivi)))]}
  (let [grid (aseta-gridin-polut grid)
        rivi (etsi-osa grid (gop/id rivi))
        rivin-vanhempi (vanhempi rivi)
        rivin-index-polku (::index-polku rivi)]
    (post-walk-grid! grid
                     (fn [osa]
                       (let [osan-index-polku (::index-polku osa)
                             osa-solun-jalkelainen? (and (>= (count osan-index-polku)
                                                             (count rivin-index-polku))
                                                         (every? true?
                                                                 (map #(= %1 %2)
                                                                      rivin-index-polku
                                                                      osan-index-polku)))
                             root-grid? (gop/id? osa (::root-id osa))]
                         (when osa-solun-jalkelainen?
                           (when-not root-grid?
                             (p/paivita-lapset! (vanhempi osa)
                                                (fn [lapset]
                                                  (vec
                                                    (keep (fn [lapsi]
                                                            (when (and (gop/id? lapsi (gop/id osa))
                                                                       (satisfies? p/IGrid lapsi))
                                                              (p/aseta-alueet! lapsi [])
                                                              (p/aseta-lapset! lapsi nil)
                                                              (p/aseta-parametrit! lapsi nil))
                                                            (when-not (gop/id? lapsi (gop/id osa))
                                                              lapsi))
                                                          lapset)))))))))
    (p/paivita-alueet! rivin-vanhempi
                       (fn [alueet]
                             (mapv (fn [alue]
                                     (update alue :rivit (fn [[alku loppu]]
                                                           [alku (dec loppu)])))
                                   alueet)))))

(defn poista-sarake! [grid solu]
  ;; TODO
  )

(defn aseta-osat!
  ([grid osat]
   (swap! (:osat grid) (fn [_] osat))
   grid)
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

(defn aseta-root-fn! [grid {:keys [haku paivita!]}]
  (let [grid-id (gop/id grid)]
    (swap! taulukko-konteksti
           (fn [konteksti]
             (-> konteksti
                 (assoc-in [grid-id :root-fn] haku)
                 (assoc-in [grid-id :paivita-root!] paivita!))))
    grid))

(defn piillota! [grid]
  (p/paivita-parametrit! grid (fn [parametrit]
                                (update parametrit :class conj "piillotettu"))))

(defn nayta! [grid]
  (p/paivita-parametrit! grid (fn [parametrit]
                                (update parametrit :class disj "piillotettu"))))

(defn piillotettu? [grid]
  (let [pred #(contains? (get (p/parametrit %) :class) "piillotettu")]
    (loop [grid grid
           piillotettu? (pred grid)]
      (if (or (nil? grid)
              piillotettu?)
        piillotettu?
        (recur (vanhempi grid)
               (pred grid))))))

(defn grid-osat
  ([grid] @(:osat grid))
  ([grid polku] (get-in @(:osat grid) polku)))

(defn grid-alueet
  [grid]
  @(:alueet grid))

(defn aseta-alueet! [grid alueet]
  (swap! (:alueet grid)
         (fn [_]
           alueet))
  grid)
(defn paivita-alueet! [grid f]
  (swap! (:alueet grid)
         (fn [alueet]
           (f alueet)))
  grid)

(defn grid-parametrit [grid]
  @(:parametrit grid))

(defn aseta-parametrit! [grid parametrit]
  (swap! (:parametrit grid)
         (fn [_]
           parametrit))
  grid)
(defn paivita-parametrit! [grid f]
  (swap! (:parametrit grid)
         (fn [parametrit]
           (f parametrit)))
  grid)

(defn grid-kopioi [kopioitava-grid konstruktori]
  (let [grid (konstruktori (gop/id kopioitava-grid)
                           (r/atom (p/alueet kopioitava-grid))
                           nil
                           (r/atom (p/lapset kopioitava-grid))
                           (r/atom (p/parametrit kopioitava-grid)))
        grid (paivita-kaikki-lapset! grid
                                     (constantly true)
                                     (fn [lapsi]
                                       (if (satisfies? p/IGrid lapsi)
                                         (gop/kopioi lapsi)
                                         lapsi)))
        grid (paivita-kaikki-lapset! (dissoc grid ::osan-derefable ::tunniste-rajapinnan-dataan)
                                     (constantly true)
                                     (fn [lapsi]
                                       (dissoc lapsi ::osan-derefable ::tunniste-rajapinnan-dataan)))]
    (merge grid
           (dissoc kopioitava-grid :id :alueet :koko :osat :parametrit))))

(declare piirra-grid dynaamisen-jalkelainen?)

(declare ->Grid)

(defn grid-olemassa? [grid]
  (not (nil?
         (try (root grid)
              (catch :default _
                nil)))))

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
    (lisaa-rivi! this solu))
  (-lisaa-rivi! [this solu index]
    (lisaa-rivi! this solu index))
  (-lisaa-sarake! [this solu]
    (p/lisaa-sarake! this solu (count (p/lapset this))))
  (-lisaa-sarake! [this solu index]
    (lisaa-sarake! this solu index))
  (-poista-rivi! [this solu]
    (poista-rivi! this solu))
  (-poista-sarake! [this solu]
    (poista-sarake! this solu))

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
  (-aseta-root-fn! [this f]
    (aseta-root-fn! this f))

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
    (swap! taulukko-konteksti assoc-in [(gop/id this) :datan-kasittelija] datan-kasittelija)
    (let [this (aseta-gridin-polut (root this))
          grid-rajapintakasittelijat (reduce-kv (fn [m polku kasittelija]
                                                  (assoc m polku (rajapinnan-grid-kasittelija this polku kasittelija)))
                                                {}
                                                grid-kasittelija)]
      (paivita-root! this
                     (fn [_]
                       (paivita-kaikki-lapset! (assoc this ::lopeta-rajapinnan-kautta-kuuntelu! (fn [gridin-id]
                                                                                                  (when-let [datan-kasittelija (get-in @taulukko-konteksti [gridin-id :datan-kasittelija])]
                                                                                                    (dk/poista-seurannat! datan-kasittelija)
                                                                                                    (dk/lopeta-tilan-kuuntelu! datan-kasittelija)))
                                                      ::grid-rajapintakasittelijat grid-rajapintakasittelijat)
                                               (fn [osa]
                                                 (not (dynaamisen-jalkelainen? osa)))
                                               (partial osan-data-yhdistaminen grid-rajapintakasittelijat))))))
  (-grid-tapahtumat [this data-atom tapahtumat]
    (let [this (root this)]
      (paivita-root! this
                     (fn [_]
                       (assoc this ::grid-tapahtumat (into {}
                                                           (map (fn [[tapahtuman-nimi {:keys [polut toiminto!] :as tapahtuma}]]
                                                                  (let [kasittely-fn (fn []
                                                                                       (dk/next-tick (fn []
                                                                                                       ;; Jos toiminnossa muutetaan itse atomia, niin halutaan, että seurannat huomioi sen muutoksen
                                                                                                       (binding [dk/*seuranta-muutos?* false]
                                                                                                         (let [data @data-atom
                                                                                                               uusi-data (map #(get-in data %) polut)
                                                                                                               lisa-argumentit (:args (meta polut))]
                                                                                                           (apply toiminto!
                                                                                                                  (root this)
                                                                                                                  data
                                                                                                                  (if lisa-argumentit
                                                                                                                    (concat uusi-data lisa-argumentit)
                                                                                                                    uusi-data)))))))]
                                                                    (add-watch data-atom
                                                                               tapahtuman-nimi
                                                                               (fn [_ _ vanha uusi]
                                                                                 (when (and (not dk/*seuranta-muutos?*)
                                                                                            (grid-olemassa? this))
                                                                                   (let [vanha-data (map #(get-in vanha %) polut)
                                                                                         uusi-data (map #(get-in uusi %) polut)
                                                                                         seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                                                                         ajetaan-tapahtuma? (and seurattava-data-muuttunut?
                                                                                                                 *ajetaan-tapahtuma?*)]
                                                                                     (when ajetaan-tapahtuma?
                                                                                       (kasittely-fn))))))
                                                                    [tapahtuman-nimi {:seurannan-lopetus! (fn []
                                                                                                            (remove-watch data-atom tapahtuman-nimi))
                                                                                      :tapahtuma-trigger! (fn []
                                                                                                            (kasittely-fn))}]))
                                                                tapahtumat)))))))
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

(declare ->DynaaminenGrid)

(defrecord DynaaminenGrid [id toistettava-osa toistettavan-osan-data osien-maara-muuttui alueet koko osat parametrit]
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
    (lisaa-rivi! this solu))
  (-lisaa-rivi! [this solu index]
    (lisaa-rivi! this solu index))
  (-lisaa-sarake! [this solu]
    (p/lisaa-sarake! this solu (count (p/lapset this))))
  (-lisaa-sarake! [this solu index]
    (lisaa-sarake! this solu index))
  (-poista-rivi! [this solu]
    (poista-rivi! this solu))
  (-poista-sarake! [this solu]
    (poista-sarake! this solu))

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
  (-aseta-root-fn! [this f]
    (aseta-root-fn! this f))

  (-parametrit [this]
    (grid-parametrit this))
  (-aseta-parametrit! [this parametrit]
    (aseta-parametrit! this parametrit))
  (-paivita-parametrit! [this f]
    (paivita-parametrit! this f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))

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

(defn dynaamisen-jalkelainen?
  ([osa] (dynaamisen-jalkelainen? osa false))
  ([osa dynaaminen?]
   (let [vanhempi (vanhempi osa)]
     (if (and vanhempi (false? dynaaminen?))
       (recur vanhempi (instance? DynaaminenGrid vanhempi))
       dynaaminen?))))

(defn dynaaminen-vanhempi
  ([osa] (dynaaminen-vanhempi osa false))
  ([osa dynaaminen?]
   (let [vanhempi (vanhempi osa)]
     (cond
       (nil? vanhempi) nil
       (false? dynaaminen?) (recur vanhempi (instance? DynaaminenGrid vanhempi))
       :else osa))))

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
                                       error-info "\n"
                                       (when g-debug/GRID_DEBUG
                                         (apply str "\n---- VIIMEISIMMÄT DATA MUUTOKSET ----"
                                                (str "\nGRID: " (or (gop/nimi grid) "Nimetön") "(" (gop/id grid) ")")
                                                (for [lapsi (p/lapset grid)]
                                                  (let [data (conj (get-in @g-debug/debug [:rajapinnat (get-in @g-debug/debug [:osat (gop/id lapsi) :rajapinta])])
                                                                   (get-in @g-debug/debug [:osat (gop/id lapsi) :derefable]))]
                                                    (str "\n--> " (or (gop/nimi lapsi) (gop/id lapsi))
                                                         "\n" (pr-str {:data-rajapinnasta (first data)
                                                                       :grid-data (second data)
                                                                       :osan-derefable (get data 2)})))))))))
     :display-name (str (or (gop/nimi grid) "Nimetön") " (" (gop/id grid) ")")
     :render (fn [this]
               (if-let [error (.. this -state -error)]
                 [virhekasittely/rendaa-virhe error]
                 (let [[_ grid] (r/argv this)
                       ;; Tätä domNodeHaku funktiota voi sitten käyttää jossain muualla, jos haluaa esim. muuttaa dom noden attribuutin arvoa
                       ;; ilman, että se triggeröi reactin renderöintiä.
                       _ (set! (.-domNode grid) (fn [] (dom/dom-node this)))
                       _ (when (instance? DynaaminenGrid grid)
                           @(get-in grid [:osien-maara-muuttui :trigger]))
                       {luokat :class dom-id :id style :style
                        data-cy :data-cy} @(:parametrit grid)
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
                                        :id dom-id
                                        :style style
                                        :data-cy data-cy}
                    [:div {:style {:display "grid"
                                   :position "relative"
                                   :top top
                                   :left left
                                   :grid-template-columns leveydet
                                   :grid-template-rows korkeudet}}
                     (if (instance? DynaaminenGrid grid)
                       ^{:key (or (-> grid meta :key) (str (:id grid) "-dynamic"))}
                       [dynaaminen-grid grid]
                       ^{:key (or (-> grid meta :key) (str (:id grid) "-static"))}
                       [staattinen-grid grid])]])))}))

(defn vaihda-osa! [vaihdettava-osa vaihto-fn & datan-kasittelyt]
  (let [root-grid (root vaihdettava-osa)
        vaihdettavan-osan-id (gop/id vaihdettava-osa)

        vanhat-osat (if (satisfies? p/IGrid vaihdettava-osa)
                      (gridin-osat-vektoriin vaihdettava-osa (constantly true) identity)
                      [vaihdettava-osa])
        ;; Pitää tehdä ennen kuin päivitetään uudet osat. Se kun mutatoi.
        vaihdettavat-rajapintakasittelijat (distinct
                                             (keep (fn [vanha-osa]
                                                     (some (fn [[grid-polku {:keys [solun-polun-pituus] :as kasittelija}]]
                                                             (when (and (grid-polku-sopii-osaan? grid-polku vanha-osa)
                                                                        (solun-polun-pituus-oikein? grid-polku solun-polun-pituus vanha-osa))
                                                               kasittelija))
                                                           (::grid-rajapintakasittelijat root-grid)))
                                                   vanhat-osat))
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
                                                  vanha-index-polku (::index-polku vaihdettava-osa)
                                                  vanha-nimi-polku (::nimi-polku vaihdettava-osa)
                                                  uuden-osan-nimi (or (gop/nimi uusi-osa) (last vanha-nimi-polku))
                                                  uuden-osan-koot (when uusi-osa-grid?
                                                                    (p/koot uusi-osa))
                                                  uuden-osan-id (gop/id uusi-osa)
                                                  vaihdettavan-osan-koot (when vaihdettava-osa-grid?
                                                                           (select-keys (p/koot vaihdettava-osa)
                                                                                        (into #{}
                                                                                              (gridin-osat-vektoriin vaihdettava-osa #(satisfies? p/IGrid %) gop/id))))

                                                  lisattavat-tiedot (select-keys vaihdettava-osa #{::root-id})

                                                  osan-kasittely (fn [osa]
                                                                   (let [osa (-> osa
                                                                                 (merge lisattavat-tiedot)
                                                                                 (update ::index-polku
                                                                                         (fn [_]
                                                                                           (vec (concat vanha-index-polku
                                                                                                        (get index-polut (gop/id osa))))))
                                                                                 (update ::nimi-polku
                                                                                         (fn [_]
                                                                                           (vec (concat (butlast vanha-nimi-polku)
                                                                                                        [uuden-osan-nimi]
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
                                                                         (swap! ((get-in @taulukko-konteksti [(::root-id root-grid) :koko-fn]))
                                                                                (fn [koot]
                                                                                  (assoc koot id (get vaihdettavan-osan-koot kaytetyn-osan-id))))
                                                                         (assoc osa :id id))
                                                                       osa)))
                                                  _ (when uusi-osa-grid?
                                                      (pre-walk-grid! uusi-osa
                                                                      (fn [osa]
                                                                        (when (satisfies? p/IGrid osa)
                                                                          (swap! ((get-in @taulukko-konteksti [(::root-id root-grid) :koko-fn]))
                                                                                 (fn [koot]
                                                                                   (assoc koot (gop/id osa) (get uuden-osan-koot (gop/id osa)))))))))
                                                  paivitetty-osa (assoc (if uusi-osa-grid?
                                                                          (paivita-kaikki-lapset! (osan-kasittely uusi-osa)
                                                                                                  (constantly true)
                                                                                                  osan-kasittely)
                                                                          (merge (osan-kasittely uusi-osa)
                                                                                 (select-keys vaihdettava-osa #{::osan-derefable
                                                                                                                ::tunniste-rajapinnan-dataan
                                                                                                                ::triggeroi-seuranta!})))
                                                                        ::nimi uuden-osan-nimi)]
                                              (when-let [poistuvat-osat (and vaihdettava-osa-grid?
                                                                             (gridin-osat-vektoriin vaihdettava-osa #(satisfies? p/IGrid %) gop/id))]
                                                (swap! ((get-in @taulukko-konteksti [(::root-id root-grid) :koko-fn]))
                                                       (fn [koot]
                                                         (apply dissoc koot poistuvat-osat))))
                                              (when uusi-osa-grid?
                                                (swap! taulukko-konteksti dissoc uuden-osan-id)
                                                (swap! ((get-in @taulukko-konteksti [(::root-id root-grid) :koko-fn]))
                                                       (fn [koot]
                                                         (assoc koot (gop/id vaihdettava-osa) (get uuden-osan-koot (gop/id uusi-osa))))))
                                              (if (and uusi-osa-grid? vaihdettava-osa-grid?)
                                                (assoc paivitetty-osa :id vaihdettavan-osan-id)
                                                paivitetty-osa))))]
    (aseta-koot! uusi-grid)

    ;; GRIDKÄSITTELIJÄN HOITAMINEN
    (if-not (empty? datan-kasittelyt)
      (let [polun-alku (::index-polku vaihdettava-osa)
            vaihdettavan-osan-polun-osa (vec (butlast polun-alku))
            uusi-osa (get-in-grid uusi-grid (conj vaihdettavan-osan-polun-osa vaihdettavan-osan-id))
            uuden-osan-nimi (gop/nimi uusi-osa)
            polun-alku (conj vaihdettavan-osan-polun-osa (or uuden-osan-nimi
                                                             (first (keep-indexed (fn [index osa]
                                                                                    (when (= (gop/id osa) vaihdettavan-osan-id)
                                                                                      index))
                                                                                  (p/lapset (get-in-grid uusi-grid vaihdettavan-osan-polun-osa))))))
            #_#_rajapintakasittelijat (::grid-rajapintakasittelijat uusi-grid)

            uudet-gridkasittelijat (reduce (fn [m [polku kasittelija]]
                                             (let [kasittelija (if (fn? kasittelija)
                                                                 (kasittelija vaihdettavat-rajapintakasittelijat)
                                                                 kasittelija)
                                                   polku (muodosta-uusi-polku polku polun-alku)]
                                               (assoc m polku (rajapinnan-grid-kasittelija uusi-grid polku kasittelija))))
                                           {}
                                           (partition 2 datan-kasittelyt))
            _ (doseq [{:keys [rajapintakasittelija rajapinta]} vaihdettavat-rajapintakasittelijat]
                (while (> (:n rajapintakasittelija) 0)
                  (poista-seuranta-derefable! rajapintakasittelija)))
            osan-kasittely (fn [osa]
                             (osan-data-yhdistaminen uudet-gridkasittelijat osa))
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
                                    (osan-kasittely uusi-osa)))))
      uusi-grid)))

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


(defn validi-grid-asetukset?
  [{:keys [nimi alueet koko osat root-fn luokat]}]
  (and (or (nil? nimi) (satisfies? IEquiv nimi))
       (or (nil? alueet) (s/valid? ::alueet alueet))
       (or (nil? koko) (s/valid? ::koko koko))
       (or (nil? osat) (s/valid? ::osat osat))))

(defn grid-c [record {:keys [nimi alueet koko osat root-fn paivita-root! luokat dom-id data-cy
                             toistettavan-osan-data toistettava-osa osien-maara-muuttui!] :as asetukset}]
  (let [root-id (gensym "grid")
        toistettavan-osan-data (when toistettavan-osan-data (r/partial toistettavan-osan-data))
        toistettava-osa (when toistettava-osa (r/partial toistettava-osa))
        osien-maara-muuttui! (when osien-maara-muuttui! (r/partial osien-maara-muuttui!))
        koko (r/atom {root-id koko})
        osat (r/atom osat)
        alueet (r/atom alueet)
        parametrit (r/atom {:class (or luokat #{})
                            :id dom-id
                            :data-cy data-cy})
        koko-fn (constantly koko)
        dynaaminen-grid? toistettava-osa
        gridi (cond-> (if dynaaminen-grid?
                        (record root-id
                                toistettava-osa
                                toistettavan-osan-data
                                {:trigger (r/atom false) :osien-maara-muuttui! osien-maara-muuttui!}
                                alueet
                                koko
                                osat
                                parametrit)
                        (record root-id alueet koko osat parametrit))
                      nimi (assoc ::nimi nimi))]
    (swap! taulukko-konteksti
           (fn [konteksti]
             (-> konteksti
                 (assoc-in [root-id :koko-fn] koko-fn)
                 (assoc-in [root-id :root-fn] root-fn)
                 (assoc-in [root-id :paivita-root!] paivita-root!))))
    (post-walk-grid! gridi
                    (fn [osa]
                      (let [osan-id (gop/id osa)
                            root-osa? (= osan-id root-id)
                            koot (when (and (satisfies? p/IGrid osa) (not root-osa?))
                                   (p/koot osa))]
                        (when (not root-osa?)
                          (when koot
                            (swap! koko (fn [koko]
                                          (merge koko koot))))
                          (swap! taulukko-konteksti dissoc osan-id)))))
    (paivita-kaikki-lapset! (assoc gridi ::root-id root-id)
                            (constantly true)
                            (fn [osa]
                              (assoc osa :koko nil
                                     ::root-id root-id)))))
