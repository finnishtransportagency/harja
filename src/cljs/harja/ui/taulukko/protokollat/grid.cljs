(ns harja.ui.taulukko.protokollat.grid
  (:require [harja.ui.taulukko.protokollat.solu :as sp]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]))

(defprotocol IGrid
  (-osat [this] [this polku])
  (-aseta-osat! [this osat] [this polku osat])
  (-paivita-osat! [this f] [this polku f])
  (-koko [this])
  (-koot [this])
  (-aseta-koko! [this koko])
  (-paivita-koko! [this f])
  (-alueet [this])
  (-aseta-alueet! [this alueet])
  (-paivita-alueet! [this f])
  (-aseta-root-fn! [this f])
  (-parametrit [this])
  (-aseta-parametrit! [this parametrit])
  (-paivita-parametrit! [this f])

  (-lisaa-rivi! [this solu] [this solu index])
  (-lisaa-sarake! [this solu] [this solu index])
  (-poista-rivi! [this solu])
  (-poista-sarake! [this solu])

  (-rivi [this tunniste] "Palauttaa rivin tunnisteen perusteella")
  (-sarake [this tunniste] "Palauttaa sarakkeen tunnisteen perusteella")
  (-solu [this tunniste] "Palauttaa solun tunnisteen perusteella"))

(defprotocol IGridDataYhdistaminen
  (-rajapinta-grid-yhdistaminen! [this rajapinta datan-kasittelija grid-kasittelija])
  (-grid-tapahtumat [this data-atom tapahtumat]))

(defn lapset [osa]
  (when (satisfies? IGrid osa)
    (-osat osa)))

(defn aseta-lapset!
  ([grid osat]
   {:pre [(satisfies? IGrid grid)
          (every? #(satisfies? gop/IGridOsa %) osat)]
    :post [(satisfies? IGrid %)
           (= (lapset %) osat)]}
   (-aseta-osat! grid osat))
  ([grid polku osat]
   {:pre [(satisfies? IGrid grid)
          (vector? polku)
          (every? #(satisfies? gop/IGridOsa %) osat)]
    :post [(satisfies? IGrid %)]}
   (-aseta-osat! grid polku osat)))

(defn paivita-lapset!
  ([grid f]
   {:pre [(satisfies? IGrid grid)
          (fn? f)]
    :post [(satisfies? IGrid %)]}
   (-paivita-osat! grid f))
  ([grid polku f]
   {:pre [(satisfies? IGrid grid)
          (vector? polku)
          (fn? f)]
    :post [(satisfies? IGrid %)]}
   (-paivita-osat! grid polku f)))

(defn lisaa-sarake!
  ([grid solu]
   {:pre [(satisfies? IGrid grid)
          (satisfies? sp/ISolu solu)]
    ;;TODO
    #_#_:post [                                                 ;;;TODO
           ;Puun päissä on vain yksi lisää
           #_(= (count (lapset grid))
              (inc (count (lapset %))))]}
   (-lisaa-sarake! grid solu))
  ([grid solu index]
   {:pre [(satisfies? IGrid grid)
          (satisfies? sp/ISolu solu)
          (integer? index)]
    ;; TODO :post
    #_#_:post [
           #_(= (count (lapset grid))
              (inc (count (lapset %))))]}
   (-lisaa-sarake! grid solu index)))

(defn lisaa-rivi!
  ([grid rivi]
   {:pre [(satisfies? IGrid grid)
          (satisfies? IGrid rivi)]
    ;; TODO :post
    :post [
           #_(= (count (lapset grid))
              (inc (count (lapset %))))]}
   (-lisaa-rivi! grid rivi))
  ([grid rivi index-polku]
   {:pre [(satisfies? IGrid grid)
          (satisfies? IGrid rivi)
          (vector? index-polku)
          (every? integer? index-polku)]
    :post [(satisfies? IGrid %)]}
   (-lisaa-rivi! grid rivi index-polku)))

(defn poista-rivi!
  ;; TODO :PRE :POST
  [grid solu]
  (-poista-rivi! grid solu))

(defn poista-sarake!
  ;; TODO :PRE :POST
  [grid solu]
  (-poista-sarake! grid solu))

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

(defn aseta-alueet! [grid alueiden-arvot]
  {:pre [(satisfies? IGrid grid)
         (vector? alueiden-arvot)
         ;; TODO Tarkista alueet
         ]
   ;; TODO :post
   :post [
          (= (alueet %) alueiden-arvot)]}
  (-aseta-alueet! grid alueiden-arvot))

(defn paivita-alueet! [grid f]
  {:pre [(satisfies? IGrid grid)
         (fn? f)]
   ;; TODO :post
   }
  (-paivita-alueet! grid f))

(defn rajapinta-grid-yhdistaminen! [grid rajapinta datan-kasittelija grid-kasittelija]
  {:pre [(and (satisfies? IGridDataYhdistaminen grid)
              (satisfies? IGrid grid))]}
  (-rajapinta-grid-yhdistaminen! grid rajapinta datan-kasittelija grid-kasittelija))

(defn aseta-root-fn! [this m]
  {:pre [(fn? (:haku m))
         (fn? (:paivita! m))]
   :post [(satisfies? IGrid %)]}
  (-aseta-root-fn! this m))

(defn grid-tapahtumat [this data-atom tapahtuma-maaritelmat]
  ;; TODO :pre ja :post
  (-grid-tapahtumat this data-atom tapahtuma-maaritelmat))

(defn parametrit [grid]
  ;;TODO :pre ja :post
  (-parametrit grid))
(defn aseta-parametrit! [grid parametrit]
  ;; TODO :pre ja :post
  (-aseta-parametrit! grid parametrit))
(defn paivita-parametrit! [grid f]
  ;; TODO :pre ja :post
  (-paivita-parametrit! grid f))