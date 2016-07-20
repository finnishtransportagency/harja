(ns harja.domain.tierekisterin-tietolajin-kuvauksen-kasittely
  "Muntaa tierekisterin tietolajin arvot string-merkkijonosta
   Clojure-mapiksi ja päinvastoin."
  (:require [clojure.string :as str]
    #?@(:cljs [[harja.loki :refer [log]]]
        :clj  [
            [taoensso.timbre :as log]])
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.pvm :as pvm]))

(defn- jarjesta-ja-suodata-tietolajin-kuvaus [tietolajin-kuvaus]
  (sort-by :jarjestysnumero (filter :jarjestysnumero (:ominaisuudet tietolajin-kuvaus))))

(defn- heita-poikkeus [tietolaji virhe]
  (let [viesti (str "Virhe tietolajin " tietolaji " arvojen käsittelyssä: " virhe)]
    (throw (Exception. viesti))))

(defn validoi-arvo
  "Validoi, että annettu arvo täyttää kentän kuvauksen vaatimukset."
  [arvo {:keys [kenttatunniste pakollinen pituus] :as kentan-kuvaus} tietolaji]
  (log/debug "Validoidaan arvo " (pr-str arvo) " kentän kuvauksella: " (pr-str kentan-kuvaus))
  ;; TODO tyyppivalidointi vielä numerolle ja pvm:lle
  (when (and pakollinen (not arvo))
    (heita-poikkeus tietolaji (str "Pakollinen arvo puuttuu kentästä: " kenttatunniste)))
  (when (< pituus (count arvo))
    (heita-poikkeus tietolaji (str "Liian pitkä arvo kentässä: " kenttatunniste " maksimipituus: " pituus))))

(defn validoi-tietolajin-arvot
  "Tarkistaa, että tietolajin arvot on annettu oikein tietolajin kuvauksen mukaisesti.
   Jos arvoissa on ongelma, heittää poikkeuksen. Jos arvot ovat ok, palauttaa nil."
  [tietolaji arvot tietolajin-kuvaus]
  (let [kenttien-kuvaukset (sort-by :jarjestysnumero (:ominaisuudet tietolajin-kuvaus))
        ylimaaraiset-kentat (filter ;; TODO Set difference
                              (fn [arvo]
                                (not (some? (first (filter
                                                     (fn [kentan-kuvaus]
                                                       (= (:kenttatunniste kentan-kuvaus) arvo))
                                                     kenttien-kuvaukset)))))
                              (keys arvot))]
    (when-not (empty? ylimaaraiset-kentat)
      (throw (Exception. "Tietolajin arvoissa on ylimääräisiä kenttiä,
       joita ei löydy tierekisterin tietolajin kuvauksesta: " (str/join ", " ylimaaraiset-kentat))))

    ;; Eli ylimääräisiä kenttiä, validoi annetut kentät
    (doseq [kentan-kuvaus kenttien-kuvaukset]
      (validoi-arvo (clojure.walk/stringify-keys (get arvot (:kenttatunniste kentan-kuvaus)))
                                 kentan-kuvaus
                                 tietolaji))))

(defn- muunna-teksti-kentan-mukaiseen-tyyppiin [arvo-tekstina kentan-kuvaus]
  (condp = (:tietotyyppi kentan-kuvaus)
    :merkkijono arvo-tekstina
    :numeerinen (do (merkkijono/vaadi-kokonaisluku arvo-tekstina)
                    (Integer/parseInt arvo-tekstina))
    :paivamaara (do (merkkijono/vaadi-iso-8601-paivamaara arvo-tekstina)
                    (pvm/iso-8601->pvm arvo-tekstina))
    :koodisto arvo-tekstina))

(defn- hae-arvo
  "Ottaa arvot-stringin ja etsii sieltä halutun arvon käyttäen apuna kenttien-kuvaukset -mappia.
   Palauttaa arvon castattuna oikeaan tietotyyppiin."
  [arvot-merkkijono kenttien-kuvaukset jarjestysnumero]
  (let [jarjestysnumeron-kentta (first (filter #(= (:jarjestysnumero %) jarjestysnumero)
                                               kenttien-kuvaukset))
        alkuindeksi (apply +
                           (map :pituus
                                (filter #(< (:jarjestysnumero %) jarjestysnumero)
                                        kenttien-kuvaukset)))
        loppuindeksi (+ alkuindeksi (:pituus jarjestysnumeron-kentta))
        ;; TODO Require string ns
        arvo-teksti (clojure.string/trim (subs arvot-merkkijono alkuindeksi loppuindeksi))]
    arvo-teksti))

(defn- muunna-kentta-stringiksi [arvo kentan-kuvaus]
  (condp = (:tietotyyppi kentan-kuvaus)
    :merkkijono arvo
    :numeerinen (str arvo)
    :paivamaara (pvm/pvm->iso-8601 arvo)
    :koodisto arvo))

(defn- muodosta-kentta [tietolaji arvot-map {:keys [pituus kenttatunniste] :as kentan-kuvaus}]
  (let [arvo (get arvot-map kenttatunniste)]
    (validoi-arvo arvo kentan-kuvaus tietolaji)
    (merkkijono/tayta-oikealle pituus arvo)))

(defn tietolajin-arvot-map->string
  "Ottaa arvot-mapin ja purkaa sen stringiksi käyttäen apuna annettua tietolajin kuvausta.
  Tietolajin kuvaus on tierekisterin palauttama kuvaus tietolajista, muunnettuna Clojure-mapiksi."
  [arvot-map tietolajin-kuvaus]
  (let [tietolaji (:tunniste tietolajin-kuvaus)
        kenttien-kuvaukset (jarjesta-ja-suodata-tietolajin-kuvaus tietolajin-kuvaus)
        string-osat (map (partial muodosta-kentta tietolaji arvot-map) kenttien-kuvaukset)]
    (str/join string-osat)))

(defn- pura-kentta [arvot-merkkijono
                    tietolaji
                    kenttien-kuvaukset
                    {:keys [jarjestysnumero kenttatunniste] :as kentan-kuvaus}]
  (let [arvo (hae-arvo arvot-merkkijono kenttien-kuvaukset jarjestysnumero)]
    (validoi-arvo arvo kentan-kuvaus tietolaji)
    {kenttatunniste arvo}))

(defn tietolajin-arvot-merkkijono->map
  "Ottaa arvot-stringin ja purkaa sen mapiksi käyttäen apuna annettua tietolajin kuvausta.
  Tietolajin kuvaus on tierekisterin palauttama kuvaus tietolajista, muunnettuna Clojure-mapiksi."
  [arvot-merkkijono tietolajin-kuvaus]
  (let [tietolaji (:tunniste tietolajin-kuvaus)
        kenttien-kuvaukset (jarjesta-ja-suodata-tietolajin-kuvaus tietolajin-kuvaus)
        map-osat (mapv
                   (partial pura-kentta arvot-merkkijono tietolaji kenttien-kuvaukset)
                   kenttien-kuvaukset)]
    (reduce merge map-osat)))


