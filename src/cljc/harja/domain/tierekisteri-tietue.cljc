(ns harja.domain.tierekisteri-tietue
  (:require [clojure.string :as str]
    #?@(:cljs [[harja.loki :refer [log]]])
            [harja.tyokalut.merkkijono :as merkkijono]))

(defn jarjesta-ja-suodata-tietolajin-kuvaus [tietolajin-kuvaus]
  (sort-by :jarjestysnumero (filter :jarjestysnumero (:ominaisuudet tietolajin-kuvaus))))

(defn heita-poikkeus [tietolaji virhe]
  (let [viesti (str "Virhe tietolajin " tietolaji " arvojen käsittelyssä: " virhe)]
    (throw (Exception. viesti))))

(defn validoi-arvo [tietolaji {:keys [kenttatunniste pakollinen pituus]} arvo]

  (when (and pakollinen (not arvo))
    (heita-poikkeus tietolaji (str "Pakollinen arvo puuttuu kentästä: " kenttatunniste)))
  (when (< pituus (count arvo))
    (heita-poikkeus tietolaji (str "Liian pitkä arvo kentässä: " kenttatunniste " maksimipituus: " pituus))))

(defn muodosta-kentta [tietolaji {:keys [pituus] :as kentan-kuvaus} arvot]
  (let [kenttatunniste (:kenttatunniste kentan-kuvaus)
        arvo (:arvo (first (filter #(= kenttatunniste (:avain %)) arvot)))]
    (validoi-arvo tietolaji kentan-kuvaus arvo)
    (merkkijono/tayta-oikealle pituus arvo)))

(defn muodosta-arvot [tietolajin-kuvaus arvot]
  (let [tietolaji (:tunniste tietolajin-kuvaus)
        kentat (jarjesta-ja-suodata-tietolajin-kuvaus tietolajin-kuvaus)]
    (str/join (mapv #(muodosta-kentta tietolaji % arvot) kentat))))

(defn pura-arvot [tietolajin-kuvaus arvot])

