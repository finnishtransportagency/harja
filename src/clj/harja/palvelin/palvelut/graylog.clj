(ns harja.palvelin.palvelut.graylog
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as st]
            [clj-time.coerce :as tc]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.graylog-muunnokset :as muunnokset]
            [harja.palvelin.tyokalut.graylog-analyysit :as analyysit]
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

(defn hae-yhteyskatkos-data
  "Lukee csv tiedoston poistaen header-rivin.
   Käyttää pilkkua sarakkeiden erottelemisessa"
  [data-csvna]
  (rest (lue-csv data-csvna)))

(defn bool-keyword
  [avain]
  (keyword (str (name avain) "?")))

(defn asetuksien-kytkenta
  [yhteyskatkokset-map hakuasetukset haku-avain]
  (let [asetukset-fn (fn [yhteyskatkokset-map]
                       (muunnokset/asetukset-kayttoon haku-avain
                                                      yhteyskatkokset-map
                                                      hakuasetukset))]
    (cond
      (get-in yhteyskatkokset-map [:rikkinainen :yhteyskatkokset]) {:rikkinainen (asetukset-fn (:rikkinainen yhteyskatkokset-map))}
      (:yhteyskatkokset yhteyskatkokset-map) (asetukset-fn yhteyskatkokset-map)
      :else yhteyskatkokset-map)))

(defn hae-yhteyskatkosten-data-visualisointia-varten [{ryhma-avain :ryhma-avain jarjestys-avain :jarjestys-avain :as hakuasetukset} data-csvna ryhmana?]
  (let [graylogista-haetut-lokitukset (hae-yhteyskatkos-data data-csvna)
        haettavat-tiedot-lokituksista {(bool-keyword ryhma-avain) true (bool-keyword jarjestys-avain) true}
        yhteyskatkokset-mappina (map #(muunnokset/yhteyskatkokset-lokitus-string->yhteyskatkokset-map % haettavat-tiedot-lokituksista)
                                      graylogista-haetut-lokitukset)
        yhteyskatkokset (if ryhmana?
                          (map #(muunnokset/yhteyskatkokset-ryhmittain %)
                               yhteyskatkokset-mappina)
                          yhteyskatkokset-mappina)
        rikkinaisten-kasittely (keep #(cond
                                        (:yhteyskatkokset %) %
                                        (get-in % [:rikkinainen :yhteyskatkokset]) (:rikkinainen %)
                                        :else nil)
                                     yhteyskatkokset)
        ryhma-jarjestys-map (mapcat (fn [mappi]
                                      (map #(merge %
                                                  (dissoc mappi :yhteyskatkokset))
                                           (:yhteyskatkokset mappi)))
                                    rikkinaisten-kasittely)
        yhteyskatkokset (if (or (= ryhma-avain :pvm)
                                (= jarjestys-avain :pvm))
                           ;; Jos halutaan näyttää dataa päivämäärän mukaan, niin oletettavasti tarkoitetaan
                           ;; vain päivämäärää eikä millisekunnin tarkkaa aikaa. Sen takia tehdään tämä muunnos.
                           (map #(assoc % :pvm (pvm/paiva-kuukausi (:pvm %))) ryhma-jarjestys-map)
                           ryhma-jarjestys-map)
        yhteyskatkokset (apply muunnokset/yhdista-avaimet-kun + :katkokset [(muunnokset/avain-monikko->yksikko ryhma-avain) (muunnokset/avain-monikko->yksikko jarjestys-avain)] yhteyskatkokset)
        asetukset-kayttoon (keep #(muunnokset/asetukset-kayttoon :diagrammi % hakuasetukset)
                                 yhteyskatkokset)
        jarjestelty-data (muunnokset/jarjestele-yhteyskatkos-data-visualisointia-varten asetukset-kayttoon ryhma-avain jarjestys-avain)]
    jarjestelty-data))

(defn haettavat-tiedot-analyyseja-varten
  [haettavat-analyysit ping-erikseen?]
  (let [tehtava-analyysi? #(contains? haettavat-analyysit %)
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
                                              (tehtava-analyysi? :monellako-kayttajalla) (conj :kayttaja)
                                              ping-erikseen? (conj :palvelut))]
    (zipmap (map bool-keyword haettavat-tiedot-lokituksista)
            (repeat (count haettavat-tiedot-lokituksista) true))))

(defn hae-yhteyskatkosanalyysi
  [{:keys [analysointimetodi haettavat-analyysit hakuasetukset] :as analyysihaku} data-csvna]
  (let [graylogista-haetut-lokitukset (hae-yhteyskatkos-data data-csvna)
        ping-erikseen? (when (and (not (get-in hakuasetukset [:naytettavat-ryhmat :muut]))
                                  (contains? haettavat-analyysit :selain-sammutettu-katkoksen-aikana))
                          true)
        haettavat-tiedot-lokituksista (haettavat-tiedot-analyyseja-varten haettavat-analyysit ping-erikseen?)
        yhteyskatkokset-mappina (map #(muunnokset/yhteyskatkokset-lokitus-string->yhteyskatkokset-map % haettavat-tiedot-lokituksista)
                                      graylogista-haetut-lokitukset)
        asetukset-kayttoon (keep #(asetuksien-kytkenta % (assoc hakuasetukset :ping-erikseen? ping-erikseen?) :analyysi)
                                 yhteyskatkokset-mappina)
        analyysit (analyysit/analyysit-yhteyskatkoksista asetukset-kayttoon analyysihaku)
        pingin-poisto-fn #(into {} (map (fn [[analyysi tulos]]
                                          [analyysi (cond
                                                      (map? tulos) (dissoc tulos "ping")
                                                      :else tulos)])
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
                            (hae-yhteyskatkosten-data-visualisointia-varten hakuasetukset (st/trim (:polku data-csvna)) false)))
    (http/julkaise-palvelu (:http-palvelin this)
                           :graylog-hae-yhteyskatkosryhma
                           (fn [_ hakuasetukset]
                            (hae-yhteyskatkosten-data-visualisointia-varten hakuasetukset (st/trim (:polku data-csvna)) true)))
    (http/julkaise-palvelu (:http-palvelin this)
                           :graylog-hae-analyysi
                           (fn [_ analyysihaku]
                            (hae-yhteyskatkosanalyysi analyysihaku (st/trim (:polku data-csvna)))))
    this)

  (stop [this]
    (http/poista-palvelut (:http-palvelin this) :graylog-hae-yhteyskatkokset :graylog-hae-yhteyskatkosryhma :graylog-hae-analyysi)
    this))
