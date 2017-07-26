(ns harja.palvelin.palvelut.graylog
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [harja.tyokalut.spec :refer [defn+ let+] :as sp]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as st]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.domain.graylog :as dgl]))

(defn s-pos-int [] (gen/gen-for-name 'clojure.test.check.generators/s-pos-int))
(s/def ::boolean #{true false})
(s/def ::nilable-fn (s/nilable fn?))
(s/def ::graylogista-luettu-itemi (s/with-gen vector?
                                              #(gen/fmap (fn [[date kayttaja palvelut katkokset ensimmaiset viimeiset]]
                                                            (let [palvelin "harja-app1-prod.solitaservices.fi"
                                                                  date (str (tc/from-date date))
                                                                  viesti (apply str "May 30 12:30:42 2017-toukokuuta-30 12:30:42 +0300 WARN [harja.palvelin.palvelut.selainvirhe] - "
                                                                                     "[:div Käyttäjä  " kayttaja " (11111)  raportoi yhteyskatkoksista palveluissa: [:table ("
                                                                                     (conj (mapv (fn [palvelu katkoksia ensimmainen viimeinen]
                                                                                                    (str "[:tr [:td {:valign top} [:b " (keyword palvelu) "]] "
                                                                                                         "[:td [:pre Katkoksia " katkoksia " kpl, "
                                                                                                         "ensimmäinen: " ensimmainen
                                                                                                         "viimeinen: " viimeinen "]]] "))
                                                                                                 palvelut katkokset (mapv tc/from-date ensimmaiset) (mapv tc/from-date viimeiset))
                                                                                           ")]]"))]
                                                                [date palvelin viesti]))

                                                         (let [eri-palveluiden-maara (inc (rand-int 10))]
                                                          (gen/tuple (dgl/date-gen)
                                                                     (gen/string-alphanumeric)
                                                                     (gen/vector (dgl/palvelu-gen) eri-palveluiden-maara)
                                                                     (gen/vector (s-pos-int) eri-palveluiden-maara)
                                                                     (gen/vector (dgl/date-gen) eri-palveluiden-maara)
                                                                     (gen/vector (dgl/date-gen) eri-palveluiden-maara))))))
(s/def ::csvsta-luettu-rivi-dataa (s/cat :aika (s/and string?
                                                      #(pvm/pvm? (tf/parse %)))
                                         :vali (s/* string?)
                                         :taulukko string?))
(s/def ::csvsta-luettu-data (s/coll-of ::graylogista-luettu-itemi))

(defn lue-csv
  [csv-file]
  (with-open [reader (io/reader csv-file)]
    (doall
      (csv/read-csv reader))))

(defn+ ilman-skandeja
  "Korjaa ääkköset"
  [teksti string?] string?
  (st/replace teksti #"�_" "ä"))


(defn+ etsi-arvot-valilta
  "Tällä voi hakea stringin 'teksti' sisältä substringin alku- ja loppu-tekstin
   väliltä. Jos 'kaikki' arvo on true, palauttaa kaikki löydetyt arvot vektorissa
   muuten ensimmäisen osuman.

   esim. (etsi-arvot-valilta \"([:foo a] [:bar b])\" \"[\" \"]\" true)
          palauttaa [\":foo a\" \":bar b\"]"
   ([teksti string? alku-teksti string? loppu-teksti string] string?
    (etsi-arvot-valilta teksti alku-teksti loppu-teksti false))
   ([teksti string? alku-teksti string? loppu-teksti string? kaikki? ::boolean] string?
    (loop [kasiteltava-teksti (ilman-skandeja teksti)
           tekstin-alku (if-let [alku-tekstin-alku (st/index-of kasiteltava-teksti alku-teksti)]
                          (+ alku-tekstin-alku (count alku-teksti)) nil)
           tekstin-loppu (if tekstin-alku (st/index-of kasiteltava-teksti loppu-teksti tekstin-alku) nil)
           arvo (if (and tekstin-alku tekstin-loppu)
                  [(subs kasiteltava-teksti tekstin-alku tekstin-loppu)]
                  nil)]
        (if (and tekstin-alku tekstin-loppu)
          (if kaikki?
              (let [kasiteltava-teksti (subs kasiteltava-teksti tekstin-loppu)
                    tekstin-alku (if (st/index-of kasiteltava-teksti alku-teksti) (+ (st/index-of kasiteltava-teksti alku-teksti) (count alku-teksti)) nil)
                    tekstin-loppu (if tekstin-alku (st/index-of kasiteltava-teksti loppu-teksti tekstin-alku) nil)
                    uusi-arvo (if (and tekstin-alku tekstin-loppu) (subs kasiteltava-teksti tekstin-alku tekstin-loppu) nil)]
                (recur
                  kasiteltava-teksti
                  tekstin-alku
                  tekstin-loppu
                  (if uusi-arvo
                    (conj arvo uusi-arvo)
                    arvo)))
              (first arvo))
          arvo))))

(defn hae-yhteyskatkos-data
  "Lukee csv tiedoston poistaen header-rivin.
   Käyttää pilkkua sarakkeiden erottelemisessa"
  [data-csvna]
  (rest (lue-csv data-csvna)))

(defn+ tekstin-hyvaksymis-fn
  [optio? ::boolean re s/regex?] ::nilable-fn
  (when (not optio?)
   (fn [teksti]
      (if (and (string? teksti) (re-find re teksti)) nil teksti))))

(defn parsi-string-data
  [parsittava-data aika & avaimet]
  (let [pvm? (some #(= :pvm %) avaimet)
        kello? (some #(= :kello %) avaimet)
        kayttaja? (some #(= :kayttaja %) avaimet)]
    (map-indexed #(cond-> {}
                          pvm? (assoc :pvm (etsi-arvot-valilta (get aika %1) "" "T"))
                          kello? (assoc :kello (etsi-arvot-valilta  (get aika %1) "T" "."))
                          kayttaja? (assoc :kayttaja (etsi-arvot-valilta %2 "[:div Käyttäjä  " " ")))
                 parsittava-data)))
(defn parsi-vektori-data
  [parsittava-data & avaimet]
  (let [palvelut? (some #(= :palvelut %) avaimet)
        ensimmaiset-katkokset? (some #(= :ensimmaiset-katkokset %) avaimet)
        viimeiset-katkokset? (some #(= :viimeiset-katkokset %) avaimet)]
    (map #(let [data [(when palvelut?
                        (etsi-arvot-valilta % "[:tr [:td {:valign top} [:b :" "]]" true))
                      (mapv (fn [x] (Integer. x)) (etsi-arvot-valilta % "[:pre Katkoksia " " " true))
                      (when ensimmaiset-katkokset?
                        (etsi-arvot-valilta % "ensimmäinen: " "viimeinen" true))
                      (when viimeiset-katkokset?
                        (etsi-arvot-valilta % "viimeinen: " "]]]" true))]
                map-vec->vec-map (fn [mappi-vektoreita]
                                   (apply mapv (fn [& arvot]
                                                 (zipmap (keys mappi-vektoreita) arvot))
                                               (vals mappi-vektoreita)))]
              (map-vec->vec-map
                (cond-> {}
                        palvelut? (assoc :palvelut (first data))
                        true (assoc :katkokset (second data))
                        ensimmaiset-katkokset? (assoc :ensimmaiset-katkokset (get data 2))
                        viimeiset-katkokset? (assoc :viimeiset-katkokset (last data)))))
         parsittava-data)))

(defn parsi-yhteyskatkos-data
  "Ottaa csv:stä luetun datan ja käyttäjän antamat optiot.
   Mikäli optioita ei ole annettu, lukee kaiken mahdolliset arvot datasta
   ja sijoittaa ne mappiin. Optiota ovat :pvm :kello :kayttaja :palvelut
   :katkokset :ensimmaiset-katkokset ja :viimeiset-katkokset"
  [data {:keys [ryhma-avain jarjestys-avain naytettavat-ryhmat min-katkokset]}]
  (let [aika (mapv first data)
        taulukko (mapv last data)
        parsittu-string-data (parsi-string-data taulukko aika ryhma-avain jarjestys-avain)
        parsittu-vektori-data (parsi-vektori-data taulukko ryhma-avain jarjestys-avain)
        parsittu-data (map #(merge %1 {:yhteyskatkokset %2}) parsittu-string-data parsittu-vektori-data)]
    parsittu-data))


(s/fdef parsi-yhteyskatkos-data
  :args (s/cat :data ::csvsta-luettu-data
               :ryhma-avain (s/nilable keyword?)
               :jarjestys-avain (s/nilable keyword?)
               :naytettavat-ryhmat (s/nilable set?)
               :min-katkokset (s/nilable (s/and integer? pos?)))
  :ret ::dgl/parsittu-yhteyskatkos-data)

(defn jarjestele-yhteyskatkos-data
  [yhteyskatkos-data ryhma-avain jarjestys-avain]
  (let [ryhmien-avaaminen (mapcat #(map (fn [yhteyskatkokset-map]
                                          {:jarjestys-avain (if (jarjestys-avain %)
                                                              (jarjestys-avain %)
                                                              (jarjestys-avain yhteyskatkokset-map))
                                           :ryhma-avain (if (ryhma-avain %)
                                                          (ryhma-avain %)
                                                          (ryhma-avain yhteyskatkokset-map))
                                           :arvo-avain (:katkokset yhteyskatkokset-map)})
                                        (:yhteyskatkokset %))
                                  yhteyskatkos-data)]
      (reduce (fn [jarjestelty-data uusi-map]
                (let [loytynyt-jarjestys (some #(when (= (:jarjestys-avain %) (:jarjestys-avain uusi-map))
                                                  %)
                                               jarjestelty-data)
                      loytynyt-ryhma (when loytynyt-jarjestys
                                        (first (filter #(= (:category %) (:ryhma-avain uusi-map))
                                                       (:yhteyskatkokset loytynyt-jarjestys))))]
                  (if loytynyt-jarjestys
                    (if loytynyt-ryhma
                      (mapv #(if (= loytynyt-jarjestys %)
                               (assoc %
                                      :yhteyskatkokset
                                      (mapv (fn [ryhma]
                                              (if (= loytynyt-ryhma ryhma)
                                                (update ryhma :value (fn [arvo]
                                                                       (+ arvo (:arvo-avain uusi-map))))
                                                ryhma))
                                            (:yhteyskatkokset %)))
                               %)
                            jarjestelty-data)
                      (mapv #(if (= loytynyt-jarjestys %)
                               (update %
                                       :yhteyskatkokset
                                       (fn [ryhmat]
                                         (conj ryhmat {:category (:ryhma-avain uusi-map)
                                                       :value (:arvo-avain uusi-map)})))
                               %)
                            jarjestelty-data))
                    (conj jarjestelty-data
                          {:jarjestys-avain (:jarjestys-avain uusi-map)
                           :yhteyskatkokset [{:category (:ryhma-avain uusi-map)
                                              :value (:arvo-avain uusi-map)}]}))))
              [] ryhmien-avaaminen)))

(defn asetukset-kayttoon
  [data {:keys [ryhma-avain jarjestys-avain naytettavat-ryhmat min-katkokset]}]
  (let [ryhma-annettu? #(if (nil? naytettavat-ryhmat)
                          true
                          (contains? naytettavat-ryhmat %))
        hae? (ryhma-annettu? :hae)
        tallenna? (ryhma-annettu? :tallenna)
        urakka? (ryhma-annettu? :urakka)
        muut? (ryhma-annettu? :muut)
        palvelu-jarjestyksena? (= jarjestys-avain :palvelut)
        min-katkokset (or min-katkokset 0)
        hae-fn (tekstin-hyvaksymis-fn hae? #"^(hae-)")
        tallenna-fn (tekstin-hyvaksymis-fn tallenna? #"^(tallenna-)")
        urakka-fn (tekstin-hyvaksymis-fn urakka? #"^(urakan-)")
        muut-fn (tekstin-hyvaksymis-fn muut? #"^(?!hae-|tallenna-|urakan-)")
        palvelujen-poisto-fn (apply comp (keep identity [hae-fn tallenna-fn urakka-fn muut-fn]))
        katkoksien-poisto-fn (fn [katkokset] (when (> katkokset min-katkokset) katkokset))
        palvelu-asetukset-kayttoon (vec
                                    (if palvelu-jarjestyksena?
                                      (keep #(when (palvelujen-poisto-fn (:jarjestys-avain %))
                                               %)
                                            data)
                                      (keep #(let [yhteyskatkokset-seq (keep (fn [mappi]
                                                                               (when (palvelujen-poisto-fn (:category mappi))
                                                                                 mappi))
                                                                             (:yhteyskatkokset %))]
                                                (when (not (empty? yhteyskatkokset-seq))
                                                  (assoc % :yhteyskatkokset (vec yhteyskatkokset-seq))))
                                            data)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Tällä voi näyttää palvelukohtaisten katkoksien määrät ;;;;;;;;;;;;
        ; katkos-asetukset-kayttoon (vec (if palvelu-jarjestyksena?
        ;                                   (keep #(let [katkoksien-maara (apply + (map :value (:yhteyskatkokset %)))]
        ;                                             (when (katkoksien-poisto-fn katkoksien-maara)
        ;                                               %))
        ;                                         palvelu-asetukset-kayttoon)
        ;                                   (let [luo-category-value-map (fn [& maps]
        ;                                                                 (reduce #(let [lisattava-pari {(keyword (:category %2)) (:value %2)}]
        ;                                                                           (merge-with + %1 lisattava-pari))
        ;                                                                         {} maps))
        ;                                         katkoksien-maarat (apply luo-category-value-map (mapcat :yhteyskatkokset palvelu-asetukset-kayttoon))]
        ;                                       (keep #(let [yhteyskatkokset-seq (keep (fn [mappi]
        ;                                                                                (let [kokonais-katkosten-maara ((keyword (:category mappi)) katkoksien-maarat)]
        ;                                                                                  (when (katkoksien-poisto-fn kokonais-katkosten-maara)
        ;                                                                                    mappi)))
        ;                                                                              (:yhteyskatkokset %))]
        ;                                                 (when (not (empty? yhteyskatkokset-seq))
        ;                                                   (assoc % :yhteyskatkokset (vec yhteyskatkokset-seq))))
        ;                                             palvelu-asetukset-kayttoon))))]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        katkos-asetukset-kayttoon (keep #(let [yhteyskatkokset-seq (keep (fn [mappi]
                                                                           (when (katkoksien-poisto-fn (:value mappi))
                                                                             mappi))
                                                                         (:yhteyskatkokset %))]
                                            (when (not (empty? yhteyskatkokset-seq))
                                              (assoc % :yhteyskatkokset (vec yhteyskatkokset-seq))))
                                        palvelu-asetukset-kayttoon)]
    katkos-asetukset-kayttoon))

(defn hae-yhteyskatkosten-data [{ryhma-avain :ryhma-avain jarjestys-avain :jarjestys-avain :as hakuasetukset} data-csvna ryhmana?]
    (println "RYHMANA?: " ryhmana?)
    (let [data (hae-yhteyskatkos-data data-csvna)
          parsittu-data (parsi-yhteyskatkos-data data hakuasetukset)
          parsittu-data (if ryhmana?
                          (map #(map (fn [mappi]
                                       (assoc mappi :katkokset 1))
                                     (:yhteyskatkokset %))
                               parsittu-data)
                          parsittu-data)
          jarjestelty-data (jarjestele-yhteyskatkos-data parsittu-data ryhma-avain jarjestys-avain)
          asetuksien-mukainen-data (asetukset-kayttoon jarjestelty-data hakuasetukset)]
      asetuksien-mukainen-data))

(defrecord Graylog [data-csvna]
  component/Lifecycle
  (start [this]
    (http/julkaise-palvelu (:http-palvelin this)
                           :graylog-hae-yhteyskatkokset
                           (fn [_ hakuasetukset]
                            (hae-yhteyskatkosten-data hakuasetukset (st/trim (:polku data-csvna)) false)))
    (http/julkaise-palvelu (:http-palvelin this)
                           :graylog-hae-yhteyskatkosryhma
                           (fn [_ hakuasetukset]
                            (hae-yhteyskatkosten-data hakuasetukset (st/trim (:polku data-csvna)) true)))
    this)

  (stop [this]
    (http/poista-palvelut (:http-palvelin this) :graylog-hae-yhteyskatkokset :graylog-hae-yhteyskatkosryhma)
    this))
