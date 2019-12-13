(ns harja.ui.taulukko.datan-kasittely-protokollat
  (:require [reagent.ratom :as ratom]))

(defprotocol IGridDatanKasittely
  (-rajapinta [this])
  (-muokkaa-osan-data! [this id f])
  (-aseta-osan-data! [this id uusi-data])
  (-muokkaa-rajapinnan-data! [this nimi-polku f])
  (-aseta-rajapinnan-data! [this nimi-polku uusi-data])
  (-aseta-pointteri! [this id polku])
  ;; TODO muokkaa-pointteri!
  (-osan-derefable [this id])
  (-aseta-datan-jarjestys! [this nimi jarjestys])
  (-jarjesta-data! [this]))

(defn rajapinta [datan-kasittelija]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)]
   ;; TODO määrittele :post
   }
  (-rajapinta datan-kasittelija))

(defn muokkaa-osan-data! [datan-kasittelija id f]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         (fn? f)]}
  (-muokkaa-osan-data! datan-kasittelija id f))

(defn aseta-osan-data! [datan-kasittelija id uusi-data]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         ]}
  (-aseta-osan-data! datan-kasittelija id uusi-data))

(defn muokkaa-rajapinnan-data! [datan-kasittelija polku f]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         (and (vector? polku)
              (find (rajapinta datan-kasittelija) polku))
         (fn? f)]
   :post [((get-in (rajapinta datan-kasittelija) polku)
           (get-in % polku))]}
  (-muokkaa-rajapinnan-data! datan-kasittelija polku f))

(defn aseta-rajapinnan-data! [datan-kasittelija polku uusi-data]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         (and (vector? polku)
              (find (rajapinta datan-kasittelija) polku))]
   :post [((get-in (rajapinta datan-kasittelija) polku)
           (get-in % polku))]}
  (-aseta-rajapinnan-data! datan-kasittelija polku uusi-data))

(defn aseta-pointteri! [datan-kasittelija id polku]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         (vector polku)]}
  (-aseta-pointteri! datan-kasittelija id polku))

(defn osan-derefable [datan-kasittelija id]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         ]
   :post [(satisfies? ratom/IReactiveAtom %)]}
  (-osan-derefable datan-kasittelija id))

(defn aseta-datan-jarjestys! [datan-kasittelija nimi jarjestys]
  (-aseta-datan-jarjestys! datan-kasittelija nimi jarjestys))

(defn jarjesta-data! [datan-kasittelija]
  (-jarjesta-data! datan-kasittelija))
