(ns harja.ui.validointi
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str-clj]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [cljs-time.core :as t]
            [harja.domain.tierekisteri :as tr]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn ei-hoitokaudella-str [alku loppu]
  (str "Olet tallentamassa valitun hoitokauden ulkopuolelle (" alku " \u2014 " loppu ").
  Nähdäksesi tuloksen, vaihda tallennuksen jälkeen valittua hoitokautta."))

(defn ei-urakan-aikana-str [alku loppu]
  (str "Olet tallentamassa urakan ulkopuolelle (" alku " \u2014 " loppu ")!"))

(defn ei-kuukauden-aikana-str [alku loppu]
  (str "Olet tallentamassa valitun kuukauden ulkopuolelle (" alku " \u2014 " loppu ").
  Nähdäksesi tuloksen, vaihda tallennuksen jälkeen valittua aikaväliä."))

;; Validointi
;; Rivin skeema voi määritellä validointisääntöjä.
;; validoi-saanto multimetodi toteuttaa tarkastuksen säännön keyword tyypin mukaan
;; nimi = Kentän nimi
;; data = Riville syötettävä data
;; rivi = Rivillä olevat tiedot
;; taulukko = Koko grid-taulukko
(defmulti validoi-saanto (fn [saanto nimi data rivi taulukko & optiot] saanto))

(defmethod validoi-saanto :hoitokaudella [_ _ data _ _ & [viesti]]
  (when (and data (not (pvm/valissa? data (first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi))))
    (or viesti
        (ei-hoitokaudella-str (pvm/pvm (first @u/valittu-hoitokausi)) (pvm/pvm (second @u/valittu-hoitokausi))))))

(defmethod validoi-saanto :urakan-aikana [_ _ data _ _ & [viesti]]
  (let [urakka @nav/valittu-urakka
        alkupvm (:alkupvm urakka)
        loppupvm (:loppupvm urakka)]
    (when (and data alkupvm loppupvm
               (not (pvm/valissa? data alkupvm loppupvm)))
      (or viesti
          (ei-urakan-aikana-str (pvm/pvm alkupvm) (pvm/pvm loppupvm))))))

(defmethod validoi-saanto :urakan-aikana-ja-hoitokaudella [_ _ data _ _ & [viesti]]
  (let [urakka @nav/valittu-urakka
        alkupvm (:alkupvm urakka)
        loppupvm (:loppupvm urakka)
        hoitokausi-alku (first @u/valittu-hoitokausi)
        hoitokausi-loppu (second @u/valittu-hoitokausi)
        urakan-aikana? (and data alkupvm loppupvm
                            (pvm/valissa? data alkupvm loppupvm))
        hoitokaudella? (and data (pvm/valissa? data hoitokausi-alku hoitokausi-loppu))]
    (if (false? urakan-aikana?)
      (or viesti
          (ei-urakan-aikana-str (pvm/pvm alkupvm) (pvm/pvm loppupvm)))
      (if (false? hoitokaudella?)
        (or viesti
            (ei-hoitokaudella-str (pvm/pvm hoitokausi-alku) (pvm/pvm hoitokausi-loppu)))))))

(defmethod validoi-saanto :valitun-kkn-aikana-urakan-hoitokaudella [_ _ data _ _ & [viesti]]
  (let [urakka @nav/valittu-urakka
        alkupvm (:alkupvm urakka)
        loppupvm (:loppupvm urakka)
        hoitokausi-alku (first @u/valittu-hoitokausi)
        hoitokausi-loppu (second @u/valittu-hoitokausi)
        urakan-aikana? (and data alkupvm loppupvm
                            (pvm/valissa? data alkupvm loppupvm))
        hoitokaudella? (and data (pvm/valissa? data hoitokausi-alku hoitokausi-loppu))
        [valittu-kk-alkupvm valittu-kk-loppupvm] @u/valittu-hoitokauden-kuukausi
        valitun-kkn-aikana? (and data valittu-kk-alkupvm valittu-kk-loppupvm
                                 (pvm/valissa? data valittu-kk-alkupvm valittu-kk-loppupvm))]
    (if (false? urakan-aikana?)
      (or viesti
          (ei-urakan-aikana-str (pvm/pvm alkupvm) (pvm/pvm loppupvm)))
      (if (false? hoitokaudella?)
        (or viesti
            (ei-hoitokaudella-str (pvm/pvm hoitokausi-alku) (pvm/pvm hoitokausi-loppu)))
        (when (and valittu-kk-alkupvm (not valitun-kkn-aikana?))
          (or viesti
              (ei-kuukauden-aikana-str (pvm/pvm valittu-kk-alkupvm) (pvm/pvm valittu-kk-loppupvm))))))))

(defmethod validoi-saanto :vakiohuomautus [_ _ data _ _ & [viesti]]
  viesti)

(defmethod validoi-saanto :validi-tr [_ _ data rivi _ & [viesti reittipolku]]
  (let [osoite (tr/normalisoi data)]
    (when
     (and (tr/validi-osoite? data)
          (or (= 0 (::tr/tie osoite)) (not (get-in rivi reittipolku))))
      (or viesti "Tarkasta tr-osoite"))))

(defmethod validoi-saanto :uusi-arvo-ei-setissa [_ _ data rivi _ & [setti-atom viesti]]
  "Tarkistaa, onko rivi uusi ja arvo annetussa setissä."
  (log "Tarkistetaan onko annettu arvo " (pr-str data) " setissä " (pr-str @setti-atom))
  (when (and (contains? @setti-atom data) (neg? (:id rivi)))
    (or viesti (str "Arvon pitää löytyä joukosta " (clojure.string/join ", " @setti-atom)))))

(defmethod validoi-saanto :ei-tyhja [_ _ data _ _ & [viesti]]
  (when (str-clj/blank? data)
    (or viesti "Anna arvo")))

(defmethod validoi-saanto :ei-negatiivinen-jos-avaimen-arvo [_ _ data rivi _ & [avain arvo viesti]]
  (when (and (= (avain rivi) arvo)
             (< data 0))
    (or viesti "Arvon pitää olla yli nolla")))

(defmethod validoi-saanto :ei-tyhja-jos-toinen-avain-nil
  [_ _ data rivi _ & [toinen-avain viesti]]
  (when (and (str-clj/blank? data)
             (not (toinen-avain rivi)))
    (or viesti "Anna arvo")))

(defmethod validoi-saanto :ei-tulevaisuudessa [_ _ data _ _ & [viesti]]
  (when (and data (t/after? data (pvm/nyt)))
    (or viesti "Päivämäärä ei voi olla tulevaisuudessa")))

(defmethod validoi-saanto :ei-avoimia-korjaavia-toimenpiteitä [_ _ data rivi _ & [viesti]]
  (when (and (or (= data :suljettu) (= data :kasitelty))
             (not (every? #(= (:tila %) :toteutettu) (:korjaavattoimenpiteet rivi))))
    (or viesti "Avoimia korjaavia toimenpiteitä")))

(defmethod validoi-saanto :joku-naista [_ _ _ rivi _ & avaimet-ja-viesti]
  (let [avaimet (if (string? (last avaimet-ja-viesti)) (butlast avaimet-ja-viesti) avaimet-ja-viesti)
        viesti (if (string? (last avaimet-ja-viesti))
                 (last avaimet-ja-viesti)

                 (str "Anna joku näistä: "
                      (clojure.string/join ", "
                                           (map (comp clojure.string/capitalize name) avaimet))))]
    (when-not (some #(not (str-clj/blank? (% rivi))) avaimet) viesti)))

(defmethod validoi-saanto :uniikki [_ nimi data _ taulukko & [viesti]]
  (let [rivit-arvoittain (group-by nimi (vals taulukko))]
    ;; Data on uniikkia jos sama arvo esiintyy taulukossa vain kerran
    (when (and (not (nil? data))
               (> (count (get rivit-arvoittain data)) 1))
      (or viesti "Arvon pitää olla uniikki"))))

(defmethod validoi-saanto :pvm-kentan-jalkeen [_ _ data rivi _ & [avain viesti]]
  (when (and
          (avain rivi)
          (pvm/ennen? data (avain rivi)))
    (or viesti (str "Päivämäärän pitää olla " (pvm/pvm (avain rivi)) " jälkeen"))))

(defmethod validoi-saanto :pvm-toisen-pvmn-jalkeen [_ _ data _ _ & [vertailtava-pvm viesti]]
  (when (and
          vertailtava-pvm
          (pvm/ennen? data vertailtava-pvm))
    (or viesti (str "Päivämäärän pitää olla " (pvm/pvm vertailtava-pvm) " jälkeen"))))

(defmethod validoi-saanto :pvm-ennen [_ _ data _ _ & [vertailtava-pvm viesti]]
  (when (and data vertailtava-pvm
             (not (pvm/ennen? data vertailtava-pvm)))
    (or viesti (str "Päivämäärän pitää olla " (pvm/pvm vertailtava-pvm) " ennen"))))

(defmethod validoi-saanto :pvm-sama [_ _ data _ _ & [vertailtava-pvm viesti]]
  (when (and data vertailtava-pvm
             (not (pvm/sama-pvm? data vertailtava-pvm)))
    (or viesti (str "Päivämäärän pitää olla sama kuin " (pvm/pvm vertailtava-pvm)))))

(defmethod validoi-saanto :aika-jalkeen [_ _ data rivi _ & [vertailtava-aika-tai-kentan-nimi viesti]]
  (let [vertailtava-aika (if (keyword? vertailtava-aika-tai-kentan-nimi)
                           (get rivi vertailtava-aika-tai-kentan-nimi)
                           vertailtava-aika-tai-kentan-nimi)]
    (when (and data vertailtava-aika
               (not (pvm/aika-jalkeen? data vertailtava-aika)))
      (or viesti "Tarkasta aika"))))

(defmethod validoi-saanto :toinen-arvo-annettu-ensin [_ _ data rivi _ & [avain viesti]]
  (when (and
          data
          (nil? (avain rivi)))
    (or viesti "Molempia arvoja ei voi syöttää")))

(defmethod validoi-saanto :ei-tyhja-jos-toinen-arvo-annettu [_ _ data rivi _ & [avain viesti]]
  (when (and
          (nil? data)
          (some? (avain rivi)))
    (or viesti "Syötä molemmat arvot")))

(defmethod validoi-saanto :ainakin-toinen-annettu [_ _ _ rivi _ & [[avain1 avain2] viesti]]
  (when-not (or (avain1 rivi)
                (avain2 rivi))
    (or viesti "Syötä ainakin toinen arvo")))

(defmethod validoi-saanto :yllapitoluokka [_ _ data _ _ & [viesti]]
  (when-not (or (nil? data) (= data 1) (= data 2) (= data 3))
    (or viesti "Anna ylläpitoluokka välillä 1 \u2014 3")))

(defmethod validoi-saanto :lampotila [_ _ data _ _ & [viesti]]
  (when-not (<= -55 data 55)
    (or viesti "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")))

(defmethod validoi-saanto :rajattu-numero [_ _ data _ _ & [min-arvo max-arvo viesti]]
  (when-not (<= min-arvo data max-arvo)
    (or viesti (str "Anna arvo välillä " min-arvo " - " max-arvo ""))))

(defmethod validoi-saanto :rajattu-numero-tai-tyhja [_ _ data _ _ & [min-arvo max-arvo viesti]]
  (and
    data
    (when-not (<= min-arvo data max-arvo)
      (or viesti (str "Anna arvo välillä " min-arvo " - " max-arvo "")))))

(defmethod validoi-saanto :ytunnus [_ _ data _ _ & [viesti]]
  (and
    data
    (let [ ;; Halkaistaan tunnus välimerkin kohdalta
          [tunnus tarkastusmerkki :as halkaistu] (str-clj/split data #"-")
          ;; Kun pudotetaan pois numerot, pitäisi tulos olla ["" "-" nil]
          [etuosa valimerkki loppuosa] (str-clj/split data #"\d+")]
      (when-not (and (= 9 (count data))
                     (= 2 (count halkaistu))
                     (= 7 (count tunnus))
                     (= 1 (count tarkastusmerkki))
                     (empty? etuosa)
                     (= "-" valimerkki)
                     (nil? loppuosa))
       (or viesti "Y-tunnuksen pitää olla 7 numeroa, väliviiva, ja tarkastusnumero.")))))

(defmethod validoi-saanto :email [_ _ data _ _ & [viesti]]
  (let [osat (str-clj/split data #",")]
    (when-not
      (or (empty? data)
          (every?
            #(re-matches #"^([a-zA-Z0-9_\-\.]+)@([a-zA-Z0-9_\-\.]+)\.([a-zA-Z]{2,5})$" (str-clj/trim %))
            osat))
      (or viesti "Kirjoita sähköpostitiedot pilkulla eroteltuna loppuun asti."))))

(defn validoi-saannot
  "Palauttaa kaikki validointivirheet kentälle, jos tyhjä niin validointi meni läpi."
  [nimi data rivi taulukko saannot]
  (sequence
    (comp
      (keep (fn [saanto]
              (cond
                (fn? saanto) (saanto data rivi taulukko)
                (map? saanto) (apply (:fn saanto) data rivi taulukko (:args saanto))
                :else (let [[saanto & optiot] saanto]
                        (apply validoi-saanto saanto nimi data rivi taulukko optiot)))))
      (mapcat (fn [virhe]
                (if (vector? virhe)
                  virhe
                  [virhe]))))
    saannot))

(defn validoi-rivin-kentat
  "Tekee validoinnin yhden rivin / lomakkeen kaikille kentille. Palauttaa mäpin kentän nimi -> virheet vektori.
  Tyyppi on joko :validoi (default) tai :varoita"
  ([taulukko rivi skeema] (validoi-rivin-kentat taulukko rivi skeema :validoi))
  ([taulukko rivi skeema tyyppi]
   (loop [v {}
          [s & skeema] skeema]
     (if-not s
       v
       (let [{:keys [nimi hae]} s
             validoi (tyyppi s)]
         (if (empty? validoi)
           (recur v skeema)
           (let [virheet (validoi-saannot nimi (if hae
                                                 (hae rivi)
                                                 (get rivi nimi))
                                          rivi taulukko
                                          validoi)]
             (recur (if (empty? virheet) v (assoc v nimi virheet))
                    skeema))))))))

(defn validoi-rivi
  [taulukko rivi skeema rivi-validointi]
  (let [rivi-virheet-sarakkeille (mapv (fn [{saanto :fn sarakkeet :sarakkeet}]
                                         (let [tulos (saanto rivi taulukko)]
                                           (reduce-kv (fn [m k v]
                                                        (assoc m k (v tulos)))
                                                      {} sarakkeet)))
                                       rivi-validointi)
        rivi-virheet {::rivi-virheet (keep (fn [virheet]
                                             (when-let [rivi-virhe (::rivi virheet)]
                                               rivi-virhe))
                                           rivi-virheet-sarakkeille)}
        rivin-virheet (loop [v {}
                              [s & skeema] skeema]
                         (if-not s
                           v
                           (let [{:keys [nimi]} s
                                 rivivirheet-sarakkeelle (flatten
                                                           (keep (fn [virheet]
                                                                   (when-let [virhe (get virheet nimi)]
                                                                     virhe))
                                                                 rivi-virheet-sarakkeille))]
                             (recur (if (empty? rivivirheet-sarakkeelle)
                                      v
                                      (assoc v nimi rivivirheet-sarakkeelle))
                                    skeema))))]
    (merge rivin-virheet (when-not (empty? (::rivi-virheet rivi-virheet))
                            rivi-virheet))))

(defn validoi-taulukko
  ([taulukko skeema taulukko-validointi] (validoi-taulukko taulukko skeema taulukko-validointi nil))
  ([taulukko skeema taulukko-validointi poistettu-avain]
   (let [taulukko (if (nil? poistettu-avain)
                    taulukko
                    (into {}
                          (keep (fn [[_ rivi :as rivitiedot]]
                                  (when-not (get rivi poistettu-avain)
                                    rivitiedot))
                                taulukko)))]
     (into {}
           (doall
            (map (fn [[rivi-indeksi rivi]]
                   (let [taulukko-virheet (mapv (fn [{saanto :fn sarakkeet :sarakkeet}]
                                                  (let [tulos (saanto rivi-indeksi rivi taulukko)]
                                                    (reduce-kv (fn [m k v]
                                                                 (assoc m k (v tulos)))
                                                               {} sarakkeet)))
                                                taulukko-validointi)]
                     [rivi-indeksi
                      (loop [v {}
                             [s & skeema] skeema]
                        (if-not s
                          v
                          (let [{:keys [nimi]} s
                                taulukkovirheet-sarakkeelle (vec
                                                             (flatten
                                                              (keep (fn [virheet]
                                                                      (when-let [virhe (get virheet nimi)]
                                                                        virhe))
                                                                    taulukko-virheet)))]
                            (if (empty? taulukkovirheet-sarakkeelle)
                              (recur v skeema)
                              (recur (assoc v nimi taulukkovirheet-sarakkeelle) skeema)))))]))
                 taulukko))))))

(defn liita-taulukkotason-virheet
  [taulukon-virheet virheet]
  (if taulukon-virheet
    (reduce-kv (fn [m rivi-id uudet-rivin-taulukkotason-virheet]
                 (update m rivi-id (fn [rivin-virheet]
                                     (reduce-kv (fn [m2 sarakkeen-nimi virhe-vektori]
                                                  (update m2 sarakkeen-nimi concat virhe-vektori))
                                                rivin-virheet uudet-rivin-taulukkotason-virheet))))
               virheet taulukon-virheet)
    virheet))

(defn liita-rivitason-virheet
  [rivin-virheet virheet]
  (if rivin-virheet
    (reduce-kv (fn [m sarakkeen-nimi virhe-vektori]
                 (update m sarakkeen-nimi concat virhe-vektori))
               virheet rivin-virheet)
    virheet))

(defn validoi-ja-anna-virheet
  ([gridin-tiedot skeema rivivalidointi taulukkovalidointi tyyppi] (validoi-ja-anna-virheet gridin-tiedot skeema rivivalidointi taulukkovalidointi tyyppi nil))
  ([gridin-tiedot skeema rivivalidointi taulukkovalidointi tyyppi poistettu-avain]
   (let [virheet (into {}
                       (keep (fn [[index rivi]]
                               (let [kenttien-virheet (validoi-rivin-kentat gridin-tiedot rivi skeema tyyppi)
                                     rivin-virheet (when rivivalidointi
                                                     (validoi-rivi gridin-tiedot rivi skeema rivivalidointi))
                                     virheet (liita-rivitason-virheet rivin-virheet kenttien-virheet)]
                                 (when-not (empty? virheet)
                                   [index virheet])))
                             gridin-tiedot))
         taulukkovirheet (when taulukkovalidointi
                           (validoi-taulukko gridin-tiedot skeema taulukkovalidointi poistettu-avain))]
     (liita-taulukkotason-virheet taulukkovirheet virheet))))

(defn tyhja-tr-osoite? [arvo]
  (not (tr/validi-osoite? arvo)))


(defn tyhja-arvo? [arvo]
  (or (nil? arvo)
      (str-clj/blank? arvo)))

(defn puuttuvat-pakolliset-kentat
  "Palauttaa pakolliset kenttäskeemat, joiden arvo puuttuu"
  [rivi skeema]
  (keep (fn [{:keys [pakollinen? hae nimi tyyppi] :as s}]
          (when (and pakollinen?
                     (if (= :tierekisteriosoite tyyppi)
                       (tyhja-tr-osoite? (if hae
                                           (hae rivi)
                                           (get rivi nimi)))
                       (tyhja-arvo? (if hae
                                      (hae rivi)
                                      (get rivi nimi)))))
            s))
        skeema))

(defn validoi-numero
  [numero alaraja ylaraja]
  (<= alaraja (str/replace numero "," ".") ylaraja))