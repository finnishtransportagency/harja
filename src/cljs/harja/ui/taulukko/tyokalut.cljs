(ns harja.ui.taulukko.tyokalut
  (:require [harja.loki :as loki]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.jana :as jana]))

(defn numero-re
  ([] (numero-re {}))
  ([{kokonaisosan-maara :kokonaisosan-maara desimaalien-maara :desimaalien-maara
     positiivinen? :positiivinen? kokonaisluku? :kokonaisluku?
     :or {kokonaisosan-maara 10 desimaalien-maara 10 positiivinen? false kokonaisluku? false}}]
   (str (when-not positiivinen? "-?")
        "\\d{1," kokonaisosan-maara "}"
        (when-not kokonaisluku? (str "((\\.|,)\\d{0," desimaalien-maara "})?")))))

(defn positiivinen-numero-re
  ([] (positiivinen-numero-re {}))
  ([asetukset]
    (numero-re (assoc asetukset :positiivinen? true))))

(defmulti lisaa-kaytos
          (fn [kaytos _]
            (cond
              (map? kaytos) (first (keys kaytos))
              (keyword? kaytos) kaytos
              :else nil)))

(defmethod lisaa-kaytos :eventin-arvo
  [_ toiminto]
  (comp toiminto
        (fn [event]
          (.. event -target -value))))

(defmethod lisaa-kaytos :positiivinen-numero
  [_ toiminto]
  (comp toiminto
        (fn [arvo]
          (let [positiivinen-arvo? (re-matches (re-pattern (positiivinen-numero-re)) arvo)]
            (when (or (= "" arvo) positiivinen-arvo?)
              arvo)))))

(defmethod lisaa-kaytos :default
  [kaytos toiminto]
  (loki/warn "KAYTOSTÄ: " kaytos " EI OLE MÄÄRITETTY!")
  toiminto)


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

(defn muodosta-rivi-skeemasta [riviskeemat skeeman-nimi maaritelma]
  (let [{:keys [janan-tyyppi osat janat index] :as riviskeema} (get riviskeemat skeeman-nimi)
        osat-tai-janat (if janat
                         (loop [[[skeeman-nimi nn] & skeemat] (partition 2 janat)
                                maaritelmat (get maaritelma index)
                                janat []]
                           (if (nil? skeeman-nimi)
                             janat
                             (let [uudet-janat (map (fn [maaritelma]
                                                      (muodosta-rivi-skeemasta riviskeemat skeeman-nimi maaritelma))
                                                    (if (= "*" nn)
                                                      maaritelmat
                                                      (take nn maaritelmat)))]
                               (recur skeemat
                                      (if (= "*" nn)
                                        []
                                        (drop nn maaritelmat))
                                      (into [] (concat janat uudet-janat))))))
                         (mapv (fn [osan-maaritelma {:keys [osan-tyyppi]}]
                                (apply osan-tyyppi osan-maaritelma))
                              (get maaritelma index)
                              osat))
        tyypin-argumentit (concat (take index maaritelma) [osat-tai-janat] (drop (inc index) maaritelma))]
    (apply janan-tyyppi tyypin-argumentit)))

(defn muodosta-rivit [riviskeemat & args]
  (let [rivi-maaritelmat (partition 2 args)]
    (into []
          (mapcat (fn [[skeeman-nimi maaritelmat]]
                    (mapv #(muodosta-rivi-skeemasta riviskeemat skeeman-nimi %)
                          maaritelmat))
                  rivi-maaritelmat))))