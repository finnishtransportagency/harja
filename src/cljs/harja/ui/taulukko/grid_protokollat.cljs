(ns harja.ui.taulukko.grid-protokollat
  (:require [harja.ui.taulukko.datan-kasittely-protokollat :as dp]))

(defprotocol IGrid
  (-solut [this])
  (-aseta-solut [this solut])
  (-paivita-solut [this f])
  (-koko [this])
  (-koot [this])
  (-aseta-koko! [this koko])
  (-paivita-koko! [this f])
  (-alueet [this])
  (-aseta-alueet [this alueet])
  (-paivita-alueet [this f])

  (-rivi [this tunniste] "Palauttaa rivin tunnisteen perusteella")
  (-sarake [this tunniste] "Palauttaa sarakkeen tunnisteen perusteella")
  (-solu [this tunniste] "Palauttaa solun tunnisteen perusteella"))

(defprotocol IGridDataYhdistaminen
  (-gridin-pointterit! [this datan-kasittelija])
  (-gridin-muoto! [this datan-kasittelija])
  (-rajapinta-grid-yhdistaminen! [this datan-kasittelija])
  (-vanhempi [this datan-kasittelija id]))

(defprotocol IPiirrettava
  (-piirra [this]))

(defprotocol IGridOsa
  (-id [this] "Palauttaa osan idn")
  (-id? [this id] "Vertaa tämän osan idtä annettuun idhen")
  (-nimi [this] "Palauttaa osa nimen")
  (-aseta-nimi [this nimi] "Asettaa nimen"))

(defprotocol IFmt
  (-lisaa-fmt [this f] "Lisää asiaan formatointi funktion")
  (-lisaa-fmt-aktiiviselle [this f] "Jos osa on aktiivinen, minkälainen formatointi?"))

(defprotocol IDatanKasittely
  (-lisaa-filtteri [this f] "Data annetaan ensin tälle, jossa sitä voidaan filteröidä vaikapa jonku muun komponentin tilan mukaan")
  (-lisaa-arvo [this f] "Tässä kaivetaan lopullinen arvo ulos datasta"))

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

(defn lapset [osa]
  (when (satisfies? IGrid osa)
    (-solut osa)))

(defn aseta-lapset [grid solut]
  {:pre [(satisfies? IGrid grid)
         (every? #(satisfies? IGridOsa %) solut)]
   :post [(satisfies? IGrid %)
          (= (lapset %) solut)]}
  (-aseta-solut grid solut))

(defn paivita-lapset [grid f]
  {:pre [(satisfies? IGrid grid)
         (fn? f)]
   :post [(satisfies? IGrid %)]}
  (-paivita-solut grid f))

(defn koko [osa]
  {:pre [(satisfies? IGridOsa osa)]}
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
  {:pre [(satisfies? IGridOsa osa)]}
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

(defn vanhempi [grid datan-kasittelija id]
  {:pre [(satisfies? dp/IGridDatanKasittely datan-kasittelija)
         (symbol? id)]
   ;; TODO määrittele :post
   }
  (-vanhempi grid datan-kasittelija id))

(defn gridin-pointterit! [grid datan-kasittelija]
  {:pre [(and (satisfies? IGridDataYhdistaminen grid)
              (satisfies? IGrid grid))
         (satisfies? dp/IGridDatanKasittely datan-kasittelija)]}
  (-gridin-pointterit! grid datan-kasittelija))

(defn gridin-muoto! [grid datan-kasittelija]
  {:pre [(and (satisfies? IGridDataYhdistaminen grid)
              (satisfies? IGrid grid))
         (satisfies? dp/IGridDatanKasittely datan-kasittelija)]}
  (-gridin-muoto! grid datan-kasittelija))

(defn rajapinta-grid-yhdistaminen! [grid datan-kasittelija]
  {:pre [(and (satisfies? IGridDataYhdistaminen grid)
              (satisfies? IGrid grid))
         (satisfies? dp/IGridDatanKasittely datan-kasittelija)]}
  (-rajapinta-grid-yhdistaminen! grid datan-kasittelija))


(defn lisaa-fmt [solu f]
  {:pre [(satisfies? IGridOsa solu)
         (fn? f)]}
  (-lisaa-fmt solu f))
(defn lisaa-fmt-aktiiviselle [solu f]
  {:pre [(satisfies? IGridOsa solu)
         (fn? f)]}
  (-lisaa-fmt-aktiiviselle solu f))

(defn lisaa-filtteri [solu f]
  {:pre [(satisfies? IGridOsa solu)
         (fn? f)]}
  (-lisaa-filtteri solu f))
(defn lisaa-arvo [solu f]
  {:pre [(satisfies? IGridOsa solu)
         (fn? f)]}
  (-lisaa-arvo solu f))