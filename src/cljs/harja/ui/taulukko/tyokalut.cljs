(ns harja.ui.taulukko.tyokalut
  (:require [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.loki :as loki]))


(defmulti arvo
          (fn [taulukon-asia _]
            (type taulukon-asia)))

(defmethod arvo taulukko/Taulukko
  [taulukko avain]
  (let [muuta-avain {:id [:taulukon-id]
                     :lapset [:rivit]
                     :class [:parametrit :class]}]
    (get-in taulukko (muuta-avain avain))))

(defmethod arvo jana/Rivi
  [rivi avain]
  (let [muuta-avain {:id [:janan-id]
                     :lapset [:solut]
                     :class [:luokat]}]
    (get-in rivi (muuta-avain avain))))

(defmethod arvo jana/RiviLapsilla
  [rivi avain]
  (let [muuta-avain {:id [:janan-id]
                     :lapset [:janat]}]
    (get-in rivi (muuta-avain avain))))

(defmethod arvo osa/Teksti
  [osa avain]
  (let [muuta-avain {:arvo [:teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}
        palautettava-arvo (get-in osa (muuta-avain avain))]
    (if (= avain :arvo)
      (try (js/parseInt palautettava-arvo)
           (catch :default e
             palautettava-arvo))
      palautettava-arvo)))

(defmethod arvo osa/Ikoni
  [osa avain]
  (let [muuta-avain {:arvo [:ikoni-ja-teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (get-in osa (muuta-avain avain))))

(defmethod arvo osa/Otsikko
  [osa avain]
  (let [muuta-avain {:arvo [:otsikko]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (get-in osa (muuta-avain avain))))

(defmethod arvo osa/Syote
  [osa avain]
  (let [muuta-avain {:arvo [:parametrit :value]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (get-in osa (muuta-avain avain))))

(defmethod arvo osa/Laajenna
  [osa avain]
  (let [muuta-avain {:arvo [:teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}
        {renderointi :atom muodosta-arvo :muodosta-arvo} (:tilan-seuranta osa)
        palautettava-arvo (if (and renderointi (= avain :arvo))
                            (muodosta-arvo osa @renderointi)
                            (get-in osa (muuta-avain avain)))]
    (if (= avain :arvo)
      (try (js/parseInt palautettava-arvo)
           (catch :default e
             palautettava-arvo))
      palautettava-arvo)))

(defmethod arvo osa/Komponentti
  [osa avain]
  (let [muuta-avain {:arvo [:komponentin-tila]
                     :id [:osan-id]}]
    (get-in osa (muuta-avain avain))))

(defmethod arvo :default
  [osa avain]
  (loki/warn "TAULUKON OSALLE: " osa " AVAIMEN " avain " arvo defmethod OLE MÄÄRITETTY!")
  (loki/warn "OSAN TYYPPI: " (type osa))
  osa)

(defn aseta-asian-arvo [asia avain-arvo muuta-avain]
  (let [asian-avain-arvo (map (fn [[avain arvo]]
                                   [(muuta-avain avain) arvo])
                              (partition 2 avain-arvo))]
    (reduce (fn [asia [polku arvo]]
              (assoc-in asia polku arvo))
            asia asian-avain-arvo)))

(defmulti aseta-arvo
          (fn [taulukon-asia & _]
            (type taulukon-asia)))

(defmethod aseta-arvo taulukko/Taulukko
  [taulukko & avain-arvo]
  (let [muuta-avain {:id [:taulukon-id]
                     :lapset [:skeema-sarake]
                     :class [:parametrit :class]}]
    (aseta-asian-arvo taulukko avain-arvo muuta-avain)))

(defmethod aseta-arvo jana/Rivi
  [rivi & avain-arvo]
  (let [muuta-avain {:id [:janan-id]
                     :lapset [:solut]
                     :class [:luokat]
                     :piillotettu? [:piilotettu?]}]
    (aseta-asian-arvo rivi avain-arvo muuta-avain)))

(defmethod aseta-arvo jana/RiviLapsilla
  [rivi & avain-arvo]
  (let [muuta-avain {:id [:janan-id]
                     :lapset [:janat]}]
    (aseta-asian-arvo rivi avain-arvo muuta-avain)))

(defmethod aseta-arvo osa/Teksti
  [osa & avain-arvo]
  (let [muuta-avain {:arvo [:teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (aseta-asian-arvo osa avain-arvo muuta-avain)))

(defmethod aseta-arvo osa/Ikoni
  [osa & avain-arvo]
  (let [muuta-avain {:arvo [:ikoni-ja-teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (aseta-asian-arvo osa avain-arvo muuta-avain)))

(defmethod aseta-arvo osa/Otsikko
  [osa & avain-arvo]
  (let [muuta-avain {:arvo [:otsikko]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (aseta-asian-arvo osa avain-arvo muuta-avain)))

(defmethod aseta-arvo osa/Syote
  [osa & avain-arvo]
  (let [muuta-avain {:arvo [:parametrit :value]
                     :id [:osan-id]
                     :class [:parametrit :class]
                     :on-change [:toiminnot :on-change]
                     :on-blur [:toiminnot :on-blur]}]
    (aseta-asian-arvo osa avain-arvo muuta-avain)))

(defmethod aseta-arvo osa/Laajenna
  [osa & avain-arvo]
  (let [muuta-avain {:arvo [:teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (aseta-asian-arvo osa avain-arvo muuta-avain)))

(defmethod aseta-arvo osa/Komponentti
  [osa & avain-arvo]
  (let [muuta-avain {:arvo [:komponentin-tila]
                     :id [:osan-id]}]
    (aseta-asian-arvo osa avain-arvo muuta-avain)))

(defmethod aseta-arvo :default
  [taulukon-asia avain & _]
  (loki/warn "TAULUKON ASIALLE: " (str taulukon-asia) " AVAIMEN ASETTAMIS FN EI OLE MÄÄRITETTY!")
  (loki/warn "ASIAN TYYPPI: " (type taulukon-asia))
  taulukon-asia)

(defmulti paivita-arvo
          (fn [taulukon-asia & _]
            (type taulukon-asia)))

(defmethod paivita-arvo taulukko/Taulukko
  [taulukko avain f & args]
  (let [muuta-avain {:id [:taulukon-id]
                     :lapset [:rivit]
                     :class [:parametrit :class]}]
    (apply update-in taulukko (muuta-avain avain) f args)))

(defmethod paivita-arvo jana/Rivi
  [rivi avain f & args]
  (let [muuta-avain {:id [:janan-id]
                     :lapset [:solut]
                     :class [:luokat]}]
    (apply update-in rivi (muuta-avain avain) f args)))

(defmethod paivita-arvo jana/RiviLapsilla
  [rivi avain f & args]
  (let [muuta-avain {:id [:janan-id]
                     :lapset [:janat]}]
    (apply update-in rivi (muuta-avain avain) f args)))

(defmethod paivita-arvo osa/Teksti
  [osa avain f & args]
  (let [muuta-avain {:arvo [:teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (apply update-in osa (muuta-avain avain) f args)))

(defmethod paivita-arvo osa/Ikoni
  [osa avain f & args]
  (let [muuta-avain {:arvo [:ikoni-ja-teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (apply update-in osa (muuta-avain avain) f args)))

(defmethod paivita-arvo osa/Otsikko
  [osa avain f & args]
  (let [muuta-avain {:arvo [:otsikko]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (apply update-in osa (muuta-avain avain) f args)))

(defmethod paivita-arvo osa/Syote
  [osa avain f & args]
  (let [muuta-avain {:arvo [:parametrit :value]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (apply update-in osa (muuta-avain avain) f args)))

(defmethod paivita-arvo osa/Laajenna
  [osa avain f & args]
  (let [muuta-avain {:arvo [:teksti]
                     :id [:osan-id]
                     :class [:parametrit :class]}]
    (apply update-in osa (muuta-avain avain) f args)))

(defmethod paivita-arvo osa/Komponentti
  [osa avain f & args]
  (let [muuta-avain {:arvo [:komponentin-tila]
                     :id [:osan-id]}]
    (apply update-in osa (muuta-avain avain) f args)))

(defmethod paivita-arvo :default
  [taulukon-asia avain & _]
  (loki/warn "TAULUKON ASIALLE: " (str taulukon-asia) " AVAIMEN PAIVITTÄMIS FN EI OLE MÄÄRITETTY!")
  (loki/warn (type taulukon-asia))
  taulukon-asia)


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
  (loki/log "sain janan " (p/janan-id jana) " ja oli tämmänenki " jana)
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
  (-> riviryhma (arvo :lapset) (nth rivin-index) (arvo :lapset) (nth otsikon-index)))