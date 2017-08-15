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
                              (re-escape loppu-teksti) "]+)"))
          teksti (ilman-skandeja teksti)]
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
         (re-find #"\]\]\]\)\]\]$|\]\]\]\)\]\]\"$" teksti)) :html
    (re-find #":td|:tr|:table|:div|:pre|:valign" teksti) :html-rikkinainen
    (and (re-find #"raportoi yhteyskatkoksista palveluissa:," teksti)
         (re-find #"\}\]\}\"$" teksti)) :slack1
    (and (re-find #"raportoi yhteyskatkoksista palveluissa:\"\"" teksti)
         (re-find #"\}\]\}\"$" teksti)) :slack2
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
                                       (keep #(when-not (empty? %) %) (vals mappi-vektoreita))))
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
                                            {:pvm ["" ","]
                                             :kello ["T" "."]
                                             :kayttaja ["[:div Käyttäjä  " " "]
                                             :palvelut ["[:tr [:td {:valign top} [:b :" "]]"]
                                             :katkokset ["[:pre Katkoksia " " "]
                                             :ensimmaiset-katkokset ["ensimmäinen: " "viimeinen"]
                                             :viimeiset-katkokset ["viimeinen: " "]]]"]})
      :html-rikkinainen {:rikkinainen "foo"}
      :slack1 (yhteyskatkokset-formatoinnille loki-teksti aika haettavat-tiedot-lokituksista
                                             {:pvm ["" ","]
                                              :kello ["T" "."]
                                              :kayttaja ["{:text Käyttäjä " " "]
                                              :palvelut ["{:title :" ", "]
                                              :katkokset [", :value Katkoksia " " "]
                                              :ensimmaiset-katkokset ["ensimmäinen: " "(slack-n)"]
                                              :viimeiset-katkokset ["viimeinen: " "}"]})
      :slack2 (yhteyskatkokset-formatoinnille loki-teksti aika haettavat-tiedot-lokituksista
                                             {:pvm ["" ","]
                                              :kello ["T" "."]
                                              :kayttaja ["{:text \"\"Käyttäjä " " "]
                                              :palvelut ["{:title \"\":" "\"\""]
                                              :katkokset [", :value \"\"Katkoksia " " "]
                                              :ensimmaiset-katkokset ["ensimmäinen: " "(slack-n)"]
                                              :viimeiset-katkokset ["viimeinen: " "\"\""]})
      :slack-rikkinainen {:rikkinainen (yhteyskatkokset-formatoinnille loki-teksti aika haettavat-tiedot-lokituksista
                                             {:pvm ["" ","]
                                              :kello ["T" "."]
                                              :kayttaja ["{:text Käyttäjä " " "]
                                              :palvelut ["{:title :" ", "]
                                              :katkokset [", :value Katkoksia " " "]
                                              :ensimmaiset-katkokset ["ensimmäinen: " "(slack-n)"]
                                              :viimeiset-katkokset ["viimeinen: " "}"]})}
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
(defn palvelujen-poisto-fn
  [naytettavat-ryhmat]
  (let [ryhma-annettu? #(if (nil? naytettavat-ryhmat)
                          true
                          (contains? naytettavat-ryhmat %))
        hae? (ryhma-annettu? :hae)
        tallenna? (ryhma-annettu? :tallenna)
        urakka? (ryhma-annettu? :urakka)
        muut? (ryhma-annettu? :muut)
        hae-fn (tekstin-hyvaksymis-fn hae? #"^(hae-)")
        tallenna-fn (tekstin-hyvaksymis-fn tallenna? #"^(tallenna-)")
        urakka-fn (tekstin-hyvaksymis-fn urakka? #"^(urakan-)")
        muut-fn (tekstin-hyvaksymis-fn muut? #"^(?!hae-|tallenna-|urakan-)")]
      (apply comp (keep identity [hae-fn tallenna-fn urakka-fn muut-fn]))))

(defn asetukset-kayttoon
  [data {:keys [jarjestys-avain naytettavat-ryhmat min-katkokset]}]
  (let [palvelu-jarjestyksena? (= jarjestys-avain :palvelut)
        min-katkokset (or min-katkokset 0)
        palvelujen-poisto-fn (palvelujen-poisto-fn naytettavat-ryhmat)
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

(defn analyysin-hakuasetukset-kayttoon
  [onnistunut-mappaus {:keys [naytettavat-ryhmat ping-erikseen?]}]
  (let [palvelujen-poisto-fn (palvelujen-poisto-fn naytettavat-ryhmat)
        katkostiedot (keep (fn [{:keys [palvelut] :as mappi}]
                              (if (and (= "ping" palvelut) ping-erikseen?)
                                mappi
                                (when (palvelujen-poisto-fn palvelut)
                                  mappi)))
                           (:yhteyskatkokset onnistunut-mappaus))]
     (if (empty? katkostiedot)
       nil
       (assoc onnistunut-mappaus :yhteyskatkokset (vec katkostiedot)))))

(defn yrita-korjata
  [rikkinainen]
  (map #(hash-map :palvelut (get-in % [:rikkinainen :yhteyskatkokset 0 :palvelut])
                  :katkokset (get-in % [:rikkinainen :yhteyskatkokset 0 :katkokset]))
       rikkinainen))

(defn analyysit-yhteyskatkoksista
  [yhteyskatkokset {analysointimetodi :analysointimetodi haettavat-analyysit :haettavat-analyysit}]
  (let [ok-yhteyskatkos-data (filter :yhteyskatkokset yhteyskatkokset)
        rikkinaiset-lokitukset (when (contains? haettavat-analyysit :rikkinaiset-lokitukset)
                                  (count (filter #(when-let [arvo (:rikkinainen %)]
                                                    (when (or (:kayttaja arvo) ; jotta sama rikkinainen lasketaan vain kerran
                                                              (= arvo "foo"))
                                                      true))
                                                 yhteyskatkokset)))
        eheytetyt-yhteyskatkokset (when (contains? haettavat-analyysit :rikkinaiset-lokitukset)
                                    (yrita-korjata (filter #(when-let [arvo (:rikkinainen %)]
                                                              (when-not (= arvo "foo")
                                                                arvo))
                                                           yhteyskatkokset)))
        eheytetyt-yhteyskatkokset-lkm (when (contains? haettavat-analyysit :rikkinaiset-lokitukset)
                                        (count eheytetyt-yhteyskatkokset))
        yhteyskatkokset-ryhmittain (when (contains? haettavat-analyysit :eniten-katkosryhmia)
                                    (mapcat #(mapv (fn [mappi]
                                                     (assoc mappi :katkokset 1))
                                                   (:yhteyskatkokset %))
                                            ok-yhteyskatkos-data))
        yhteyskatkokset (when (contains? haettavat-analyysit :eniten-katkoksia)
                          (if (contains? haettavat-analyysit :rikkinaiset-lokitukset)
                            (concat (mapcat :yhteyskatkokset ok-yhteyskatkos-data) eheytetyt-yhteyskatkokset)
                            (mapcat :yhteyskatkokset ok-yhteyskatkos-data)))
        yhteyskatkokset-ryhmittain (map #(assoc % :katkokset 1) yhteyskatkokset)
        ota-mapin-n-suurinta-arvoa #(into {}
                                      (take-last %2 (sort-by (fn [mappi]
                                                              (second mappi))
                                                             %1)))
        eniten-katkosryhmia (when (contains? haettavat-analyysit :eniten-katkosryhmia)
                              (ota-mapin-n-suurinta-arvoa (reduce #(if (contains? %1 (:palvelut %2))
                                                                     (update %1 (:palvelut %2) inc)
                                                                     (assoc %1 (:palvelut %2) 1))
                                                                  {} yhteyskatkokset-ryhmittain)
                                                          5))
        eniten-katkoksia (when (contains? haettavat-analyysit :eniten-katkoksia)
                          (ota-mapin-n-suurinta-arvoa (reduce #(if (contains? %1 (:palvelut %2))
                                                                 (update %1 (:palvelut %2) (fn [palvelun-katkokset]
                                                                                             (+ palvelun-katkokset (:katkokset %2))))
                                                                 (assoc %1 (:palvelut %2) (:katkokset %2)))
                                                              {} yhteyskatkokset)
                                                      5))

        katkoksien-pituudet #(let [ensimmainen-ms (when-let [ek (:ensimmaiset-katkokset %)]
                                                    (.getTime (pvm/dateksi ek)))
                                   viimeinen-ms (when-let [vk (:viimeiset-katkokset %)]
                                                 (.getTime (pvm/dateksi vk)))]
                              (if (and ensimmainen-ms viimeinen-ms)
                                (Math/abs (- viimeinen-ms ensimmainen-ms)) ;abs, koska lokituksessa oli bugi alussa, jolloin ensimmainen olikin viimeinen
                                0))
        pisimmat-katkokset (when (contains? haettavat-analyysit :pisimmat-katkokset)
                              (ota-mapin-n-suurinta-arvoa (reduce #(if (contains? %1 (:palvelut %2))
                                                                     (update %1 (:palvelut %2) (fn [palvelun-katkoksen-pituus]
                                                                                                 (let [tarkasteltavan-mapin-katkoksen-pituus (katkoksien-pituudet %2)]
                                                                                                   (if (> tarkasteltavan-mapin-katkoksen-pituus palvelun-katkoksen-pituus)
                                                                                                     tarkasteltavan-mapin-katkoksen-pituus
                                                                                                     palvelun-katkoksen-pituus))))
                                                                     (assoc %1 (:palvelut %2) (katkoksien-pituudet %2)))
                                                                  {} yhteyskatkokset)
                                                          5))
        selain-sammutettu-katkoksen-aikana (when (contains? haettavat-analyysit :selain-sammutettu-katkoksen-aikana)
                                             (reduce #(let [lokitus-tapahtui (.getTime (pvm/dateksi (:pvm %2)))
                                                            ping-yhteyskatkokset (some (fn [palvelun-katkokset]
                                                                                         (when (= "ping" (:palvelut palvelun-katkokset))
                                                                                           palvelun-katkokset))
                                                                                       (:yhteyskatkokset %2))
                                                            ; Tämä tehdään siltä varalta, että pingiä ei kerettyä tehdä. Siinä tapauksessa otetaan vain joku palvelukutsu
                                                            ping-yhteyskatkokset (if ping-yhteyskatkokset
                                                                                    ping-yhteyskatkokset
                                                                                    (first (:yhteyskatkokset %2)))
                                                            viimeinen-pingaus (if (> (.getTime (pvm/dateksi (:viimeiset-katkokset ping-yhteyskatkokset)))
                                                                                     (.getTime (pvm/dateksi (:ensimmaiset-katkokset ping-yhteyskatkokset))))
                                                                                 (.getTime (pvm/dateksi (:viimeiset-katkokset ping-yhteyskatkokset)))
                                                                                 (.getTime (pvm/dateksi (:ensimmaiset-katkokset ping-yhteyskatkokset))))
                                                            lokituksen-ja-pingauksen-vali (- lokitus-tapahtui viimeinen-pingaus)
                                                            kutsutut-palvelut (keep (fn [palvelun-katkokset]
                                                                                      (if (= "ping" (:palvelut palvelun-katkokset))
                                                                                        nil
                                                                                        (:palvelut palvelun-katkokset)))
                                                                                    (:yhteyskatkokset %2))]
                                                           (if (> lokituksen-ja-pingauksen-vali 10000)
                                                             (merge-with + %1 (zipmap kutsutut-palvelut
                                                                                      (repeat (count kutsutut-palvelut) 1)))
                                                             %1))
                                                     {} ok-yhteyskatkos-data))]
    {:eniten-katkoksia eniten-katkoksia :pisimmat-katkokset pisimmat-katkokset
     :rikkinaiset-lokitukset rikkinaiset-lokitukset :eniten-katkosryhmia eniten-katkosryhmia
     :selain-sammutettu-katkoksen-aikana selain-sammutettu-katkoksen-aikana
     :eheytetyt-yhteyskatkokset-lkm eheytetyt-yhteyskatkokset-lkm}))

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
        onnistuneet-mappaukset (map #(assoc % :pvm (etsi-arvot-valilta (:pvm %) "" "T")) onnistuneet-mappaukset)
        jarjestelty-data (jarjestele-yhteyskatkos-data-visualisointia-varten onnistuneet-mappaukset ryhma-avain jarjestys-avain)
        asetuksien-mukainen-data (asetukset-kayttoon jarjestelty-data hakuasetukset)]
    asetuksien-mukainen-data))

(defn hae-yhteyskatkosanalyysi
  [{:keys [analysointimetodi haettavat-analyysit hakuasetukset] :as analyysihaku} data-csvna]
  (let [graylogista-haetut-lokitukset (hae-yhteyskatkos-data data-csvna)
        tehtava-analyysi? #(contains? haettavat-analyysit %)
        haettavat-tiedot-lokituksista (cond-> #{}
                                              (tehtava-analyysi? :eniten-katkoksia) (conj :palvelut)
                                              (tehtava-analyysi? :pisimmat-katkokset) (conj :ensimmaiset-katkokset
                                                                                            :viimeiset-katkokset)
                                              (tehtava-analyysi? :eniten-katkosryhmia) (conj :palvelut)
                                              (tehtava-analyysi? :rikkinaiset-lokitukset) (conj :kayttaja)
                                              (tehtava-analyysi? :selain-sammutettu-katkoksen-aikana) (conj :ensimmaiset-katkokset
                                                                                                            :viimeiset-katkokset
                                                                                                            :palvelut :pvm :kello)
                                              (tehtava-analyysi? :vaihdettu-nakymaa-katkoksen-aikana) (conj :palvelut :pvm :kello)
                                              (tehtava-analyysi? :monellako-kayttajalla) (conj :kayttaja))
        ping-erikseen? (when (and (not (get-in hakuasetukset [:naytettavat-ryhmat :muut]))
                                  (tehtava-analyysi? :selain-sammutettu-katkoksen-aikana))
                          true)
        haettavat-tiedot-lokituksista (zipmap (map bool-keyword haettavat-tiedot-lokituksista)
                                              (repeat (count haettavat-tiedot-lokituksista) true))
        yhteyskatkokset-mappina (keep #(let [mappaus-yritys (yhteyskatkokset-lokitus-string->yhteyskatkokset-map % haettavat-tiedot-lokituksista)]
                                        (if (contains? mappaus-yritys :rikkinainen)
                                          mappaus-yritys
                                          (analyysin-hakuasetukset-kayttoon mappaus-yritys (assoc hakuasetukset :ping-erikseen? ping-erikseen?))))
                                      graylogista-haetut-lokitukset)
        analyysit (analyysit-yhteyskatkoksista yhteyskatkokset-mappina analyysihaku)
        pingin-poisto-fn #(into {} (map (fn [[analyysi tulos]]
                                          [analyysi (if (map? tulos) (dissoc tulos "ping") tulos)])
                                        %))]
      (if ping-erikseen?
        (pingin-poisto-fn analyysit)
        analyysit)))


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
                           (fn [_ analyysihaku]
                            (hae-yhteyskatkosanalyysi analyysihaku (st/trim (:polku data-csvna)))))
    this)

  (stop [this]
    (http/poista-palvelut (:http-palvelin this) :graylog-hae-yhteyskatkokset :graylog-hae-yhteyskatkosryhma :graylog-hae-analyysi)
    this))
