(ns harja.ui.taulukko.grid-osa-protokollat)

(defprotocol IPiirrettava
  (-piirra [this]))

(defprotocol IPiillota
  (-piillota! [this])
  (-nayta! [this])
  (-piillotettu? [this]))

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

(defn piillota! [osa]
  ;;TODO :pre ja Post
  (-piillota! osa))
(defn nayta! [osa]
  ;;TODO :pre ja Post
  (-nayta! osa))
(defn piillotettu? [osa]
  ;;TODO :pre ja Post
  (-piillotettu? osa))

(defn kopioi [osa]
  ;; TODO :pre ja :post
  (-kopioi osa))
