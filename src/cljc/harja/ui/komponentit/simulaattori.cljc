(ns harja.ui.komponentit.simulaattori
  "Deklaratiivisen komponenttipuun pieni ja turvallinen validointikerros.

  Simulaattori hyväksyy vain rekisteröityjä komponentteja ja niiden dokumentoituja
  vanhempi-slot-suhteita. Se ei suorita agentin tuottamaa koodia."
  (:require [harja.ui.komponentit.rekisteri :as rekisteri]))

(def ^:private suojatut-asetukset
  "Renderer antaa nämä avaimet itse, joten syöte ei saa asettaa niitä."
  #{:id :arvo :vaihda-fn})

(defn komponentin-kuvaus [id]
  (rekisteri/hae id))

(defn- lapset [solmu]
  (or (:lapset solmu) []))

(defn- tagattu? [x]
  #?(:clj (instance? clojure.lang.TaggedLiteral x)
     :cljs false))

(defn- kielletty-tyyppi [x]
  (cond
    (fn? x) "funktio"
    (symbol? x) "symboli"
    (tagattu? x) "tagattu literaali"))

(defn- asetusvirheet [id kuvaus asetukset]
  (let [nimi (name id)]
    (for [[avain arvo] asetukset
          virhe (concat
                  (cond
                    (contains? suojatut-asetukset avain)
                    [(str nimi ": suojattu asetus " avain " ei ole sallittu")]

                    (not (contains? (:asetukset kuvaus) avain))
                    [(str nimi ": tuntematon asetus " avain)])
                  (for [osa (tree-seq coll? seq arvo)
                        :let [tyyppi (kielletty-tyyppi osa)]
                        :when tyyppi]
                    (str nimi ": asetus " avain " on " tyyppi ", ei sallittu")))]
      virhe)))

(defn- tarkista-solmu [solmu vanhempi]
  (let [id (:komponentti solmu)
        kuvaus (komponentin-kuvaus id)
        sallitut (or (:sallitut-vanhemmat kuvaus) #{})]
    (concat
      (when-not kuvaus
        [(str "Tuntematon komponentti: " id)])
      (when (and kuvaus (not (contains? sallitut vanhempi)))
        [(str id ": vanhempi=" (pr-str vanhempi) " ei ole sallittu")])
      (when kuvaus
        (asetusvirheet id kuvaus (:asetukset solmu)))
      (mapcat #(tarkista-solmu % id) (lapset solmu)))))

(defn virheet
  "Palauttaa deklaratiivisen sivupuun virheet tekstinä."
  [puu]
  (vec (mapcat #(tarkista-solmu % :sivu) puu)))

(defn kelvollinen?
  "Kertoo, voiko deklaratiivisen puun antaa rendererille."
  [puu]
  (empty? (virheet puu)))
