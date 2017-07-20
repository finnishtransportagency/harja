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

(defn parsi-yhteyskatkos-data
  "Ottaa csv:stä luetun datan ja käyttäjän antamat optiot.
   Mikäli optioita ei ole annettu, lukee kaiken mahdolliset arvot datasta
   ja sijoittaa ne mappiin. Optiota ovat :pvm :kello :kayttaja :palvelut
   :katkokset :ensimmaiset-katkokset ja :viimeiset-katkokset"
  [data {:keys [pvm? kello? kayttaja? palvelut? ensimmaiset-katkokset?
                viimeiset-katkokset? naytettavat-ryhmat min-katkokset]}]
  (let [pvm? (or pvm? true)
        kello? (or kello? true)
        kayttaja? (or kayttaja? true)
        palvelut? (or palvelut? true)
        ensimmaiset-katkokset? (or ensimmaiset-katkokset? true)
        viimeiset-katkokset? (or viimeiset-katkokset? true)
        hae? (if (nil? naytettavat-ryhmat) true (if (:hae naytettavat-ryhmat) true false))
        tallenna? (if (nil? naytettavat-ryhmat) true (if (:tallenna naytettavat-ryhmat) true false))
        urakka? (if (nil? naytettavat-ryhmat) true (if (:urakka naytettavat-ryhmat) true false))
        muut? (if (nil? naytettavat-ryhmat) true (if (:muut naytettavat-ryhmat) true false))
        min-katkokset (or min-katkokset 0)
        hae-fn (tekstin-hyvaksymis-fn hae? #"^(hae-)")
        tallenna-fn (tekstin-hyvaksymis-fn tallenna? #"^(tallenna-)")
        urakka-fn (tekstin-hyvaksymis-fn urakka? #"^(urakan-)")
        muut-fn (tekstin-hyvaksymis-fn muut? #"^(?!hae-|tallenna-|urakan-)")
        palvelujen-poisto-fn (apply comp (keep identity [hae-fn tallenna-fn urakka-fn muut-fn]))
        katkoksien-poisto-fn (fn [katkokset] (when (> katkokset min-katkokset) katkokset))
        aika (mapv first data)
        taulukko (mapv last data)]
    (map-indexed #(cond-> {}
                          pvm? (assoc :pvm (etsi-arvot-valilta (get aika %1) "" "T"))
                          kello? (assoc :kello (etsi-arvot-valilta  (get aika %1) "T" "."))
                          kayttaja? (assoc :kayttaja (etsi-arvot-valilta %2 "[:div Käyttäjä  " " "))
                          palvelut? (assoc :palvelut (mapv (fn [x] (palvelujen-poisto-fn x)) (etsi-arvot-valilta %2 "[:tr [:td {:valign top} [:b :" "]]" true)))
                          true (assoc :katkokset (mapv (fn [x] (katkoksien-poisto-fn (Integer/parseInt x))) (etsi-arvot-valilta %2 "[:pre Katkoksia " " " true)))
                          ensimmaiset-katkokset? (assoc :ensimmaiset-katkokset (etsi-arvot-valilta %2 "ensimmäinen: " "viimeinen" true))
                          viimeiset-katkokset? (assoc :viimeiset-katkokset (etsi-arvot-valilta %2 "viimeinen: " "]]]" true)))
                 taulukko)))
(s/fdef parsi-yhteyskatkos-data
  :args (s/coll-of (s/cat :data ::csvsta-luettu-data :pvm? (s/nilable ::boolean) :kello? (s/nilable ::boolean)
                          :kayttaja? (s/nilable ::boolean) :palvelut? (s/nilable ::boolean)
                          :ensimmaiset-katkokset? (s/nilable ::boolean)
                          :viimeiset-katkokset? (s/nilable ::boolean) :naytettavat-ryhmat (s/nilable set?)
                          :min-katkokset (s/nilable (s/and pos? integer?))))
  :ret ::dgl/parsittu-yhteyskatkos-data)

(defn hae-yhteyskatkosten-data [hakuasetukset data-csvna]
    (let [data (hae-yhteyskatkos-data data-csvna)
          parsittu-data (parsi-yhteyskatkos-data data hakuasetukset)]
      parsittu-data))

(defrecord Graylog [data-csvna]
  component/Lifecycle
  (start [this]
    (http/julkaise-palvelu (:http-palvelin this)
                           :graylog-hae-yhteyskatkokset
                           (fn [_ hakuasetukset]
                            (hae-yhteyskatkosten-data hakuasetukset (st/trim (:polku data-csvna)))))
    this)

  (stop [this]
    (http/poista-palvelu (:http-palvelin this) :graylog-hae-yhteyskatkokset)
    this))
