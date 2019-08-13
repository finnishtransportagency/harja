(ns harja.ui.taulukko.taulukko
  "Saman tyylinen kuin grid, mutta taulukolle annetaan näytettävä tila. Lisäksi ei määritellä miltä
   joku rivi näyttää vaan jätetään se käyttäjän vastuulle"
  (:require [harja.ui.taulukko.protokollat :as p]
            [clojure.spec.alpha :as s]))


(defonce tyhja-arvo (gensym))

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

(defrecord Taulukko [taulukon-id skeema-rivi skeema-sarake rivit parametrit]
  p/Taulukko
  (piirra-taulukko [this]
    (assert (vector? (:rivit this)) (str "TAULUKON: " taulukon-id " RIVIT EI OLE VEKTORI"))
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
  (osan-polku-taulukossa [this osa]
    (first (keep-indexed (fn [rivin-index rivi]
                           (when-let [solun-polku (p/osan-polku rivi osa)]
                             [[:rivit rivin-index] solun-polku]))
                         (:rivit this))))
  (paivita-taulukko! [this a1 a2 a3 a4 a5 a6 a7]
    (let [paivita-taulukkko! (:taulukon-paivitys-fn! parametrit)
          args (remove #(= tyhja-arvo %) [a1 a2 a3 a4 a5 a6 a7])]
      (assert paivita-taulukkko! "Taulukolle ei ole määritetty :taulukon-paivitys-fn! parametria")
      ;taulukon-paivitys-fn ottaa taulukon uuden arvon ja päivittää tilan
      (apply paivita-taulukkko! this args)))
  (paivita-taulukko! [this a1 a2 a3 a4 a5 a6]
    (p/paivita-taulukko! this a1 a2 a3 a4 a5 a6 tyhja-arvo))
  (paivita-taulukko! [this a1 a2 a3 a4 a5]
    (p/paivita-taulukko! this a1 a2 a3 a4 a5 tyhja-arvo tyhja-arvo))
  (paivita-taulukko! [this a1 a2 a3 a4]
    (p/paivita-taulukko! this a1 a2 a3 a4 tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-taulukko! [this a1 a2 a3]
    (p/paivita-taulukko! this a1 a2 a3 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-taulukko! [this a1 a2]
    (p/paivita-taulukko! this a1 a2 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-taulukko! [this a1]
    (p/paivita-taulukko! this a1 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-taulukko! [this]
    (p/paivita-taulukko! this tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi a1 a2 a3 a4 a5 a6 a7]
    (let [paivita-taulukkko! (:taulukon-paivitys-fn! parametrit)
          rivin-index (rivin-index (:rivit this) paivitetty-rivi)
          paivitetty-taulukko (assoc-in this [:rivit rivin-index] paivitetty-rivi)
          args (remove #(= tyhja-arvo %) [a1 a2 a3 a4 a5 a6 a7])]
      (assert paivita-taulukkko! "Taulukolle ei ole määritetty :taulukon-paivitys-fn! parametria")
      ;taulukon-paivitys-fn ottaa taulukon uuden arvon ja päivittää tilan
      (apply paivita-taulukkko! paivitetty-taulukko args)))
  (paivita-rivi! [this paivitetty-rivi a1 a2 a3 a4 a5 a6]
    (p/paivita-rivi! this paivitetty-rivi a1 a2 a3 a4 a5 a6 tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi a1 a2 a3 a4 a5]
    (p/paivita-rivi! this paivitetty-rivi  a1 a2 a3 a4 a5 tyhja-arvo tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi a1 a2 a3 a4]
    (p/paivita-rivi! this paivitetty-rivi a1 a2 a3 a4 tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi a1 a2 a3]
    (p/paivita-rivi! this paivitetty-rivi a1 a2 a3 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi a1 a2]
    (p/paivita-rivi! this paivitetty-rivi a1 a2 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi a1]
    (p/paivita-rivi! this paivitetty-rivi a1 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi]
    (p/paivita-rivi! this paivitetty-rivi tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2 a3 a4 a5 a6 a7]
    (let [paivita-taulukkko! (:taulukon-paivitys-fn! parametrit)
          solun-polku (into [] (apply concat (p/osan-polku-taulukossa this paivitetty-solu)))
          paivitetty-taulukko (assoc-in this solun-polku paivitetty-solu)
          args (remove #(= tyhja-arvo %) [a1 a2 a3 a4 a5 a6 a7])]
      (assert paivita-taulukkko! "Taulukolle ei ole määritetty :taulukon-paivitys-fn! parametria")
      ;taulukon-paivitys-fn ottaa taulukon uuden arvon ja päivittää tilan
      (apply paivita-taulukkko! paivitetty-taulukko args)))
  (paivita-solu! [this paivitetty-solu a1 a2 a3 a4 a5 a6]
    (p/paivita-solu! this paivitetty-solu a1 a2 a3 a4 a5 a6 tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2 a3 a4 a5]
    (p/paivita-solu! this paivitetty-solu  a1 a2 a3 a4 a5 tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2 a3 a4]
    (p/paivita-solu! this paivitetty-solu a1 a2 a3 a4 tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2 a3]
    (p/paivita-solu! this paivitetty-solu a1 a2 a3 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2]
    (p/paivita-solu! this paivitetty-solu a1 a2 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1]
    (p/paivita-solu! this paivitetty-solu a1 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu]
    (p/paivita-solu! this paivitetty-solu tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo)))

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