(ns harja.ui.perus.simulaattori
  "Deklaratiivisen komponenttipuun pieni ja turvallinen validointikerros.

  Simulaattori hyväksyy vain rekisteröityjä komponentteja ja niiden dokumentoituja
  vanhempi-slot-suhteita. Se ei suorita agentin tuottamaa koodia."
  (:require [harja.ui.perus.rekisteri :as rekisteri]))

(def ^:private suojatut-asetukset
  "Renderer antaa nämä avaimet itse, joten syöte ei saa asettaa niitä."
  #{:id :arvo :vaihda-fn})

(defn komponentin-kuvaus [id]
  (rekisteri/hae id))

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
  (if-not (map? solmu)
    [(str "Solmun pitää olla map: " (pr-str solmu))]
    (let [{:keys [komponentti asetukset lapset]} solmu
          kuvaus (komponentin-kuvaus komponentti)
          sallitut (or (:sallitut-vanhemmat kuvaus) #{})
          asetukset-kunnossa? (or (nil? asetukset) (map? asetukset))
          lapset-kunnossa? (or (nil? lapset) (sequential? lapset))]
      (concat
        (when-not kuvaus
          [(str "Tuntematon komponentti: " komponentti)])
        (when (and kuvaus (not (contains? sallitut vanhempi)))
          [(str komponentti ": vanhempi=" (pr-str vanhempi) " ei ole sallittu")])
        (when-not asetukset-kunnossa?
          [(str komponentti ": asetukset ei ole map")])
        (when-not lapset-kunnossa?
          [(str komponentti ": lapset ei ole lista")])
        (when (and kuvaus asetukset-kunnossa?)
          (asetusvirheet komponentti kuvaus asetukset))
        (when lapset-kunnossa?
          (mapcat #(tarkista-solmu % komponentti) lapset))))))

(defn virheet
  "Palauttaa deklaratiivisen sivupuun virheet tekstinä."
  [puu]
  (vec (mapcat #(tarkista-solmu % :sivu) puu)))

(defn kelvollinen?
  "Kertoo, voiko deklaratiivisen puun antaa rendererille."
  [puu]
  (empty? (virheet puu)))
