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
  (filter #(p/janan-id? % id) taulukko))

(defn janan-osa
  "Palauttaa janan elementi(t) joiden id vastaa annettua"
  [jana id]
  (filter #(p/osan-id? % id) (p/janan-osat jana)))

(defn janan-index
  "Palauttaa janan indeksin taulukossa"
  [taulukko jana]
  (ominaisuus-predikaatilla taulukko (fn [index _] index) #(= (p/janan-id %1) (p/janan-id jana))))

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

(defn generoi-pohjadata
  ([f oleelliset-datat olemassa-olevat] (generoi-pohjadata f oleelliset-datat olemassa-olevat nil))
  ([f oleelliset-datat olemassa-olevat default-arvoja]
   (for [data oleelliset-datat]
     (if-let [loytynyt-olemassa-oleva-data (some (fn [olemassa-oleva-data]
                                                   (when (every? true?
                                                                 (vals (reduce-kv (fn [m k v]
                                                                                    (if-let [loytynyt-arvo (m k)]
                                                                                      (assoc m k (= loytynyt-arvo v))
                                                                                      m))
                                                                                  data olemassa-oleva-data)))
                                                     olemassa-oleva-data))
                                                 olemassa-olevat)]
       loytynyt-olemassa-oleva-data
       (f (merge data default-arvoja))))))

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