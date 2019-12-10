(ns harja.ui.taulukko.protokollat
  (:require [reagent.ratom :as ratom]))


(defn aseta-asian-arvo [asia avain-arvo muuta-avain]
  (let [asian-avain-arvo (map (fn [[avain arvo]]
                                [(muuta-avain avain) arvo])
                              (partition 2 avain-arvo))]
    (reduce (fn [asia [polku arvo]]
              (assoc-in asia polku arvo))
            asia asian-avain-arvo)))

(defprotocol Asia
  "Taulukon asioille haku ja päivitys funktioita"
  (arvo [this avain])
  (aseta-arvo [this k1 a1]
              [this k1 a1 k2 a2]
              [this k1 a1 k2 a2 k3 a3]
              [this k1 a1 k2 a2 k3 a3 k4 a4]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9])
  (paivita-arvo [this avain f]
                [this avain f a1]
                [this avain f a1 a2]
                [this avain f a1 a2 a3]
                [this avain f a1 a2 a3 a4]
                [this avain f a1 a2 a3 a4 a5]
                [this avain f a1 a2 a3 a4 a5 a6]
                [this avain f a1 a2 a3 a4 a5 a6 a7]
                [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
                [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
                [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]))

(defprotocol CollectionKasittely
  (lisaa-kokoelman-kasittely [this f] "Kutsuu annettua funktiota arvolle, jos coll? palauttaa true arvolle")
  ;; Nämä eroaa Asia protokollasta siten, että Asia:n funktioiden tulisi palauttaa relevantti arvo kokoelmasta
  ;; käyttäen kokoelman käsittely funktiota. Nämä alla olevat taasen koskee koko kokoelmaa.
  (kokoelma [this] "Palauttaa kokoelman")
  (aseta-kokoelma [this coll] "Asettaa uuden kokoelman")
  (paivita-kokoelma [this f] "Päivittaa kokoelmaa funktiolla"))

(defprotocol Jana
  (piirra-jana [this]
               "Tämän funktion pitäisi luoda html elementti, joka edustaa joko riviä tai saraketta.
                Todennäiköisesti kutsuu myös osa/piirra-osa funktiota")
  (janan-id? [this id])
  (janan-osat [this])
  (janan-id [this])
  (osan-polku [this osa]
              "Palauttaa nil, jos osa ei kuulu tähän janaan"))

(defprotocol Osa
  (piirra-osa [this])
  (osan-id? [this id])
  (osan-id [this])
  (osan-tila [this]))

(defprotocol Tila
  (hae-tila [this])
  (aseta-tila! [this tila])
  (paivita-tila! [this tila])
  (luo-tila! [this]))

(defprotocol Taulukko
  (piirra-taulukko [this])
  (taulukon-id [this])
  (taulukon-id? [this id])
  (rivin-skeema [this jana])
  (otsikon-index [this otsikko])
  (osan-polku-taulukossa [this osa] "Palauttaa vektorin, jossa ensimmäinen elementti on polku janaan ja toinen polku janasta osaan")
  (paivita-taulukko! [this] [this a1] [this a1 a2] [this a1 a2 a3] [this a1 a2 a3 a4] [this a1 a2 a3 a4 a5] [this a1 a2 a3 a4 a5 a6] [this a1 a2 a3 a4 a5 a6 a7]
                     "Tulisi palauttaa taulukko")
  (paivita-rivi! [this paivitetty-rivi] [this paivitetty-rivi a1] [this paivitetty-rivi a1 a2] [this paivitetty-rivi a1 a2 a3] [this paivitetty-rivi a1 a2 a3 a4] [this paivitetty-rivi a1 a2 a3 a4 a5] [this paivitetty-rivi a1 a2 a3 a4 a5 a6] [this paivitetty-rivi a1 a2 a3 a4 a5 a6 a7]
                 "Tulisi palauttaa taulukko")
  (paivita-solu! [this paivitetty-osa] [this paivitetty-osa a1] [this paivitetty-osa a1 a2] [this paivitetty-osa a1 a2 a3] [this paivitetty-osa a1 a2 a3 a4] [this paivitetty-osa a1 a2 a3 a4 a5] [this paivitetty-osa a1 a2 a3 a4 a5 a6] [this paivitetty-osa a1 a2 a3 a4 a5 a6 a7]
                 "Tulisi palauttaa taulukko"))

(defprotocol TilanSeuranta
  "Tämän avulla lisätään taulukon asiaan derefable renderöinti funktioon, jonka seurauksena asia renderöidään uudestaan.
   Hyödyllinen, jos asian arvo on riippuvainen jostain siitä riippumattomasta arvosta.
   Esim. 'summa'/'yhteensä' osat ovat riippuvaisia muista arvoista."
  (lisaa-renderointi-derefable! [this tila polut] [this tila polut alkutila] "Palauttaa tämän muutettuna siten, että jokainen muutos poluissa aiheuttaa tämän re-renderöinnin.")
  (lisaa-muodosta-arvo [this f] "Muodostaa asian arvon tämän funktion perusteella."))

(defprotocol Fmt
  (lisaa-fmt [this f] "Lisää asiaan formatointi funktion")
  (lisaa-fmt-aktiiviselle [this f] "Jos osa on aktiivinen, minkälainen formatointi?"))

(defprotocol IGrid
  (-solut [this])
  (-aseta-solut [this solut])
  (-paivita-solut [this f])
  (-koko [this])
  (-aseta-koko! [this koko])
  (-paivita-koko! [this f])
  (-alueet [this])
  (-aseta-alueet [this alueet])
  (-paivita-alueet [this f])

  (-rivi [this tunniste] "Palauttaa rivin tunnisteen perusteella")
  (-sarake [this tunniste] "Palauttaa sarakkeen tunnisteen perusteella")
  (-solu [this tunniste] "Palauttaa solun tunnisteen perusteella"))

(defprotocol IGridDatanKasittely
  ;;TODO Polun muokkaukset
  (-muokkaa-osan-data! [this id f])
  (-aseta-osan-data! [this id uusi-data])
  (-muokkaa-alueen-data! [this nimi-polku f])
  (-aseta-alueen-data! [this nimi-polku uusi-data])
  (-gridin-muoto! [this grid])
  (-osan-derefable [this id]))

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
  (-piirra osa))

(defn lapset [osa]
  {:pre [(satisfies? IGridOsa osa)]}
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

(defn osan-derefable [datan-kasittelija id]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         ]
   :post [(satisfies? ratom/IReactiveAtom %)]}
  (-osan-derefable datan-kasittelija id))

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

(defn muokkaa-alueen-data! [datan-kasittelija nimi-polku f]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         (vector? nimi-polku)
         (fn? f)]}
  (-muokkaa-alueen-data! datan-kasittelija nimi-polku f))

(defn aseta-alueen-data! [datan-kasittelija nimi-polku uusi-data]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         (vector? nimi-polku)]}
  (-aseta-alueen-data! datan-kasittelija nimi-polku uusi-data))

(defn gridin-muoto! [datan-kasittelija grid]
  {:pre [(satisfies? IGridDatanKasittely datan-kasittelija)
         ;;TODO tarkista id
         ]}
  (-gridin-muoto! datan-kasittelija grid))


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