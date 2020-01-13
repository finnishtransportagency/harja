(ns harja.ui.taulukko.grid-protokollat
  (:require [harja.ui.taulukko.solu-protokollat :as sp]
            [harja.ui.taulukko.grid-osa-protokollat :as gop]))

(defprotocol IGrid
  (-osat [this] [this polku])
  (-aseta-osat [this osat] [this polku osat])
  (-paivita-osat [this f] [this polku f])
  (-koko [this])
  (-koot [this])
  (-aseta-koko! [this koko])
  (-paivita-koko! [this f])
  (-alueet [this])
  (-aseta-alueet [this alueet])
  (-paivita-alueet [this f])
  (-aseta-root-fn [this f])

  (-lisaa-rivi [this solu] [this solu index])
  (-lisaa-sarake [this solu] [this solu index])

  (-rivi [this tunniste] "Palauttaa rivin tunnisteen perusteella")
  (-sarake [this tunniste] "Palauttaa sarakkeen tunnisteen perusteella")
  (-solu [this tunniste] "Palauttaa solun tunnisteen perusteella"))

(defprotocol IGridDataYhdistaminen
  (-rajapinta-grid-yhdistaminen! [this rajapinta datan-kasittelija grid-kasittelija]))

(defn lapset [osa]
  (when (satisfies? IGrid osa)
    (-osat osa)))

(defn aseta-lapset
  ([grid osat]
   {:pre [(satisfies? IGrid grid)
          (every? #(satisfies? gop/IGridOsa %) osat)]
    :post [(satisfies? IGrid %)
           (= (lapset %) osat)]}
   (-aseta-osat grid osat))
  ([grid polku osat]
   {:pre [(satisfies? IGrid grid)
          (vector? polku)
          (every? #(satisfies? gop/IGridOsa %) osat)]
    :post [(satisfies? IGrid %)]}
   (-aseta-osat grid polku osat)))

(defn paivita-lapset
  ([grid f]
   {:pre [(satisfies? IGrid grid)
          (fn? f)]
    :post [(satisfies? IGrid %)]}
   (-paivita-osat grid f))
  ([grid polku f]
   {:pre [(satisfies? IGrid grid)
          (vector? polku)
          (fn? f)]
    :post [(satisfies? IGrid %)]}
   (-paivita-osat grid polku f)))

(defn lisaa-sarake
  ([grid solu]
   {:pre [(satisfies? IGrid grid)
          (satisfies? sp/ISolu solu)]
    :post [(satisfies? IGrid %)
           ;Puun päissä on vain yksi lisää
           #_(= (count (lapset grid))
              (inc (count (lapset %))))]}
   (-lisaa-sarake grid solu))
  ([grid solu index]
   {:pre [(satisfies? IGrid grid)
          (satisfies? sp/ISolu solu)
          (integer? index)]
    :post [(satisfies? IGrid %)
           #_(= (count (lapset grid))
              (inc (count (lapset %))))]}
   (-lisaa-sarake grid solu index)))

(defn lisaa-rivi
  ([grid solu]
   {:pre [(satisfies? IGrid grid)
          (satisfies? sp/ISolu solu)]
    :post [(satisfies? IGrid %)
           #_(= (count (lapset grid))
              (inc (count (lapset %))))]}
   (-lisaa-rivi grid solu))
  ([grid solu index]
   {:pre [(satisfies? IGrid grid)
          (satisfies? sp/ISolu solu)
          (integer? index)]
    :post [(satisfies? IGrid %)
           #_(= (count (lapset grid))
              (inc (count (lapset %))))]}
   (-lisaa-rivi grid solu index)))

(defn koko [osa]
  {:pre [(satisfies? gop/IGridOsa osa)]}
  (when (satisfies? IGrid osa)
    (-koko osa)))

(defn koot [osa]
  {:pre [(satisfies? IGrid osa)]}
  (-koot osa))

(defn aseta-koko! [grid koko]
  {:pre [(satisfies? IGrid grid)
         ;;TODO koko oikein?
         ]}
  (-aseta-koko! grid koko))

(defn paivita-koko! [grid f]
  {:pre [(satisfies? IGrid grid)
         (fn? f)]}
  (-paivita-koko! grid f))

(defn alueet [osa]
  {:pre [(satisfies? gop/IGridOsa osa)]}
  (when (satisfies? IGrid osa)
    (-alueet osa)))

(defn aseta-alueet [grid alueet]
  {:pre [(satisfies? IGrid grid)
         (vector? alueet)
         ;; TODO Tarkista alueet
         ]
   :post [(satisfies? IGrid %)
          (= (alueet %) alueet)]}
  (-aseta-alueet grid alueet))

(defn paivita-alueet [grid f]
  {:pre [(satisfies? IGrid grid)
         (fn? f)]
   :post [(satisfies? IGrid %)]}
  (-paivita-alueet grid f))

(defn rajapinta-grid-yhdistaminen! [grid rajapinta datan-kasittelija grid-kasittelija]
  {:pre [(and (satisfies? IGridDataYhdistaminen grid)
              (satisfies? IGrid grid))]}
  (-rajapinta-grid-yhdistaminen! grid rajapinta datan-kasittelija grid-kasittelija))

(defn aseta-root-fn [this f]
  ;; TODO :pre ja :post
  (-aseta-root-fn this f))