(ns harja.palvelin.tyokalut.graylog-muunnokset
  (:require [clojure.string :as st]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]))

;;;;;;;;;;;;;;;;;;;; Mapin muokkaamis fn ;;;;;;;;;;;;;
(defn map-vec->vec-map
  [mappi-vektoreita]
  (apply mapv (fn [& arvot]
                (zipmap (keys mappi-vektoreita) arvot))
              (keep #(when-not (empty? %) %) (vals mappi-vektoreita))))

(defn yhdista-avaimet-kun
  "esim. (yhdista-avaimet-kun + :a [:b] {:a 3 :b :foo :c 2} {:a 2 :b :foo} {:a 4 :d 3 :b :bar} {:a 6 :b :bar})
   palauttaa [{:b :foo, :a 5} {:b :bar, :a 10}]"
  [funktio yhdistettava-avain sama-avain & mapit]
  (let [yhdistamis-fn #(zipmap (conj sama-avain yhdistettava-avain)
                               (conj (mapv (fn [avain]
                                             (avain %))
                                           sama-avain)
                                     (yhdistettava-avain %)))
        samat-avaimet? (fn [map-1 map-2] (= (map #(% map-1) sama-avain) (map #(% map-2) sama-avain)))]
    (reduce (fn [yhdistetyt-mapit kasiteltava-mappi]
              (if (and (contains? kasiteltava-mappi yhdistettava-avain)
                       (every? identity (map #(contains? kasiteltava-mappi %) sama-avain)))
                (loop [[paa & hanta] yhdistetyt-mapit
                        lopputulos []
                        loytyi? false]
                  (if (and (not loytyi?) (nil? paa))
                    (conj lopputulos (yhdistamis-fn kasiteltava-mappi))
                    (if loytyi?
                      (if paa
                        (concat (conj lopputulos paa) hanta)
                        lopputulos)
                      (let [loytyi-tasta? (samat-avaimet? paa kasiteltava-mappi)
                            paivitetty-paa (if loytyi-tasta?
                                            (merge (yhdistamis-fn kasiteltava-mappi)
                                                   {yhdistettava-avain (funktio (yhdistettava-avain paa) (yhdistettava-avain kasiteltava-mappi))})
                                            ; {yhdistettava-avain (funktio (yhdistettava-avain paa) (yhdistettava-avain kasiteltava-mappi))
                                            ;  sama-avain (sama-avain paa)}
                                            paa)]
                        (recur hanta
                               (conj lopputulos paivitetty-paa)
                               (or loytyi-tasta? loytyi?))))))
                yhdistetyt-mapit))
            '() mapit)))

(defn sort-by-vec
  [paa-vektori & vektorit]
  (let [jarjestelty-vektori (vec (sort paa-vektori))
        jarjestely-indeksit (map #(.indexOf paa-vektori %) jarjestelty-vektori)
        vektorit-jarjestelty (map (fn [vektori]
                                    (loop [[indeksi & indeksi-hanta] jarjestely-indeksit
                                           sijoitettava-arvo (get vektori indeksi)
                                           kierros-n 0
                                           jarjestetty vektori]
                                      (if (nil? indeksi)
                                        jarjestetty
                                        (recur indeksi-hanta
                                               (get vektori (first indeksi-hanta))
                                               (inc kierros-n)
                                               (assoc jarjestetty kierros-n sijoitettava-arvo)))))
                                  vektorit)]
    (conj vektorit-jarjestelty jarjestelty-vektori)))

;;;;;;;;;;;;;;;;;;;; Yhteyskatkoksien parsimiseen graylogin stringeistä liittyvät funktiot ;;;;;;;;;;;;;
(defn ilman-skandeja
  "Korjaa ääkköset"
  [teksti]
  (st/replace teksti #"�_" "ä"))

(defn re-escape
  [teksti]
  (st/escape teksti {\. "\\." \\ "\\\\" \+ "\\+"
                     \* "\\*" \? "\\?" \^ "\\^"
                     \$ "\\$" \[ "\\[" \] "\\]"
                     \( "\\(" \) "\\)" \{ "\\{"
                     \} "\\}" \| "\\|" \/ "\\/"
                     \space "\\s"}))

(defn etsi-arvot-valilta
  "Tällä voi hakea stringin 'teksti' sisältä substringin alku- ja loppu-tekstin
   väliltä. Jos 'kaikki' arvo on true, palauttaa kaikki löydetyt arvot vektorissa
   muuten ensimmäisen osuman.

   esim. (etsi-arvot-valilta \"([:foo a] [:bar b])\" \"[\" \"]\" true)
          palauttaa [\":foo a\" \":bar b\"]"
   ([teksti alku-teksti loppu-teksti]
    (etsi-arvot-valilta teksti alku-teksti loppu-teksti false))
   ([teksti alku-teksti loppu-teksti kaikki?]
    (let [re (re-pattern (str (re-escape alku-teksti) "([^"
                              (re-escape loppu-teksti) "]+)"))
          teksti (ilman-skandeja teksti)]
      (if kaikki?
        (mapv second (re-seq re teksti))
        (second (re-find re teksti))))))

(defn tekstin-formatointi
  [teksti]
  (cond
    (and (re-find #"raportoi yhteyskatkoksista palveluissa" teksti)
         (re-find #"\]\]\]\)\]\]$" teksti)) :html
    (re-find #":td|:tr|:table|:div|:pre|:valign" teksti) :html-rikkinainen
    (and (re-find #"raportoi yhteyskatkoksista palveluissa:," teksti)
         (re-find #"\}\]\}$" teksti)) :slack
    (re-find #":title|:value" teksti) :slack-rikkinainen
    :else :joku-rikkinainen))

(defn yhteyskatkokset-formatoinnille
  "Ottaa lokitetun yhteyskatkokstekstin ja palauttaa siitä eritellyt tiedot mappina kayttajan antamien asetuksien perusteella.
   Asetusena voi antaa halutut tiedot mappina, jossa avaimina voi olla
     :pvm? Halutaanko päivämäärä? (java Date)
     :kello? Halutaanko kellon aika? (string)
     :kayttaja? Halutaanko kayttaja? (string)
     :palvelut? Halutaanko palvelut? (string)
     :ensimmaiset-katkokset? Halutaanko ensimmaiset-katkokset? (java Date)
     :viimeiset-katkokset? Halutaanko viimeiset-katkokset? (java Date)
   Palautettu mappi on muotoa {:pvm Date
                               :kello string
                               :kayttaja string
                               :yhteyskatkokset [{:palvelu string :katkokset integer :ensimmainen-katkos Date :viimeinen-katkoks Date} ...]}"
  [loki-teksti aika {:keys [pvm? kello? kayttaja? palvelut? ensimmaiset-katkokset? viimeiset-katkokset? :as haettavat-tiedot-lokituksista]}
   {:keys [pvm kello kayttaja palvelut katkokset ensimmaiset-katkokset viimeiset-katkokset :as tekstit]}]
  (let [string->Date #(when (string? %)
                        (pvm/dateksi %))
        yhteyskatkoksien-metadata  (cond-> {}
                                           pvm? (assoc :pvm (string->Date (apply etsi-arvot-valilta aika pvm)))
                                           kello? (assoc :kello (apply etsi-arvot-valilta aika kello))
                                           kayttaja? (assoc :kayttaja (apply etsi-arvot-valilta loki-teksti kayttaja)))
        lokituksessa-ilmenevat-palvelut (when palvelut?
                                          (apply etsi-arvot-valilta loki-teksti (conj palvelut true)))
        lokituksessa-ilmenevat-katkokset (map (fn [x]
                                                (Integer. x))
                                              (apply etsi-arvot-valilta loki-teksti (conj katkokset true)))
        lokituksessa-ilmenevat-ensimmaiset-katkokset (when ensimmaiset-katkokset?
                                                      (map #(string->Date %)
                                                           (apply etsi-arvot-valilta loki-teksti (conj ensimmaiset-katkokset true))))
        lokituksessa-ilmenevat-viimeiset-katkokset (when viimeiset-katkokset?
                                                    (map #(string->Date %)
                                                         (apply etsi-arvot-valilta loki-teksti (conj viimeiset-katkokset true))))
        yhteyskatkokset (map-vec->vec-map
                          (cond-> {}
                                  palvelut? (assoc :palvelu lokituksessa-ilmenevat-palvelut)
                                  true (assoc :katkokset lokituksessa-ilmenevat-katkokset)
                                  ensimmaiset-katkokset? (assoc :ensimmainen-katkos lokituksessa-ilmenevat-ensimmaiset-katkokset)
                                  viimeiset-katkokset? (assoc :viimeinen-katkos lokituksessa-ilmenevat-viimeiset-katkokset)))]
      (merge yhteyskatkoksien-metadata {:yhteyskatkokset yhteyskatkokset})))

;;;;;;;;;;;;;;;;;;;; Parsitun datan muokkaamiseen liittyvät funktiot ;;;;;;;;;;;;;
(defn tekstin-hyvaksymis-fn
  [optio? re]
  (when (not optio?)
   (fn [teksti]
      (if (and (string? teksti) (re-find re teksti)) nil teksti))))

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

(defn katkoksien-poisto-fn
  [min-katkokset]
  (fn [katkokset]
    (when (and (not (nil? katkokset))
               (> katkokset min-katkokset))
      katkokset)))

(defmulti asetukset-kayttoon
  (fn [tyyppi yhteyskatkos-data asetukset]
    tyyppi))

(defmethod asetukset-kayttoon :diagrammi
  [_ mappi {:keys [ryhma-avain jarjestys-avain naytettavat-ryhmat min-katkokset]}]
  (let [min-katkokset (or min-katkokset 0)
        palvelujen-poisto-fn (palvelujen-poisto-fn naytettavat-ryhmat)
        katkoksien-poisto-fn (katkoksien-poisto-fn min-katkokset)
        palvelu-asetukset-kayttoon (when (palvelujen-poisto-fn (:palvelu mappi))
                                      mappi)
        katkos-asetukset-kayttoon (when (katkoksien-poisto-fn (:katkokset palvelu-asetukset-kayttoon))
                                    palvelu-asetukset-kayttoon)]
    katkos-asetukset-kayttoon))

(defmethod asetukset-kayttoon :analyysi
  [_ onnistunut-mappaus {:keys [naytettavat-ryhmat ping-erikseen?]}]
  (let [palvelujen-poisto-fn (palvelujen-poisto-fn naytettavat-ryhmat)
        katkostiedot (keep (fn [{:keys [palvelu] :as mappi}]
                              (if (and (= "ping" palvelu) ping-erikseen?)
                                mappi
                                (when (palvelujen-poisto-fn palvelu)
                                  mappi)))
                           (:yhteyskatkokset onnistunut-mappaus))]
     (if (empty? katkostiedot)
       nil
       (assoc onnistunut-mappaus :yhteyskatkokset (vec katkostiedot)))))


(defn yhteyskatkokset-ryhmittain
  [{yhteyskatkokset :yhteyskatkokset :as data}]
  (let [ryhmietty-yhteyskatkos (mapv #(if (contains? % :katkokset)
                                        (assoc % :katkokset 1)
                                        %)
                                     yhteyskatkokset)]
    (assoc data :yhteyskatkokset ryhmietty-yhteyskatkos)))

;;;;;;;;;;;;;;;;;;;; Parsitun datan muokkaamisen toiseen muotoon liittyvät funktiot ;;;;;;;;;;;;;
(defn avain-monikko->yksikko
  [avain]
  (let [avain-mappaukset {:pvm :pvm
                          :kello :kello
                          :kayttaja :kayttaja
                          :palvelut :palvelu
                          :katkokset :katkokoset
                          :ensimmaiset-katkokset :ensimmainen-katkos
                          :viimeiset-katkokset :viimeinen-katkos}]
    (avain avain-mappaukset)))
(defmulti jarjestele-yhteyskatkos-data-visualisointia-varten
  (fn [tyyppi yhteyskatkos-data ryhma-avain jarjestys-avain]
    tyyppi))

(defmethod jarjestele-yhteyskatkos-data-visualisointia-varten :line-plot
  [_ yhteyskatkos-data ryhma-avain jarjestys-avain]
  (let [ryhma-avain (avain-monikko->yksikko ryhma-avain)
        jarjestys-avain (avain-monikko->yksikko jarjestys-avain)
        yhteyskatkokset-oikeassa-muodossa (reduce (fn [jarjestelty-data uusi-map]
                                                    (let [uusi-map-loytyi? (some #(= (:label %) (ryhma-avain uusi-map)) jarjestelty-data)]
                                                      (if uusi-map-loytyi?
                                                        (map (fn [kaytava-map]
                                                              (if (= (:label kaytava-map) (ryhma-avain uusi-map))
                                                                (let [x-updated (update kaytava-map :x #(conj % (jarjestys-avain uusi-map)))]
                                                                  (update x-updated :y #(conj % (:katkokset uusi-map))))
                                                                kaytava-map))
                                                             jarjestelty-data)
                                                        (conj jarjestelty-data {:x [(jarjestys-avain uusi-map)]
                                                                                :y [(:katkokset uusi-map)]
                                                                                :label (ryhma-avain uusi-map)}))))
                                                  [] yhteyskatkos-data)]
    (mapv #(let [sortatut-kentat (sort-by-vec (:x %) (:y %))]
             (assoc % :x (first sortatut-kentat)
                      :y (second sortatut-kentat)))
          yhteyskatkokset-oikeassa-muodossa)))

(defmethod jarjestele-yhteyskatkos-data-visualisointia-varten :bars
  [_ yhteyskatkos-data ryhma-avain jarjestys-avain]
  (let [ryhma-avain (avain-monikko->yksikko ryhma-avain)
        jarjestys-avain (avain-monikko->yksikko jarjestys-avain)
        ryhmat-avattuna (map (fn [ryhma-jarjestys-map]
                                {:jarjestys-avain (jarjestys-avain ryhma-jarjestys-map)
                                 :ryhma-avain (ryhma-avain ryhma-jarjestys-map)
                                 :arvo-avain (:katkokset ryhma-jarjestys-map)})
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
        loki-teksti (st/replace (last lokitus) #"\"" "")
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
      :slack (yhteyskatkokset-formatoinnille loki-teksti aika haettavat-tiedot-lokituksista
                                             {:pvm ["" ","]
                                              :kello ["T" "."]
                                              :kayttaja ["{:text Käyttäjä " " "]
                                              :palvelut ["{:title :" ", "]
                                              :katkokset [", :value Katkoksia " " "]
                                              :ensimmaiset-katkokset ["ensimmäinen: " "(slack-n)"]
                                              :viimeiset-katkokset ["viimeinen: " "}"]})
      :slack-rikkinainen {:rikkinainen (yhteyskatkokset-formatoinnille loki-teksti aika haettavat-tiedot-lokituksista
                                             {:pvm ["" ","]
                                              :kello ["T" "."]
                                              :kayttaja ["{:text Käyttäjä " " "]
                                              :palvelut ["{:title :" ", "]
                                              :katkokset [", :value Katkoksia " " "]
                                              :ensimmaiset-katkokset ["ensimmäinen: " "(slack-n)"]
                                              :viimeiset-katkokset ["viimeinen: " "}"]})}
      :joku-rikkinainen {:rikkinainen "foo"})))
