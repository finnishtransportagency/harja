(ns harja.ui.taulukko.taulukko
  "Saman tyylinen kuin grid, mutta taulukolle annetaan näytettävä tila. Lisäksi ei määritellä miltä
   joku rivi näyttää vaan jätetään se käyttäjän vastuulle"
  (:require [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko-debug :as debug]
            [clojure.spec.alpha :as s]
            [harja.loki :as loki]))

(defonce tyhja-arvo (gensym))

(defn taulukon-jana-validi?
  [taulukon-skeemat jana janan-skeema]
  (and (not (nil? janan-skeema))
       (= (type jana) (:janan-tyyppi janan-skeema))
       (if (contains? janan-skeema :janat)
         (loop [[jana & janat] (p/janan-osat jana)
                [skeeman-nimi & _ :as skeemojen-nimet] (:janat janan-skeema)
                validi-jana? true]
           (if (or (false? validi-jana?)
                   (nil? jana))
             validi-jana?
             (let [validi-jana? (taulukon-jana-validi? taulukon-skeemat jana (get taulukon-skeemat skeeman-nimi))
                   [validi-jana? skeemojen-nimet] (if (false? validi-jana?)
                                                    ;; Jos ei nykyisellä skeemalla ollut validi, kokeillaan seuraavaa
                                                    [(taulukon-jana-validi? taulukon-skeemat jana (get taulukon-skeemat (second skeemojen-nimet))) (rest skeemojen-nimet)]
                                                    [validi-jana? skeemojen-nimet])]
               (recur janat
                      skeemojen-nimet
                      validi-jana?))))
         (every? true?
                 (map (fn [osan-skeema osa]
                        (= (type osa) osan-skeema))
                      (:osat janan-skeema)
                      (p/janan-osat jana))))))

(defn rivin-index
  "Palauttaa janan indeksin taulukossa"
  [rivit rivi]
  (first (keep-indexed #(when (= (p/janan-id %2) (p/janan-id rivi))
                          %1)
                       rivit)))

(defn tee-rivi [jana args]
  (let [jana-fn #(jana (keyword (gensym "jana-")) % #{"jana"})]
    (loop [rivi []
           args (vec args)]
      (if (= 0 (count args))
        (jana-fn rivi)
        (let [a (first args)
              o-fn (first a)
              o-par (vec (rest a))]
          (recur (conj rivi (apply
                              o-fn
                              (conj o-par {:class #{"osa" "osa-teksti"}})))
                 (rest args)))))))

(defonce muuta-avain
         {:id     [:taulukon-id]
          :lapset [:rivit]
          :class  [:parametrit :class]})

; {:taulukon-id 1 :skeema-rivi .. :skeema-sarake :rivit [] :parametrit}
(defrecord Taulukko [taulukon-id skeema-rivi skeema-sarake rivit parametrit]
  p/Taulukko
  (piirra-taulukko [this]
    (assert (vector? (:rivit this)) (str "TAULUKON: " taulukon-id " RIVIT EI OLE VEKTORI"))
    (let [luokat (-> this :parametrit :class)
          dom-id (-> this :parametrit :id)]
      [:div.taulukko {:data-cy "taulukko"
                      :id      dom-id
                      :class   (apply str (interpose " " luokat))}
       (when debug/TAULUKKO_DEBUG
         [debug/debug this])
       (for [rivi (:rivit this)]
         (with-meta [p/piirra-jana rivi]
                    {:key (p/janan-id rivi)}))]))
  (otsikon-index [this otsikko]
    (first (keep-indexed (fn [index s-otsikko]
                           (when (= s-otsikko otsikko)
                             index))
                         (:skeema-sarake this))))
  (rivin-skeema [this rivi]
    (some (fn [[skeeman-nimi rivin-skeema]]
            (when (taulukon-jana-validi? (:skeema-rivi this) rivi rivin-skeema)
              skeeman-nimi))
          (:skeema-rivi this)))
  (osan-polku-taulukossa [this osa]
    (first (keep-indexed (fn [rivin-index rivi]
                           (let [solun-polku (p/osan-polku rivi osa)
                                 rivin-polku [:rivit rivin-index]]
                             (cond
                               (and (satisfies? p/Jana osa)
                                    (p/janan-id? osa (p/janan-id rivi))) [rivin-polku]
                               solun-polku (into []
                                                 (cons rivin-polku solun-polku))
                               :else nil)))
                         (:rivit this))))
  (taulukon-id [this]
    (:taulukon-id this))
  (taulukon-id? [this id]
    (= (:taulukon-id this) id))
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

  (lisaa-rivi! [this rivin-tiedot a1 a2 a3 a4 a5 a6 a7]
    (let [paivita-taulukko! (:taulukon-paivitys-fn! parametrit)
          {:keys [avain rivi]} rivin-tiedot
          args (remove #(= tyhja-arvo %) [a1 a2 a3 a4 a5 a6 a7])
          paivitetty-taulukko (update this :rivit (fn [m] (conj m (tee-rivi rivi args))))]
      (paivita-taulukko! paivitetty-taulukko)))
  (lisaa-rivi! [this rivin-avain a1 a2 a3 a4 a5 a6]
    (p/lisaa-rivi! this rivin-avain a1 a2 a3 a4 a5 a6 tyhja-arvo))
  (lisaa-rivi! [this rivin-avain a1 a2 a3 a4 a5]
    (p/lisaa-rivi! this rivin-avain a1 a2 a3 a4 a5 tyhja-arvo tyhja-arvo))
  (lisaa-rivi! [this rivin-avain a1 a2 a3 a4]
    (p/lisaa-rivi! this rivin-avain a1 a2 a3 a4 tyhja-arvo tyhja-arvo tyhja-arvo))
  (lisaa-rivi! [this rivin-avain a1 a2 a3]
    (p/lisaa-rivi! this rivin-avain a1 a2 a3 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (lisaa-rivi! [this rivin-avain a1 a2]
    (p/lisaa-rivi! this rivin-avain a1 a2 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (lisaa-rivi! [this rivin-avain a1]
    (p/lisaa-rivi! this rivin-avain a1 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (lisaa-rivi! [this rivin-avain]
    (p/lisaa-rivi! this rivin-avain tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))

  (paivita-rivi! [this paivitetty-rivi a1 a2 a3 a4 a5 a6 a7]
    (let [paivita-taulukkko! (:taulukon-paivitys-fn! parametrit)
          rivin-polku (into [] (apply concat (p/osan-polku-taulukossa this paivitetty-rivi)))
          paivitetty-taulukko (assoc-in this rivin-polku paivitetty-rivi)
          args (remove #(= tyhja-arvo %) [a1 a2 a3 a4 a5 a6 a7])]
      (assert paivita-taulukkko! "Taulukolle ei ole määritetty :taulukon-paivitys-fn! parametria")
      ;taulukon-paivitys-fn ottaa taulukon uuden arvon ja päivittää tilan
      (apply paivita-taulukkko! paivitetty-taulukko args)))
  (paivita-rivi! [this paivitetty-rivi a1 a2 a3 a4 a5 a6]
    (p/paivita-rivi! this paivitetty-rivi a1 a2 a3 a4 a5 a6 tyhja-arvo))
  (paivita-rivi! [this paivitetty-rivi a1 a2 a3 a4 a5]
    (p/paivita-rivi! this paivitetty-rivi a1 a2 a3 a4 a5 tyhja-arvo tyhja-arvo))
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
    (p/paivita-solu! this paivitetty-solu a1 a2 a3 a4 a5 tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2 a3 a4]
    (p/paivita-solu! this paivitetty-solu a1 a2 a3 a4 tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2 a3]
    (p/paivita-solu! this paivitetty-solu a1 a2 a3 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1 a2]
    (p/paivita-solu! this paivitetty-solu a1 a2 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu a1]
    (p/paivita-solu! this paivitetty-solu a1 tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  (paivita-solu! [this paivitetty-solu]
    (p/paivita-solu! this paivitetty-solu tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo tyhja-arvo))
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain))

  (paivita-arvo [this avain f]
    (update-in this (muuta-avain avain) f))
  (paivita-arvo [this avain f a1]
    (update-in this (muuta-avain avain) f a1))
  (paivita-arvo [this avain f a1 a2]
    (update-in this (muuta-avain avain) f a1 a2))
  (paivita-arvo [this avain f a1 a2 a3]
    (update-in this (muuta-avain avain) f a1 a2 a3))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (update-in this (muuta-avain avain) f a1 a2 a3 a4))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (update-in this (muuta-avain avain) f a1 a2 a3 a4 a5))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (update-in this (muuta-avain avain) f a1 a2 a3 a4 a5 a6))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (update-in this (muuta-avain avain) f a1 a2 a3 a4 a5 a6 a7))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (update-in this (muuta-avain avain) f a1 a2 a3 a4 a5 a6 a7 a8))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (update-in this (muuta-avain avain) f a1 a2 a3 a4 a5 a6 a7 a8 a9))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (update-in this (muuta-avain avain) f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)))

(defn taulukko
  ([tila] [taulukko tila nil])
  ([tila luokat]
   {:pre [(sequential? tila)
          (every? #(satisfies? p/Jana %) tila)]}
   [:div.taulukko {:data-cy "taulukko"
                   :class   (apply str (interpose " " luokat))}
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