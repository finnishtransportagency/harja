(ns harja.ui.taulukko.taulukko
  "Saman tyylinen kuin grid, mutta taulukolle annetaan näytettävä tila. Lisäksi ei määritellä miltä
   joku rivi näyttää vaan jätetään se käyttäjän vastuulle"
  (:require [harja.ui.taulukko.protokollat :as p]
            [clojure.spec.alpha :as s]))


{:paa {:janan-tyyppi harja.ui.taulukko.jana/->Rivi
       :osat [{:osan-tyyppi harja.ui.taulukko.osa/->Teksti}]}}

(defn taulukon-jana-validi?
  [jana janan-skeema]
  (and (not (nil? janan-skeema))
       (= (type jana) (:janan-tyyppi janan-skeema))
       (every? true?
               (map (fn [osan-skeema osa]
                      (= (type osa) (:osan-tyyppi osan-skeema)))
                    (:osat janan-skeema)
                    (p/janan-osat jana)))))

(defn rivin-index
  "Palauttaa janan indeksin taulukossa"
  [rivit rivi]
  (first (keep-indexed #(when (= (p/janan-id %2) (p/janan-id rivi))
                          %1)
                       rivit)))

(defn solun-polku-taulukossa
  [rivit solu]
  (first (keep-indexed (fn [rivin-index rivi]
                         (when-let [solun-polku (p/osan-polku rivi solu)]
                           (into []
                                 (cons rivin-index solun-polku))))
                       rivit)))

(defrecord Taulukko [taulukon-id skeema-rivi skeema-sarake rivit parametrit]
  p/Taulukko
  (piirra-taulukko [this]
    (let [luokat (-> this :parametrit :class)]
      [:div.taulukko {:data-cy "taulukko"
                      :class (apply str (interpose " " luokat))}
       (for [rivi (:rivit this)]
         (with-meta [p/piirra-jana rivi]
                    {:key (p/janan-id rivi)}))]))
  (otsikon-index [this otsikko]
    (first (keep-indexed (fn [index ss]
                           (when (= (:otsikko ss) otsikko)
                             index))
                         (:skeema-sarake this))))
  (rivin-skeema [this rivi]
    (some (fn [[skeeman-nimi rivin-skeema]]
            (when (taulukon-jana-validi? rivi rivin-skeema)
              skeeman-nimi))
          (:skeema-rivi this)))
  (paivita-taulukko! [this a1 a2 a3 a4 a5 a6 a7]
    (let [paivita-taulukkko! (:taulukon-paivitys-fn! parametrit)
          args (remove #(= ::tyhja %) [a1 a2 a3 a4 a5 a6 a7])]
      (assert paivita-taulukkko! "Taulukolle ei ole määritetty :taulukon-paivitys-fn! parametria")
      ;taulukon-paivitys-fn ottaa taulukon uuden arvon ja päivittää tilan
      (apply paivita-taulukkko! this args)))
  (paivita-taulukko! [this a1 a2 a3 a4 a5 a6]
    (p/paivita-taulukko! this a1 a2 a3 a4 a5 a6 ::tyhja))
  (paivita-taulukko! [this a1 a2 a3 a4 a5]
    (p/paivita-taulukko! this a1 a2 a3 a4 a5 ::tyhja ::tyhja))
  (paivita-taulukko! [this a1 a2 a3 a4]
    (p/paivita-taulukko! this a1 a2 a3 a4 ::tyhja ::tyhja ::tyhja))
  (paivita-taulukko! [this a1 a2 a3]
    (p/paivita-taulukko! this a1 a2 a3 ::tyhja ::tyhja ::tyhja ::tyhja))
  (paivita-taulukko! [this a1 a2]
    (p/paivita-taulukko! this a1 a2 ::tyhja ::tyhja ::tyhja ::tyhja ::tyhja))
  (paivita-taulukko! [this a1]
    (p/paivita-taulukko! this a1 ::tyhja ::tyhja ::tyhja ::tyhja ::tyhja ::tyhja))
  (paivita-taulukko! [this]
    (p/paivita-taulukko! this ::tyhja ::tyhja ::tyhja ::tyhja ::tyhja ::tyhja ::tyhja))
  (paivita-rivi! [this paivitetty-rivi]
    (let [paivita-taulukkko! (:taulukon-paivitys-fn! parametrit)
          rivin-index (rivin-index (:rivit this) paivitetty-rivi)
          paivitetty-taulukko (assoc-in this [:rivit rivin-index] paivitetty-rivi)]
      (assert paivita-taulukkko! "Taulukolle ei ole määritetty :taulukon-paivitys-fn! parametria")
      ;taulukon-paivitys-fn ottaa taulukon uuden arvon ja päivittää tilan
      (paivita-taulukkko! paivitetty-taulukko)))
  (paivita-solu! [this paivitetty-solu]
    (let [paivita-taulukkko! (:taulukon-paivitys-fn! parametrit)
          solun-polku (solun-polku-taulukossa (:rivit this) paivitetty-solu)
          paivitetty-taulukko (assoc-in this solun-polku paivitetty-solu)]
      (assert paivita-taulukkko! "Taulukolle ei ole määritetty :taulukon-paivitys-fn! parametria")
      ;taulukon-paivitys-fn ottaa taulukon uuden arvon ja päivittää tilan
      (paivita-taulukkko! paivitetty-taulukko))))

(defn taulukko
  ([tila] [taulukko tila nil])
  ([tila luokat]
   {:pre [(sequential? tila)
          (every? #(satisfies? p/Jana %) tila)]}
   [:div.taulukko {:data-cy "taulukko"
                   :class (apply str (interpose " " luokat))}
    (for [jana tila]
      (with-meta [p/piirra-jana jana]
                 {:key (:janan-id jana)}))]))

;; SPECS
(s/def ::taulukon-id any?)
(s/def ::skeema map?)
(s/def ::jana #(satisfies? p/Jana %))
(s/def ::taulukko #(satisfies? p/Taulukko %))
(s/def ::tila (s/coll-of ::jana))
(s/def ::parametrit any?)

(s/fdef ->Taulukko
        :args (s/cat :id ::taulukon-id :skeema ::skeema :tila ::tila :parametrit ::parametrit)
        :ret ::taulukko)