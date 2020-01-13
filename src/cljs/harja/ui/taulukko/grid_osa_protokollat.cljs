(ns harja.ui.taulukko.grid-osa-protokollat)

(defprotocol IPiirrettava
  (-piirra [this]))

(defprotocol IGridOsa
  (-id [this] "Palauttaa osan idn")
  (-id? [this id] "Vertaa tämän osan idtä annettuun idhen")
  (-nimi [this] "Palauttaa osa nimen")
  (-aseta-nimi [this nimi] "Asettaa nimen"))

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
