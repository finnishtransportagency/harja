(ns harja.ui.taulukko.protokollat.grid-osa)

(defprotocol IPiirrettava
  (-piirra [this]))

(defprotocol IPiilota
  (-piilota! [this])
  (-nayta! [this])
  (-piilotettu? [this]))

(defprotocol IGridOsa
  (-id [this] "Palauttaa osan idn")
  (-id? [this id] "Vertaa tämän osan idtä annettuun idhen")
  (-nimi [this] "Palauttaa osa nimen")
  (-aseta-nimi [this nimi] "Asettaa nimen"))

(defprotocol IKopioi
  (-kopioi [this] "Suorita deep copy"))

(defn id
  [solu]
  {:pre [(satisfies? IGridOsa solu)]
   :post [(symbol? %)]}
  (-id solu))

(defn id?
  [solu id]
  {:pre [(satisfies? IGridOsa solu)]
   :post [(boolean? %)]}
  (-id? solu id))

(defn nimi
  [solu]
  {:pre [(satisfies? IGridOsa solu)]
   :post [(satisfies? IEquiv %)]}
  (-nimi solu))

(defn aseta-nimi
  [solu nimi]
  {:pre [(satisfies? IGridOsa solu)
         (satisfies? IEquiv nimi)]
   :post [(id? solu (id %))]}
  (-aseta-nimi solu nimi))

(defn piirra [osa]
  {:pre [(satisfies? IPiirrettava osa)]}
  [:<>
   [-piirra osa]])

(defn piilota! [osa]
  ;;TODO :pre ja Post
  (-piilota! osa))
(defn nayta! [osa]
  ;;TODO :pre ja Post
  (-nayta! osa))
(defn piilotettu? [osa]
  ;;TODO :pre ja Post
  (-piilotettu? osa))

(defn kopioi [osa]
  ;; TODO :pre ja :post
  (-kopioi osa))
