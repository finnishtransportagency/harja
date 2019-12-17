(ns harja.ui.taulukko.datan-kasittely-protokollat
  (:require [reagent.ratom :as ratom]))

(defprotocol IGridDatanKasittely
  (-rajapinta [this])
  (-muokkaa-osan-data! [this id f])
  (-aseta-osan-data! [this id uusi-data])
  (-muokkaa-rajapinnan-data! [this nimi-polku f])
  (-aseta-rajapinnan-data! [this nimi-polku uusi-data])
  (-aseta-pointteri! [this id polku])
  (-pointterit [this])
  (-root [this])
  (-aseta-root! [this grid])
  ;; TODO muokkaa-pointteri!
  (-aseta-grid-polut! [this polut])
  (-aseta-grid-rajapinta! [this grid-rajapinta])
  (-grid-rajapinta [this])
  (-rajapinta-data [this rajapinta-polku])
  (-osan-derefable [this id])
  (-aseta-datan-jarjestys! [this nimi jarjestys])
  (-jarjesta-data! [this]))

(defn rajapinta [datan-kasittelija]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)]
   ;; TODO määrittele :post
   }
  (-rajapinta datan-kasittelija))

(defn pointterit [datan-kasittelija]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)]
   ;; TODO määrittele :post
   }
  (-pointterit datan-kasittelija))

(defn root [datan-kasittelija]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)]
   ;; TODO määrittele :post
   }
  (-root datan-kasittelija))

(defn aseta-root! [datan-kasittelija grid]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)]
   ;; TODO määrittele :post
   }
  (-aseta-root! datan-kasittelija grid))

(defn muokkaa-osan-data! [datan-kasittelija id f]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         (fn? f)]}
  (-muokkaa-osan-data! datan-kasittelija id f))

(defn aseta-osan-data! [datan-kasittelija id uusi-data]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         ]}
  (println "ID: " id)
  (println "UUSI DATA: " uusi-data)
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
         ;; TODO tarkista polku {:polku-dataan [..] :polku-pointtereihin ?}
         ]}
  (-aseta-pointteri! datan-kasittelija id polku))

(defn aseta-grid-polut! [datan-kasittelija polut]
  (-aseta-grid-polut! datan-kasittelija polut))

(defn aseta-grid-rajapinta! [datan-kasittelija grid-rajapinta]
  (-aseta-grid-rajapinta! datan-kasittelija grid-rajapinta))

(defn grid-rajapinta [datan-kasittelija]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)]}
  (-grid-rajapinta datan-kasittelija))

(defn rajapinta-data [datan-kasittelija rajapinta-polku]
  ;;TODO :pre ja :post
  (-rajapinta-data datan-kasittelija rajapinta-polku))

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
