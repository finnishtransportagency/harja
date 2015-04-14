(ns harja.palvelin.oikeudet
  "Kaikki palvelinpuolen käyttöoikeustarkistukset."
  (:require [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]))


(def rooli-jarjestelmavastuuhenkilo          "jarjestelmavastuuhenkilo")
(def rooli-tilaajan-kayttaja                 "tilaajan kayttaja")
(def rooli-urakanvalvoja                     "urakanvalvoja")
;;(def rooli-vaylamuodon-vastuuhenkilo         "vaylamuodon vastuuhenkilo")
(def rooli-hallintayksikon-vastuuhenkilo     "hallintayksikon vastuuhenkilo")
(def rooli-liikennepaivystaja                "liikennepäivystäjä")
(def rooli-tilaajan-asiantuntija             "tilaajan asiantuntija")
(def rooli-tilaajan-laadunvalvontakonsultti  "tilaajan laadunvalvontakonsultti")
(def rooli-urakoitsijan-paakayttaja          "urakoitsijan paakayttaja")
(def rooli-urakoitsijan-urakan-vastuuhenkilo "urakoitsijan urakan vastuuhenkilo")
(def rooli-urakoitsijan-kayttaja             "urakoitsijan kayttaja")
(def rooli-urakoitsijan-laatuvastaava        "urakoitsijan laatuvastaava")

(defn roolissa?
  "Tarkistaa onko käyttäjällä tietty rooli. Rooli voi olla joko yksittäinen rooli
tai setti rooleja. Jos annetaan setti, tarkistetaan onko käyttäjällä joku annetuista
rooleista."
  [kayttaja rooli]
  ;; Järjestelmän vastuuhenkilöllä on kaikki roolit eli saa tehdä kaiken
  (if (contains? (:roolit kayttaja) rooli-jarjestelmavastuuhenkilo)
    true
    (if (some (if (set? rooli)
                rooli
                #{rooli}) (:roolit kayttaja))
      true
      false)))

(defn vaadi-rooli
    [kayttaja rooli]
  (when-not (roolissa? kayttaja rooli)
    (let [viesti (format "Käyttäjällä '%1$s' ei vaadittua roolia '%2$s'", (:kayttajanimi kayttaja) rooli)]
    (log/warn viesti)
        (throw (RuntimeException. viesti)))))

(defn rooli-urakassa?
  "Tarkistaa onko käyttäjällä tietty rooli urakassa."
  [kayttaja rooli urakka-id]
  (if (roolissa? kayttaja rooli-jarjestelmavastuuhenkilo)
    true
    (if-let [urakkaroolit (some->> (:urakkaroolit kayttaja)
                                   (filter #(= (:id (:urakka %)) urakka-id))
                                   (map :rooli) 
                                   (into #{}))]
      (if (urakkaroolit rooli)
        true
        false)
      false)))


(defn vaadi-rooli-urakassa
  [kayttaja rooli urakka-id]
  (when-not (rooli-urakassa? kayttaja rooli urakka-id)
    (let [viesti (format "Käyttäjällä '%1$s' ei vaadittua roolia '%2$s' urakassa jonka id on %3$s", (:kayttajanimi kayttaja) rooli urakka-id)]
    (log/warn viesti)
        (throw (RuntimeException. viesti)))))

(defn tilaajan-kayttaja?
  [kayttaja]
  (roolissa? kayttaja 
             #{rooli-jarjestelmavastuuhenkilo
               rooli-tilaajan-kayttaja
               rooli-urakanvalvoja
               rooli-hallintayksikon-vastuuhenkilo
               rooli-liikennepaivystaja
               rooli-tilaajan-asiantuntija
               rooli-tilaajan-laadunvalvontakonsultti}))

(defn lukuoikeus-urakassa?
  [kayttaja urakka-id]
  (or (tilaajan-kayttaja? kayttaja)
      (rooli-urakassa? kayttaja rooli-urakoitsijan-paakayttaja urakka-id)
      (rooli-urakassa? kayttaja rooli-urakoitsijan-urakan-vastuuhenkilo urakka-id)))

(defn vaadi-lukuoikeus-urakkaan
  [kayttaja urakka-id]
  (when-not (lukuoikeus-urakassa? kayttaja urakka-id)
    (let [viesti (format "Käyttäjällä '%1$s' ei lukuoikeutta urakassa jonka id on %2$s", (:kayttajanimi kayttaja) urakka-id)]
    (log/warn viesti)
        (throw (RuntimeException. viesti))))
  )
