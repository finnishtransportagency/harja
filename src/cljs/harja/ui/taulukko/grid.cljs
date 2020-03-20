(ns harja.ui.taulukko.grid
  (:require [harja.ui.taulukko.impl.grid :as g]
            [harja.ui.taulukko.impl.alue :as alue]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.protokollat.grid :as gp]
            [harja.ui.taulukko.protokollat.solu :as sp]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]
            [harja.ui.taulukko.impl.datan-kasittely :as dk]
            [reagent.core :as r]))

(declare aseta-root-fn!)

;; KONSTRUKTORIT

; - Grid
(defn grid
  [asetukset]
  {:pre [(g/validi-grid-asetukset? asetukset)]
   :post [(instance? g/Grid %)
          (symbol? (gop/id %))]}
  (g/grid-c g/->Grid asetukset))

(defn dynamic-grid
  [asetukset]
  {:pre [(g/validi-grid-asetukset? asetukset)]
   :post [(instance? g/DynaaminenGrid %)
          (symbol? (gop/id %))]}
  (g/grid-c g/->DynaaminenGrid asetukset))

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
(defn datan-kasittelija [data-atom rajapinta haku-kuvaus asetus-kuvaus seurannat-kuvaus]
  (let [datan-kasittelija (dk/datan-kasittelija data-atom)]
    ;; Ajetaan init TODO johonkin järkevämpään paikkaan tuo init homma API:ssa
    (doseq [[_ {:keys [init]}] seurannat-kuvaus]
      (when (fn? init)
        (swap! data-atom init)))
    (doseq [seurantojen-luonti (filter #(contains? (val %) :luonti) seurannat-kuvaus)]
      (dk/seurannat-lisaaja! datan-kasittelija seurantojen-luonti))
    (doseq [seuranta (remove #(contains? (val %) :luonti) seurannat-kuvaus)]
      (dk/lisaa-seuranta! datan-kasittelija seuranta))
    (doseq [kuuntelija-luonti (filter #(contains? (val %) :luonti) haku-kuvaus)]
      (dk/kuuntelijat-lisaaja! datan-kasittelija rajapinta kuuntelija-luonti))
    (doseq [kuuntelija (remove #(contains? (val %) :luonti) haku-kuvaus)]
      (dk/lisaa-kuuntelija! datan-kasittelija rajapinta kuuntelija))
    (doseq [asettaja asetus-kuvaus]
      (dk/lisaa-asettaja! datan-kasittelija rajapinta asettaja))
    datan-kasittelija))

;; KOPIOINNIT

(defn kopio [osa]
  (gop/kopioi osa))

(defn samanlainen-osa [osa]
  (g/samanlainen-osa osa))

(defn lisaa-uuden-osan-rajapintakasittelijat [osa & gridkasittelijat]
  (apply g/lisaa-uuden-osan-rajapintakasittelijat osa gridkasittelijat))

(defn poista-osan-rajapintakasittelijat [osa]
  (g/poista-osan-rajapintakasittelijat osa))

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
  (let [osan-polku (::g/index-polku osa)]
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

(defn gridin-rivit [grid pred]
  {:pre [(satisfies? gp/IGrid grid)
         (fn? pred)]
   :post [(vector? %)
          (every? (fn [osa] (satisfies? gop/IGridOsa osa)) %)]}
  (g/gridin-osat-vektoriin grid
                           pred
                           identity))

(defn nakyvat-rivit [grid]
  (vec (sort-by ::g/index-polku
                (fn [polku-a polku-b]
                  (let [polun-osat-vertailtu (map (fn [i j]
                                                    (cond
                                                      (= i j) 0
                                                      (< i j) -1
                                                      (> i j) 1))
                                                  polku-a
                                                  polku-b)
                        ensimmainen-eri-arvo (first (drop-while #(= 0 %) polun-osat-vertailtu))]
                    (if (nil? ensimmainen-eri-arvo)
                      (< (count polku-a) (count polku-b))
                      ensimmainen-eri-arvo)))
                (gridin-rivit grid
                              (fn [osa]
                                (and (instance? alue/Rivi osa)
                                     (not (gop/piillotettu? osa))))))))

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

; - Root liittyvät haut

(defn root-asia [root-grid haettava-asia]
  (case haettava-asia
    :lopeta-rajapinnan-kautta-kuuntelu! (::g/lopeta-rajapinnan-kautta-kuuntelu! root-grid)
    :grid-rajapintakasittelijat (::g/grid-rajapintakasittelijat root-grid)
    :tapahtumat (::g/grid-tapahtumat root-grid)))

; - Osaan liittyvät haut

(defn osien-yhteinen-asia [osa haettava-asia]
  (case haettava-asia
    :datan-kasittelija (get-in @g/taulukko-konteksti [(::g/root-id osa) :datan-kasittelija])
    :index-polku (::g/index-polku osa)
    :nimi-polku (::g/nimi-polku osa)))

(defn hae-osa [osa haettava-asia]
  (case haettava-asia
    :nimi (gop/nimi osa)
    :id (gop/id osa)))

; - Soluun liittyvät haut
(defn solun-asia [solu haettava-asia]
  (case haettava-asia
    :tunniste-rajapinnan-dataan (::g/tunniste-rajapinnan-dataan solu)
    :osan-derefable (::g/osan-derefable solu)))

(defn solun-arvo [solu]
  @(solun-asia solu :osan-derefable))

; - Datan käsittelija haut

(defn arvo-rajapinnasta
  ([datan-kasittelija rajapinnan-nimi] (arvo-rajapinnasta datan-kasittelija rajapinnan-nimi false))
  ([datan-kasittelija rajapinnan-nimi meta?]
   (let [arvo (when-let [r (get-in datan-kasittelija [:kuuntelijat rajapinnan-nimi :r])]
                @r)]
     (if meta?
       arvo
       (:data arvo)))))

;; MUTAATIOT

; - Grid mutatiot
(defn pre-walk-grid! [grid f!]
  (g/pre-walk-grid! grid f!))

(defn post-walk-grid! [grid f!]
  (g/post-walk-grid! grid f!))

(defn lisaa-rivi!
  ([grid rivi] (gp/lisaa-rivi! grid rivi))
  ([grid rivi index-polku] (gp/lisaa-rivi! grid rivi index-polku)))

(defn lisaa-sarake!
  ([grid solu] (gp/lisaa-sarake! grid solu))
  ([grid solu index] (gp/lisaa-sarake! grid solu index)))

(defn poista-rivi! [grid rivi]
  (gp/poista-rivi! grid rivi))

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
(defn aseta-root-fn! [this m]
  (gp/aseta-root-fn! this m))
(defn grid-tapahtumat [this data-atom tapahtuma-maaritelmat]
  (gp/grid-tapahtumat this data-atom tapahtuma-maaritelmat))
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

(defn triggeroi-tapahtuma! [osa tapahtuman-nimi]
  (if-let [tapahtuma (get (root-asia (root osa) :tapahtumat) tapahtuman-nimi)]
    (binding [g/*ajetaan-tapahtuma?* true]
      ((:tapahtuma-trigger! tapahtuma)))
    (throw (js/Error. (str "triggeroi-tapahtuma! funktiolle ei annettu oikeaa tapahtuman-nimi avainta!\n"
                           "Saatiin: " tapahtuman-nimi "\n"
                           "Hyväksytyt avaimet: " (apply str (interpose ", " (keys (root-asia (root osa) :tapahtumat)))))))))

;; PREDIKAATIT

(defn rivi? [osa]
  (instance? alue/Rivi osa))

(defn pudotusvalikko? [osa]
  (instance? solu/Pudotusvalikko osa))

;; PIIRRA

(defn piirra [osa]
  (gop/piirra osa))

;; MISC

(defn aseta-gridin-polut
  "Tätä ei tulisi tarvita kutsua erikseen. Kehityksessä voi olla ihan hyödyllinen, kun ei ole vielä liitetty dataa taulukkoon."
  [grid]
  (g/aseta-gridin-polut grid))

