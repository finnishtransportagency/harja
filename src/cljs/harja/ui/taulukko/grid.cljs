(ns harja.ui.taulukko.grid
  (:require [harja.ui.taulukko.impl.grid :as g]
            [harja.ui.taulukko.impl.alue :as alue]
            [harja.ui.taulukko.protokollat.grid :as gp]
            [harja.ui.taulukko.protokollat.solu :as sp]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]
            [harja.ui.taulukko.impl.datan-kasittely :as dk]
            [reagent.core :as r]))

;; KONSTRUKTORIT

; - Grid
(defn grid
  [asetukset]
  {:pre [(g/validi-grid-asetukset? asetukset)]
   :post [(instance? g/Grid %)
          (symbol? (gop/id %))]}
  (g/grid-c g/->Grid asetukset))

(defn rivi
  "Rivi on grid, mutta varmistetaan, että alueessa on vain yksi rivi."
  [asetukset alueet]
  {:pre [(g/validi-grid-asetukset? (assoc asetukset :alueet alueet))
         (= (count alueet) 1)
         #_(= -1 (apply - (:rivit (first alueet))))]
   :post [(instance? alue/Rivi %)
          (symbol? (gop/id %))]}
  (g/grid-c alue/->Rivi (assoc asetukset :alueet alueet)))

(defn taulukko
  "Taulukko on grid, mutta varmistetaan, että kaikki osat ovat samanlaisia"
  [asetukset osat]
  {:pre [(g/validi-grid-asetukset? (assoc asetukset :osat osat))
         (alue/samat-osat? osat)]
   :post [(instance? alue/Taulukko %)
          (symbol? (gop/id %))]}
  (g/grid-c alue/->Taulukko (assoc asetukset :osat osat)))

; - Datan käsittelijä
(defn datan-kasittelija [data-atom rajapinta haku-kuvaus asetus-kuvaus seurannat]
  (let [seurannan-tila (r/atom nil)
        seurannat (dk/aseta-seuranta! data-atom seurannan-tila seurannat)
        kuuntelijat (dk/rajapinnan-kuuntelijat data-atom seurannan-tila rajapinta haku-kuvaus)
        asettajat (dk/rajapinnan-asettajat data-atom rajapinta asetus-kuvaus)]
    {:kuuntelijat kuuntelijat
     :asettajat asettajat
     :seurannat seurannat}))

;; KOPIOINNIT

(defn grid-pohjasta [grid-pohja]
  (let [kopio (gop/kopioi grid-pohja)]
    (g/muuta-id kopio)))

;; HAUT

; - gridiin liittyvät haut
(defn get-in-grid [osa polku]
  (g/get-in-grid osa polku))

(defn root [osa]
  (g/root osa))

(defn vanhempi [osa]
  (g/vanhempi osa))

(defn osa-polusta
  [osa polku]
  {:pre [(satisfies? gop/IGridOsa osa)
         (vector? polku)]}
  (let [osan-polku (::g/nimi-polku osa)]
    (loop [[polun-osa & loput-polusta] polku
           lopullinen-polku osan-polku]
      (if (nil? polun-osa)
        (get-in-grid (root osa) lopullinen-polku)
        (recur loput-polusta
               (case polun-osa
                 :. lopullinen-polku
                 :.. (vec (butlast lopullinen-polku))
                 :/ []
                 (conj lopullinen-polku polun-osa)))))))

(defn etsi-osa
  ([osa etsittavan-osan-tunniste]
   (g/etsi-osa osa etsittavan-osan-tunniste))
  ([osa etsittavan-osan-tunniste lapset]
   (g/etsi-osa osa etsittavan-osan-tunniste lapset)))

; - Dataan liittyvät haut
(defn hae-grid [grid haettava-asia]
  {:pre [(satisfies? gp/IGrid grid)
         (keyword? haettava-asia)]}
  (case haettava-asia
    :lapset (gp/lapset grid)
    :koko (gp/koko grid)
    :alueet (gp/alueet grid)
    :parametrit (gp/parametrit grid)
    (throw (js/Error. (str "hae-grid funktiolle ei annettu oikeaa haettava-asia avainta!\n"
                           "Saatiin: " haettava-asia "\n"
                           "Hyväksytyt avaimet: " (apply str (interpose ", " [:lapset :koko :alueet :parametrit])))))))

; - Osaan liittyvät haut

(defn osien-yhteinen-asia [osa haettava-asia]
  (case haettava-asia
    :datan-kasittelija (::g/datan-kasittelija osa)))

; - Soluun liittyvät haut
(defn solun-asia [solu haettava-asia]
  (case haettava-asia
    :tunniste-rajapinnan-dataan (::g/tunniste-rajapinnan-dataan solu)
    :osan-derefable (::g/osan-derefable solu)))

;; MUTAATIOT

; - Grid mutatiot
(defn pre-walk-grid! [grid f!]
  (g/pre-walk-grid! grid f!))

(defn post-walk-grid! [grid f!]
  (g/post-walk-grid! grid f!))

(defn lisaa-rivi!
  ([grid solu] (gp/lisaa-rivi! grid solu))
  ([grid solu index] (gp/lisaa-rivi! grid solu index)))

(defn lisaa-sarake!
  ([grid solu] (gp/lisaa-sarake! grid solu))
  ([grid solu index] (gp/lisaa-sarake! grid solu index)))

(defn paivita-root! [osa f]
  (g/paivita-root! osa f))

(defn paivita-grid! [grid paivitettava-asia f]
  {:pre [(satisfies? gp/IGrid grid)
         (keyword? paivitettava-asia)
         (fn? f)]}
  (case paivitettava-asia
    :lapset (gp/paivita-lapset! grid f)
    :koko (gp/paivita-koko! grid f)
    :alueet (gp/paivita-alueet! grid f)
    :parametrit (gp/paivita-parametrit! grid f)
    (throw (js/Error. (str "paivita-grid! funktiolle ei annettu oikeaa paivitettava-asia avainta!\n"
                           "Saatiin: " paivitettava-asia "\n"
                           "Hyväksytyt avaimet: " (apply str (interpose ", " [:lapset :koko :alueet :parametrit])))))))

(defn aseta-grid! [grid paivitettava-asia uusi-arvo]
  {:pre [(satisfies? gp/IGrid grid)
         (keyword? paivitettava-asia)]}
  (case paivitettava-asia
    :lapset (gp/aseta-lapset! grid uusi-arvo)
    :koko (gp/aseta-koko! grid uusi-arvo)
    :alueet (gp/aseta-alueet! grid uusi-arvo)
    :parametrit (gp/aseta-parametrit! grid uusi-arvo)
    (throw (js/Error. (str "aseta-grid! funktiolle ei annettu oikeaa paivitettava-asia avainta!\n"
                           "Saatiin: " paivitettava-asia "\n"
                           "Hyväksytyt avaimet: " (apply str (interpose ", " [:lapset :koko :alueet :parametrit])))))))

; - Osa mutatiot
(defn vaihda-osa! [vaihdettava-osa vaihto-fn & datan-kasittelyt]
  (apply g/vaihda-osa! vaihdettava-osa vaihto-fn datan-kasittelyt))

(defn paivita-osa! [osa f]
  (g/paivita-osa! osa f))

;; PÄIVITYKSET ILMAN MUTAATIOTA
; - Grid päivitykset
(defn aseta-root-fn [this m]
  (gp/aseta-root-fn this m))
; - Osa päivitykset
(defn aseta-nimi [osa nimi]
  (gop/aseta-nimi osa nimi))

; - Solu päivitykset
(defn lisaa-fmt [solu f]
  (sp/lisaa-fmt solu f))
(defn lisaa-fmt-aktiiviselle [solu f]
  (sp/lisaa-fmt-aktiiviselle solu f))

;; DATAN KÄSITTELIJÄ

(defn rajapinta-grid-yhdistaminen! [grid rajapinta datan-kasittelija grid-kasittelija]
  (gp/rajapinta-grid-yhdistaminen! grid rajapinta datan-kasittelija grid-kasittelija))

(defn rajapinnan-kuuntelija [kasittelija rajapinnan-nimi]
  (dk/rajapinnan-kuuntelija kasittelija rajapinnan-nimi))

(defn poista-seurannat! [kasittelija]
  (dk/poista-seurannat! kasittelija))

(defn triggeroi-seuranta! [kasittelija seurannan-nimi]
  (dk/triggeroi-seuranta! kasittelija seurannan-nimi))

(defn lopeta-tilan-kuuntelu! [kasittelija]
  (dk/lopeta-tilan-kuuntelu! kasittelija))

(defn aseta-rajapinnan-data! [kasittelija rajapinta & args]
  (apply dk/aseta-rajapinnan-data! kasittelija rajapinta args))

;; HELPPERIT

(defn piillota! [grid]
  (gop/piillota! grid))

(defn nayta! [grid]
  (gop/nayta! grid))

(defn piillotettu? [grid]
  (gop/piillotettu? grid))

;; PIIRRA

(defn piirra [osa]
  (gop/piirra osa))

