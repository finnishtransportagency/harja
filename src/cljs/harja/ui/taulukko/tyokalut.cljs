(ns harja.ui.taulukko.tyokalut
  (:require [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]))


(defn ominaisuus-predikaatilla [janat-tai-osat ominaisuus predikaatti]
  (first (keep-indexed (fn [index jana-tai-osa]
                         (when (predikaatti jana-tai-osa)
                           (ominaisuus index jana-tai-osa)))
                       janat-tai-osat)))

(defn jana
  "Palauttaa jana(t) joiden id vastaa annettua"
  [taulukko id]
  (into [] (filter #(p/janan-id? % id) (:rivit taulukko))))

(defn janan-osa
  "Palauttaa janan elementi(t) joiden id vastaa annettua"
  [jana id]
  (filter #(p/osan-id? % id) (p/janan-osat jana)))

(defn janan-index
  "Palauttaa janan indeksin taulukossa"
  [taulukko jana]
  (ominaisuus-predikaatilla (:rivit taulukko) (fn [index _] index) #(= (p/janan-id %1) (p/janan-id jana))))

(defn osan-polku-taulukossa
  [taulukko osa]
  (ominaisuus-predikaatilla taulukko
                            (fn [index jana]
                              (into []
                                    (cons index (p/osan-polku jana osa))))
                            (fn [jana] (p/osan-polku jana osa))))

(defn rivin-vanhempi [rivit lapsen-id]
  (ominaisuus-predikaatilla rivit
                            (fn [_ rivi]
                              rivi)
                            (fn [rivi]
                              (when (= (type rivi) jana/RiviLapsilla)
                                (some #(p/janan-id? % lapsen-id)
                                      (p/janan-osat rivi))))))

(defn- generoi-pohjadata-f
  [olemassa-olevat default-arvoja oleellinen-data]
  (let [oleellisten-meta (meta oleellinen-data)
        loytyi-arvo (gensym "loytyi")]
    (if-let [loytynyt-olemassa-oleva-data (some (fn [olemassa-oleva-data]
                                                  (when (every? #(= % loytyi-arvo)
                                                                (vals (reduce-kv (fn [m k v]
                                                                                   (if-let [oleellinen-arvo (m k)]
                                                                                     (assoc m k (when (= oleellinen-arvo v) loytyi-arvo))
                                                                                     m))
                                                                                 oleellinen-data olemassa-oleva-data)))
                                                    olemassa-oleva-data))
                                                olemassa-olevat)]
      [loytynyt-olemassa-oleva-data oleellisten-meta true]
      [(merge oleellinen-data default-arvoja) oleellisten-meta false])))

(defn generoi-pohjadata
  ([olemassa-olevat] (generoi-pohjadata olemassa-olevat nil))
  ([olemassa-olevat default-arvoja]
   (map (partial generoi-pohjadata-f olemassa-olevat default-arvoja)))
  ([olemassa-olevat default-arvoja oleelliset-datat]
   (eduction (map (partial generoi-pohjadata-f olemassa-olevat default-arvoja))
             (map first)
             oleelliset-datat)))

(defn mapv-indexed [f coll]
  (into []
        (map-indexed f coll)))

(defn map-range
  ([alku f coll] (map-range alku (count coll) f coll))
  ([alku loppu f coll]
   (concat
     (take alku coll)
     (map f
          (->> coll (drop alku) (take (- loppu alku))))
     (drop loppu coll))))

(defn mapv-range
  ([alku f coll] (into [] (map-range alku f coll)))
  ([alku loppu f coll]
   (into [] (map-range alku loppu f coll))))

(defn get-in-riviryhma
  [riviryhma [rivin-index otsikon-index]]
  {:pre [(= (type riviryhma) jana/RiviLapsilla)]}
  (-> riviryhma (p/arvo :lapset) (nth rivin-index) (p/arvo :lapset) (nth otsikon-index)))

(defn paivita-riviryhman-lapsirivet [riviryhma f & args]
  (p/paivita-arvo riviryhma :lapset
                  (fn [rivit]
                    (mapv-range 1
                                (fn [rivi]
                                  (apply f rivi args))
                                rivit))))
(defn paivita-riviryhman-paarivi [riviryhma f & args]
  (p/paivita-arvo riviryhma :lapset
                  (fn [rivit]
                    (mapv-range 0 1
                                (fn [rivi]
                                  (apply f rivi args))
                                rivit))))

(defn rivin-arvot-otsikoilla
  [taulukko rivi & otsikot]
  (let [rivin-arvot (map #(p/arvo % :arvo) (p/arvo rivi :lapset))]
    (mapv (fn [otsikko]
            (nth rivin-arvot
                 (p/otsikon-index taulukko otsikko)))
          otsikot)))

(defn hae-asia-taulukosta
  [taulukko polku]
  (loop [asia taulukko
         [polun-osa & polku] polku]
    (if (nil? polun-osa)
      asia
      (let [asiat (p/arvo asia :lapset)
            asia (cond
                   (fn? polun-osa) (polun-osa asiat)
                   (string? polun-osa) (nth asiat (p/otsikon-index taulukko polun-osa))
                   (integer? polun-osa) (nth asiat polun-osa)
                   :else nil)]
        (recur (if (nil? asia)
                 nil
                 asia)
               polku)))))

(defn paivita-asia-taulukossa-predicate
  [taulukko index asia polun-osa]
  (cond
    (integer? polun-osa) (= index polun-osa)
    (keyword? polun-osa) (= polun-osa (p/rivin-skeema taulukko asia))
    (string? polun-osa) (= index (p/otsikon-index taulukko polun-osa))))

(defn paivita-asiat-taulukossa
  ([taulukko polku f]
   (let [taulukon-rivit (p/arvo taulukko :lapset)]
     (p/aseta-arvo taulukko :lapset
                   (paivita-asiat-taulukossa taulukko taulukon-rivit polku f))))
  ([taulukko kierroksen-asiat polku f]
   (if-not (empty? (rest polku))
     (mapv-indexed (fn [index asia]
                     (if (paivita-asia-taulukossa-predicate taulukko index asia (first polku))
                       (p/aseta-arvo asia :lapset
                                     (paivita-asiat-taulukossa taulukko
                                                               (p/arvo asia :lapset)
                                                               (into [] (rest polku))
                                                               f))
                       asia))
                   kierroksen-asiat)
     (mapv-indexed (fn [index asia]
                     (if (paivita-asia-taulukossa-predicate taulukko index asia (first polku))
                       (let [asian-polku-taulukossa (apply concat (p/osan-polku-taulukossa taulukko asia))]
                         (f taulukko (reduce (fn [polut polun-osa]
                                               (conj polut
                                                     (conj (last polut) polun-osa)))
                                             [[(first asian-polku-taulukossa)]] (rest asian-polku-taulukossa))))
                       asia))
                   kierroksen-asiat))))

(defn osan-sisar [taulukko osa sisaren-tunniste]
  (let [osan-rivi (get-in taulukko
                          (into [] (apply concat
                                          (butlast (p/osan-polku-taulukossa taulukko osa)))))]
    (cond
      (integer? sisaren-tunniste) (nth (p/arvo osan-rivi :lapset) sisaren-tunniste)
      (string? sisaren-tunniste) (nth (p/arvo osan-rivi :lapset) (p/otsikon-index taulukko sisaren-tunniste)))))

(defn rivi->map
  "Muuttaa rivin mapiksi. Rivin lapset oletetaan olevan p/Osa. Mapin avaimina on joko solun id tai annetut avaimet ja
   arvona on solun arvo"
  ([rivi] (rivi->map rivi nil))
  ([rivi avaimet]
   {:pre [(satisfies? p/Jana rivi)
          (or (nil? avaimet)
              (vector? avaimet))]
    :post [(or (map? %)
               (nil? %))]}
   (let [lapset (p/arvo rivi :lapset)]
     ;; Jos joku lapsista ei ole osa, palautetaan nil
     (when (every? #(satisfies? p/Osa %) lapset)
       (let [avaimet (or avaimet (map #(p/osan-id %) lapset))]
         (into {}
               (map (fn [avain osa]
                      [avain (p/arvo osa :arvo)])
                    avaimet
                    lapset)))))))

(defn rivikontti->vector
  "Muuttaa rivejä sisätävän rivin vektoriksi mappeja."
  [taulukko rivikontti otsikot riviskeemat]
  (loop [[rivi & rivit] (p/arvo rivikontti :lapset)
         data []]
    (if (nil? rivi)
      data
      (let [rividata (rivi->map rivi otsikot)]
        (recur rivit
               (if (nil? rividata)
                 (into []
                       (concat
                         data
                         (rivikontti->vector taulukko rivi otsikot riviskeemat)))
                 (if (or (nil? riviskeemat)
                         (some (fn [skeema]
                                 (= skeema (p/rivin-skeema taulukko rivi)))
                               riviskeemat))
                   (conj data rividata)
                   data)))))))

(defn taulukko->data
  "Muuttaa taulukon vektoriksi mappeja, joiden avaimina on otsikko-skeema ja arvoina solujen arvot. Voidaan myös antaa
   setti rivien skeemoja, jolloinka vain ne otetaan mukaan."
  ([taulukko] (taulukko->data taulukko nil))
  ([taulukko riviskeemat]
   {:pre [(or (nil? riviskeemat)
              (set? riviskeemat))]}
   (let [otsikot (:skeema-sarake taulukko)]
     (into []
           (rivikontti->vector taulukko taulukko otsikot riviskeemat)))))