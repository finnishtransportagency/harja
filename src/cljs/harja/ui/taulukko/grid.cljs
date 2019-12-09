(ns harja.ui.taulukko.grid
  (:require [harja.ui.taulukko.protokollat :as p]
            [harja.loki :refer [log warn]]
            [cljs.spec.alpha :as s]
            [clojure.string :as clj-str]
            [reagent.core :as r]
            [clojure.walk :as walk]))


;; TODO tarkenna string? arvot
(s/def ::leveydet (s/map-of pos-int? string?))
(s/def ::oletus-leveys string?)
(s/def ::oletus-korkeus string?)
(s/def ::sarake (s/keys :req-un [::oletus-leveys ::leveydet]))
(s/def ::rivi (s/keys :req-un [::oletus-korkeus ::korkeudet]))

(s/def ::koko (s/keys :req-un [::sarake ::rivi]))

(def ^:dynmaic *vanhempi-asettaa?* nil)

(defn- laske-gridin-css-tiedot [grid]
  (let [grid-asetukset (::koko grid)
        osan-paikat-gridissa (map #(grid/paikka %) osat)
        pienempi (fn [x y]
                   (if (< x y) x y))
        suurempi (fn [x y]
                   (if (> x y) x y))
        ;; osan-paikat-gridissa -> [alue ..] -> alue = {:sarakkeet [int int] :rivit [int int]}

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
                                            osan-paikat-gridissa)
        [css-aloitus-sarake css-lopetus-sarake
         css-aloitus-rivi css-lopetus-rivi] [(inc (pienempi 0 aloitus-sarake))
                                             (inc (- lopetus-sarake aloitus-sarake))
                                             (inc (pienempi 0 aloitus-rivi)) (inc (- lopetus-rivi aloitus-rivi))]
        sarake-leveydet (map (fn [sarake-track-numero]
                               (if-let [maaritetty-leveys (get-in grid-asetukset [:sarake :leveydet (dec sarake-track-numero)])]
                                 maaritetty-leveys
                                 (get-in grid-asetukset [:sarake :oletus-leveys])))
                             (range css-aloitus-sarake css-lopetus-sarake))
        rivi-korkeudet (map (fn [rivi-track-numero]
                              (if-let [maaritetty-korkeus (get-in grid-asetukset [:rivi :korkeudet (dec rivi-track-numero)])]
                                maaritetty-korkeus
                                (get-in grid-asetukset [:rivi :oletus-korkeus])))
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

(defn aseta-sama-koko-vanhemman-kanssa [vanhempi osa]
  (binding [*vanhempi-asettaa?* true]
    (let [{:keys [sarakkeet rivit]} (p/alue vanhempi)]
      (p/aseta-koko osa (-> (::koko vanhempi)
                            (update-in [:sarake :leveydet]
                                       (fn [leveydet]
                                         (select-keys leveydet (apply range sarakkeet))))
                            (update-in [:rivi :korkeudet]
                                       (fn [korkeudet]
                                         (select-keys korkeudet (apply range rivit)))))))))

(defn- oikea-osa? [osa etsittava-osa]
  (let [id? (symbol? osa)]
    (if id?
      (p/id? osa etsittava-osa)
      (= (p/nimi osa) etsittava-osa))))

(defn- etsi-osa
  ([osa etsittava-osa]
   (if (oikea-osa? osa etsittava-osa)
     osa
     (let [kaikki-osan-lapset (remove nil? (p/lapset osa))]
       (recur osa etsittava-osa kaikki-osan-lapset))))
  ([osa etsittava-osa lapset]
   (let [oikea-osa (some (fn [osa]
                           (let [osan-lapset (p/lapset osa)]
                             (reduce (fn [oikea-osa osa]
                                       (cond
                                         (not (nil? oikea-osa)) oikea-osa
                                         (oikea-osa? osa etsittava-osa) osa
                                         :else nil))
                                     nil
                                     osan-lapset)))
                         lapset)]
     (if (or oikea-osa (empty? lapset))
       oikea-osa
       (let [kaikki-lapsen-lapset (sequence (comp (mapcat p/lapset)
                                                  (remove nil?))
                                            lapset)]
         (recur osa etsittava-osa kaikki-lapsen-lapset))))))

(defn- gridin-osat-vektoriin
  ([grid pred f]
   (let [kaikki-gridin-lapset (filter pred (p/lapset grid))
         aloitus (if (pred grid)
                   [(f grid)]
                   [])]
     (recur aloitus pred f kaikki-gridin-lapset)))
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
(defn- paivita-kaikki-lapset
  [osa pred f]
  (let [paivitetyt-lapset (loop [[lapsi & lapset] (p/lapset osa)
                                 paivitetyt-lapset []]
                            (if (nil? lapsi)
                              paivitetyt-lapset
                              (let [lapsi (if (pred lapsi)
                                            (f lapsi)
                                            lapsi)
                                    lapsen-lapset-paivitetty (paivita-kaikki-lapset lapsi pred f)]
                                (recur lapset
                                       (conj paivitetyt-lapset lapsen-lapset-paivitetty)))))]
    (p/aseta-lapset osa paivitetyt-lapset)))

(defn- piirra-grid [osat grid]
  (for [osa osat]
    (with-meta [p/piirra osa]
               {:key (p/id osa)})))

(defn- dynaaminen-grid [grid]
  (let [data (p/pointteri (::p/data grid) grid)
        osa (first (::osat grid))
        osat (map (fn [osan-id]
                    (p/aseta-id osa osan-id))
                  data)]
    [piirra-grid osat grid]))

(defn- staattinen-grid [grid]
  (let [osat (::osat grid)]
    [piirra-grid osat grid]))

(defrecord GridU []
  p/IGrid
  (osat [this]
    (::osat this))
  (koko [this]
    (::koko this))
  (aseta-koko [this koko]
    {:pre [(s/valid? ::koko koko)]
     :post [((::rajoitukset this))]}
    (assoc this ::koko koko
                ::koko-seuraa-vanhempaa? (boolean *vanhempi-asettaa?*)))
  (paivita-koko [this f]
    {:post [(s/valid? ::koko (::koko %))
            ((::rajoitukset this))]}
    (-> this
        (update ::koko f)
        (assoc ::koko-seuraa-vanhempaa? (boolean *vanhempi-asettaa?*))))
  p/IPiirrettava
  (-piirra [this]
    (let [luokat (-> this :parametrit :class)
          dom-id (-> this :parametrit :id)]
      [:div {:style {:overflow "hidden"}}
       (let [{:keys [korkeudet leveydet top left]} (laske-gridin-css-tiedot this)]
         (when (= (::root this) this)
           (p/gridin-muoto! (::p/data this) this))
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
          (if (-> this meta ::dynaaminen?)
            ^{:key (str (:id this) "-dynamic")}
            [dynaaminen-grid this]
            ^{:key (str (:id this) "-static")}
            [staattinen-grid this])])])))

;; data erilleen, aggregoi hommia datasta, merkkaa alueet aggregoitaviksi

;; Alue ottaa vektorin vektoreita (tai sittenkin mappeja?) dataa. Voi määritellä x- ja y-suunnissa, että kasvaako alue datan mukana vaiko ei.

(defn piirra [grid]
  {:pre [#(every? (fn [osa]
                    (satisfies? p/IPiirrettava osa))
                  (:osat grid))]}
  (p/-piirra grid))


(defn gridille-data! [grid data]
  (loop [[lapsi & lapset] (lapset grid)
         tulokset []]
    (if lapsi
      tulokset
      (let [lapsia? (lapsia? lapsi)
            hyvaksytty-data (when lapsia?
                              lapsen-dataspec)
            ]))))

(defrecord GridDatanKasittelija [data]
  p/IGridDatanKasittely
  (resetoi! [this data])
  (lisaa! [this osan-id data]
    ^{:pre [(map? data)]}
    (update-in this [osan-id :data] conj data))
  (muokkaa! [this data])
  (poista! [this data])
  (pointteri [this gridin-osa])
  (gridin-muoto! [this grid]
    ;; {<:osan-id {<:osan-data [{}..]> <:dynamic? boolean> <:lapset [...]> <:polku-dataan :osan-id>}>}

    ;; Version 2
    ;; {::data {.. ihmisen ymmärtämiä avaimia ..}
    ;;  ::pointterit {<:osan-id {:polku-dataan [..] :polut-pointtereihin [..]}>}}
    ;; lapset on vektori osan-idtä.
    ;; Dynaamisesti generoidulle datalle pitänee generoida myös yksilöivä id sen datan löytämistä varten.
    ;; Nimipolut kaiketi pakollisia siihen asti, että lapsoset ovat struktuuriltaan identtisiä vanhemman sisäl.

    ;; ::data avaimen taakse kama tulee jostain (esim. backiltä).
    ;; ::pointterit avaimen taaksen pitäisi luoda :polku-dataan tässä.
    (let [polut-dataan (walk/postwalk (fn [x]
                                        (if #(satisfies? p/IAsia x)
                                          (let [id (p/id x)
                                                lapset (p/lapset x)
                                                nimi (p/nimi x)
                                                datapolku (::datapolku x)]
                                            (cond
                                              ;; osa itse määrittää mistä sen data tulee
                                              datapolku [(with-meta datapolku
                                                                    {::datapolku? true})]
                                              ;; lehti
                                              (and id (empty? lapset)) [[id]]
                                              ;; nimellinen grid
                                              (and id nimi lapset) (vec (mapcat (fn [lapsi]
                                                                                  (mapv (fn [polku]
                                                                                          (if (-> polku meta ::datapolku?)
                                                                                            polku
                                                                                            (conj polku nimi)))
                                                                                        lapsi))
                                                                                lapset))
                                              ;; nimeton grid
                                              (and id lapset) (vec (concat
                                                                     (map-indexed (fn [index lapsi]
                                                                                    (mapv (fn [polku]
                                                                                            (if (-> polku meta ::datapolku?)
                                                                                              polku
                                                                                              (conj polku index)))
                                                                                          lapsi))
                                                                                  lapset)))))
                                          x))
                                      grid)])))

(defn lisaa-datan-kasittelija [grid data-atom data-atom-polku]
  (let [datakasittelija (->GridDatanKasittelija (r/cursor data-atom data-atom-polku))]
    (p/gridin-muoto! datakasittelija grid)
    (assoc grid ::p/data datakasittelija)))

(defonce oletus-koko {:sarake {:oletus-leveys "1fr"}
                      :rivi {:oletus-korkeus "1fr"}})

(defn aseta-koon-seuranta! [grid]
  ;;{:seurattava :otsikko
  ;  :sarakkeet <:sama | {:foo f}
  ;  :rivit :sama}
  (let [koko (p/koko grid)
        {seurattavan-koon-nimi :seurattava
         seurattava-sarakkeet :sarakkeet
         seurattava-rivit :rivit} (get-in koko :seuraa)
        seurattava-koko (-> grid ::koko deref (get seurattavan-koon-nimi))
        koko (-> (merge seurattava-koko
                        koko)
                 (dissoc :class)
                 (update :sarake (fn [{:keys [leveydet nimet] :as sarake-asetukset}]
                                   (if (= :sama seurattava-sarakkeet)
                                     sarake-asetukset
                                     (assoc sarake-asetukset
                                       :leveydet
                                       (into {}
                                             (map (fn [[nimi f]]
                                                    (f (get leveydet (get nimet nimi))))
                                                  seurattava-sarakkeet))))))
                 (update :rivi (fn [{:keys [korkeudet nimet] :as rivi-asetukset}]
                                 (if (= :sama seurattava-rivit)
                                   rivi-asetukset
                                   (assoc rivi-asetukset
                                     :korkeudet
                                     (into {}
                                           (map (fn [[nimi f]]
                                                  (f (get korkeudet (get nimet nimi))))
                                                seurattava-rivit)))))))]
    (p/aseta-koko! grid koko)))

(defn aseta-koko! [grid]
  ;:koko {<:sarake {:oletus-leveys "5px"
  ;                 :leveydet {1 "3px"}
  ;                 <:nimet {:foo 1}>}
  ;       :rivi {:oletus-korkeus "3px"
  ;              :korkeudet {2 "3px"}}>
  ;       <:class "foo">}
  {:pre [(->> grid p/alue :koko (s/valid? ::koko-conf))]
   :post [(s/valid? ::koko (::koko grid))]}
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
                                                                                   (conj rules (clj-str/replace-all selector luokka-re (str "#grid-id")))
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
        koko (:koko (p/alue grid))
        koko (if (nil? koko)
               (do
                 (warn (str "Kokoa ei annettu gridille"
                            (when-let [gridin-nimi (p/nimi grid)]
                              (str " " gridin-nimi))
                            ". Käytetään oletusarvoja."))
                 oletus-koko)
               (koko-conf->koko koko))]
    (p/aseta-koko! grid koko)))

(defn aseta-koot [root-grid]
  (let [kokojen-seuranta (gridin-osat-vektoriin #(satisfies? p/IGrid %)
                                                (fn [grid]
                                                  (let [koko (p/koko grid)
                                                        seurattavan-gridin-nimi (get-in koko [:seuraa :seurattava])
                                                        seruattava-grid (if seurattavan-gridin-nimi
                                                                          (etsi-osa (::root grid) seurattavan-gridin-nimi)
                                                                          grid)]
                                                    {grid seruattava-grid}))
                                                root-grid)
        gridit-jarjestetty (loop [jarjestetyt-gridit (filterv (fn [[grid seurattava-grid]]
                                                              (p/id? grid (p/id seurattava-grid)))
                                                            kokojen-seuranta)
                                  jarjestettavat-gridit (remove (fn [[grid seurattava-grid]]
                                                                (p/id? grid (p/id seurattava-grid)))
                                                              kokojen-seuranta)]
                             (if (empty? jarjestettavat-gridit)
                               jarjestetyt-gridit
                               (let [seuraava-grid (some (fn [[grid seurattava-grid]]
                                                          (some (fn [[jarjestetty-grid _]]
                                                                  (when (p/id? seurattava-grid (p/id jarjestetty-grid))
                                                                    [grid seurattava-grid]))
                                                                jarjestetyt-gridit))
                                                        jarjestettavat-gridit)]
                                 (if (vector? seuraava-grid)
                                   (recur (conj jarjestetyt-gridit seuraava-grid)
                                          (remove (fn [[grid _]]
                                                    (p/id? (first seuraava-grid) (p/id grid)))
                                                  jarjestettavat-gridit))
                                   (throw (js/Error. (str "Gridin "
                                                          (when-let [gridin-nimi (-> osat first ::root p/nimi)]
                                                            (str gridin-nimi " "))
                                                          "kokojen seurannassa on kiertävä riippuvuus.")))))))
        koot-asetettu (loop [[[grid seurattava-grid] & loput-gridit] gridit-jarjestetty
                             koko-asetettu {}]
                        (if (nil? grid)
                          koko-asetettu
                          (recur loput-gridit
                                 (assoc koko-asetettu
                                   grid
                                   ;; Pitää ettiä seurattava gridi koko-asetettu varista, jotta
                                   ;; voidaan olla varmoja siitä, että sille on koko asetettuna
                                   (if-let [seurattava-grid (get koko-asetettu seurattava-grid)]
                                     (aseta-koon-seuranta! seurattava-grid)
                                     (aseta-koko! grid))))))
        root-gridin-lapset (paivita-kaikki-lapset root-grid
                                                  (fn [osa]
                                                    (boolean (find koot-asetettu osa)))
                                                  (fn [osa]
                                                    (p/aseta-koko osa
                                                                  (p/koko (first (find koot-asetettu osa))))))
        root-grid (if-let [[root-grid _] (find koot-asetettu root-grid)]
                    root-grid
                    root-grid)]
    (p/aseta-lapset root-grid root-gridin-lapset)))

(defn grid [id lapset data-atom data-atom-polku]
  (let [root-grid (->Grid lapset)
        datan-kasittelija (lisaa-datan-kasittelija root-grid data-atom data-atom-polku)
        koot (atom nil)
        root-grid (paivita-kaikki-lapset root-grid
                                      (fn [& _] true)
                                      (fn [lapsi]
                                        (assoc lapsi ::root (p/id root-grid)
                                                     ::data datan-kasittelija
                                                     ::koot koot)))
        root-grid (assoc root-grid ::root (p/id root-grid)
                                   ::data datan-kasittelija
                                   ::koot koot)
        root-grid (aseta-koot root-grid)]
    ))

(defn Rivi* [id]
  ())

(let [header-jarjestys (atom [:header-1 :header-2 :header-3])]
  (grid
    ;; Alueet
    [{:sarakkeet [3 6] :rivit [0 0]}
     {:sarakkeet [3 6] :rivit [1 10]}]
    ;; Koko
    {:class "foo"}
    ;; Järjestys


    ;; Gridin ei tulisi välittää minkälaista dataa sille annetaan. Se vain näyttää sen mitä sille välitetään.
    ;; Ongelmana kumminkin järjestäminen. Osien järjestäminen riippuu hyvinkin pitkälti datasta.
    ;; Jos dataa järjestetään niin itse osankin pitäisi silloin lähtä liikkeelle (kaiketi?). Jos näin
    ;; niin kun data liitetään osaan ensimmäisen kerran, niin sen pitäisi leechata siihen.


    ;; Osat
          [(->Rivi
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
