(ns harja.ui.komponentit.simulaattori
  "Deklaratiivisen komponenttipuun pieni ja turvallinen validointikerros.

  Simulaattori hyväksyy vain rekisteröityjä komponentteja ja niiden dokumentoituja
  vanhempi-slot-suhteita. Se ei suorita agentin tuottamaa koodia."
  (:require [harja.ui.komponentit.rekisteri :as rekisteri]))

(defn komponentin-kuvaus [id]
  (rekisteri/hae id))

(defn- lapset [solmu]
  (or (:lapset solmu) []))

(defn- tarkista-solmu [solmu vanhempi]
  (let [id (:komponentti solmu)
        kuvaus (komponentin-kuvaus id)
        sallitut (or (:sallitut-vanhemmat kuvaus) #{})]
    (concat
      (when-not kuvaus
        [(str "Tuntematon komponentti: " id)])
      (when (and kuvaus (not (contains? sallitut vanhempi)))
        [(str id ": vanhempi=" (pr-str vanhempi) " ei ole sallittu")])
      (mapcat #(tarkista-solmu % id) (lapset solmu)))))

(defn virheet
  "Palauttaa deklaratiivisen sivupuun virheet tekstinä."
  [puu]
  (vec (mapcat #(tarkista-solmu % :sivu) puu)))

(defn kelvollinen?
  "Kertoo, voiko deklaratiivisen puun antaa rendererille."
  [puu]
  (empty? (virheet puu)))