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

(defn re-escape
  [teksti]
  (st/escape teksti {\. "\\." \\ "\\\\" \+ "\\+"
                     \* "\\*" \? "\\?" \^ "\\^"
                     \$ "\\$" \[ "\\[" \] "\\]"
                     \( "\\(" \) "\\)" \{ "\\{"
                     \} "\\}" \| "\\|" \/ "\\/"
                     \space "\\s"}))

(defn+ etsi-arvot-valilta
  "Tällä voi hakea stringin 'teksti' sisältä substringin alku- ja loppu-tekstin
   väliltä. Jos 'kaikki' arvo on true, palauttaa kaikki löydetyt arvot vektorissa
   muuten ensimmäisen osuman.

   esim. (etsi-arvot-valilta \"([:foo a] [:bar b])\" \"[\" \"]\" true)
          palauttaa [\":foo a\" \":bar b\"]"
   ([teksti string? alku-teksti string? loppu-teksti string] string?
    (etsi-arvot-valilta teksti alku-teksti loppu-teksti false))
   ([teksti string? alku-teksti string? loppu-teksti string? kaikki? ::boolean] string?
    (let [re (re-pattern (str (re-escape alku-teksti) "([^"
                              (re-escape loppu-teksti) "]+)"))]
      (if kaikki?
        (mapv second (re-seq re teksti))
        (second (re-find re teksti))))))

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



(defn tekstin-formatointi
  [teksti]
  (cond
    (and (re-find #"raportoi yhteyskatkoksista palveluissa" teksti)
         (re-find #"\]\]\]\)\]\]$" teksti)) :html
    (re-find #":td|:tr|:table|:div|:pre|:valign" teksti) :html-rikkinainen
    (and (re-find #"raportoi yhteyskatkoksista palveluissa" teksti)
         (re-find #"\}\]\}$" teksti)) :slack
    (re-find #":title|:value" teksti) :slack-rikkinainen
    :else :joku-rikkinainen))

(defn yhteyskatkokset-formatoinnille
  [loki-teksti aika {:keys [pvm? kello? kayttaja? palvelut? ensimmaiset-katkokset? viimeiset-katkokset? :as haettavat-tiedot-lokituksista]}
   {:keys [pvm kello kayttaja palvelut katkokset ensimmaiset-katkokset viimeiset-katkokset :as tekstit]}]
  (let [yhteyskatkoksien-metadata  (cond-> {}
                                           pvm? (assoc :pvm (apply etsi-arvot-valilta aika pvm))
                                           kello? (assoc :kello (apply etsi-arvot-valilta aika kello))
                                           kayttaja? (assoc :kayttaja (apply etsi-arvot-valilta loki-teksti kayttaja)))

        yhteyskatkos-tiedot [(when palvelut?
                               (apply etsi-arvot-valilta loki-teksti (conj palvelut true)))
                             (mapv (fn [x]
                                    (Integer. x))
                                   (apply etsi-arvot-valilta loki-teksti (conj katkokset true)))
                             (when ensimmaiset-katkokset?
                               (apply etsi-arvot-valilta loki-teksti (conj ensimmaiset-katkokset true)))
                             (when viimeiset-katkokset?
                               (apply etsi-arvot-valilta loki-teksti (conj viimeiset-katkokset true)))]
        map-vec->vec-map (fn [mappi-vektoreita]
                           (apply mapv (fn [& arvot]
                                         (zipmap (keys mappi-vektoreita) arvot))
                                       (vals mappi-vektoreita)))
        yhteyskatkokset (map-vec->vec-map
                          (cond-> {}
                                  palvelut? (assoc :palvelut (first yhteyskatkos-tiedot))
                                  true (assoc :katkokset (second yhteyskatkos-tiedot))
                                  ensimmaiset-katkokset? (assoc :ensimmaiset-katkokset (get yhteyskatkos-tiedot 2))
                                  viimeiset-katkokset? (assoc :viimeiset-katkokset (last yhteyskatkos-tiedot))))]
      (merge yhteyskatkoksien-metadata {:yhteyskatkokset yhteyskatkokset})))

(defn yhteyskatkokset-lokitus-string->yhteyskatkokset-map
  "Ottaa graylogista luetut yhteyskatkokslokituksen ja palauttaa kutsujan määrittämät
   tiedot mapissa. Katkoksien lukumäärä palautetaan aina.

   Kutsuja määrittää haluamansa tiedot mapissa boolean arvoina. Keywordit voivat olla
   :pvm
   :kello
   :kayttaja
   :palvelut
   :ensimmaiset-katkokset
   :viimeiset-katkokset
   , joista :pvm, :kello ja :kayttaja palauttavat yksittäisiä arvoja yhdestä
   lokituksesta, kun taas loput palauttavat vektoriarvoja."
  [lokitus haettavat-tiedot-lokituksista]
  (let [aika (first lokitus)
        loki-teksti (last lokitus)
        tekstin-formatointi (tekstin-formatointi loki-teksti)]
    (case tekstin-formatointi
      :html (yhteyskatkokset-formatoinnille loki-teksti aika haettavat-tiedot-lokituksista
                                            {:pvm ["" "T"]
                                             :kello ["T" "."]
                                             :kayttaja ["[:div Käyttäjä  " " "]
                                             :palvelut ["[:tr [:td {:valign top} [:b :" "]]"]
                                             :katkokset ["[:pre Katkoksia " " "]
                                             :ensimmaiset-katkokset ["ensimmäinen: " "viimeinen"]
                                             :viimeiset-katkokset ["viimeinen: " "]]]"]})
      :html-rikkinainen {:rikkinainen "foo"}
      :slack (yhteyskatkokset-formatoinnille loki-teksti aika haettavat-tiedot-lokituksista
                                             {:pvm ["" "T"]
                                              :kello ["T" "."]
                                              :kayttaja ["{:text \"\"Käyttäjä " " "]
                                              :palvelut ["{:title \"\"" "\"\""]
                                              :katkokset [", :value \"\"Katkoksia " " "]
                                              :ensimmaiset-katkokset ["ensimmäinen: " "(slack-n)"]
                                              :viimeiset-katkokset ["viimeinen: " "\"\""]})
      :slack-rikkinainen {:rikkinainen "foo"}
      :joku-rikkinainen {:rikkinainen "foo"})))

(defn jarjestele-yhteyskatkos-data-visualisointia-varten
  [yhteyskatkos-data ryhma-avain jarjestys-avain]
  (let [ryhmat-avattuna (mapcat #(map (fn [yhteyskatkokset-map]
                                        {:jarjestys-avain (if (jarjestys-avain %)
                                                            (jarjestys-avain %)
                                                            (jarjestys-avain yhteyskatkokset-map))
                                         :ryhma-avain (if (ryhma-avain %)
                                                        (ryhma-avain %)
                                                        (ryhma-avain yhteyskatkokset-map))
                                         :arvo-avain (:katkokset yhteyskatkokset-map)})
                                      (:yhteyskatkokset %))
                                yhteyskatkos-data)
        data-visualisointia-varten (reduce (fn [jarjestelty-data uusi-map]
                                             (let [loytynyt-jarjestys (some #(when (= (:jarjestys-avain %) (:jarjestys-avain uusi-map))
                                                                               %)
                                                                            jarjestelty-data)
                                                   loytynyt-ryhma (when loytynyt-jarjestys
                                                                     (first (filter #(= (:category %) (:ryhma-avain uusi-map))
                                                                                    (:yhteyskatkokset loytynyt-jarjestys))))
                                                   paivita-ryhma #(update %
                                                                          :yhteyskatkokset
                                                                          (fn [ryhmat]
                                                                           (if loytynyt-ryhma
                                                                             (mapv (fn [ryhma]
                                                                                     (if (= loytynyt-ryhma ryhma)
                                                                                       (update ryhma :value (fn [arvo]
                                                                                                              (+ arvo (:arvo-avain uusi-map))))
                                                                                       ryhma))
                                                                                   ryhmat)
                                                                             (conj ryhmat {:category (:ryhma-avain uusi-map)
                                                                                           :value (:arvo-avain uusi-map)}))))]
                                               (if loytynyt-jarjestys
                                                 (mapv #(if (= loytynyt-jarjestys %)
                                                          (paivita-ryhma %)
                                                          %)
                                                       jarjestelty-data)
                                                 (conj jarjestelty-data
                                                       {:jarjestys-avain (:jarjestys-avain uusi-map)
                                                        :yhteyskatkokset [{:category (:ryhma-avain uusi-map)
                                                                           :value (:arvo-avain uusi-map)}]}))))
                                           [] ryhmat-avattuna)]
    (sort-by :jarjestys-avain data-visualisointia-varten)))

(defn asetukset-kayttoon
  [data {:keys [jarjestys-avain naytettavat-ryhmat min-katkokset]}]
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

(defn bool-keyword
  [avain]
  (keyword (str (name avain) "?")))

(defn hae-yhteyskatkosten-data [{ryhma-avain :ryhma-avain jarjestys-avain :jarjestys-avain :as hakuasetukset} data-csvna ryhmana?]
  (let [graylogista-haetut-lokitukset (hae-yhteyskatkos-data data-csvna)
        haettavat-tiedot-lokituksista {(bool-keyword ryhma-avain) true (bool-keyword jarjestys-avain) true}
        yhteyskatkokset-mappina (map #(let [mappaus-yritys (yhteyskatkokset-lokitus-string->yhteyskatkokset-map % haettavat-tiedot-lokituksista)]
                                        (if (contains? mappaus-yritys :yhteyskatkokset)
                                          mappaus-yritys
                                          {:rikkinainen mappaus-yritys}))
                                     graylogista-haetut-lokitukset)
        rikkinaiset-mappaukset (count (filter :rikkinainen yhteyskatkokset-mappina))
        _ (log/debug "RIKKINAISET MAPPAUKSET: " rikkinaiset-mappaukset)
        onnistuneet-mappaukset (filter :yhteyskatkokset yhteyskatkokset-mappina)
        onnistuneet-mappaukset (if ryhmana?
                                  (map #(assoc % :yhteyskatkokset
                                                 (mapv (fn [mappi]
                                                         (assoc mappi :katkokset 1))
                                                       (:yhteyskatkokset %)))
                                       onnistuneet-mappaukset)
                                  onnistuneet-mappaukset)
        jarjestelty-data (jarjestele-yhteyskatkos-data-visualisointia-varten onnistuneet-mappaukset ryhma-avain jarjestys-avain)
        asetuksien-mukainen-data (asetukset-kayttoon jarjestelty-data hakuasetukset)]
    asetuksien-mukainen-data))

(defn hae-yhteyskatkosanalyysi
  [{analysointimetodi :analysointimetodi analyysit :analyysit :as hakuasetukset} data-csvna]
  (let [graylogista-haetut-lokitukset (hae-yhteyskatkos-data data-csvna)
        tehtava-analyysi? #(contains? analyysit %)
        haettavat-tiedot-lokituksista (cond-> #{}
                                              (tehtava-analyysi? :eniten-katkoksia) (conj :palvelut)
                                              (tehtava-analyysi? :pisimmat-katkokset) (conj :ensimmaiset-katkokset
                                                                                            :viimeiset-katkokset))
        yhteyskatkokset-mappina (map #(yhteyskatkokset-lokitus-string->yhteyskatkokset-map % haettavat-tiedot-lokituksista)
                                     graylogista-haetut-lokitukset)]))


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
    (http/julkaise-palvelu (:http-palvelin this)
                           :graylog-hae-analyysi
                           (fn [_ hakuasetukset]
                            (hae-yhteyskatkosanalyysi hakuasetukset (st/trim (:polku data-csvna)))))
    this)

  (stop [this]
    (http/poista-palvelut (:http-palvelin this) :graylog-hae-yhteyskatkokset :graylog-hae-yhteyskatkosryhma :graylog-hae-analyysi)
    this))
