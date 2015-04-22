(ns harja.fmt
  "Yleisiä apureita erityyppisen datan formatointiin."
  (:require [harja.pvm :as pvm]))

(defn euro
  "Formatoi summan euroina näyttämistä varten. Tuhaterottimien ja euromerkin kanssa."
  [summa]
  (str (.toLocaleString summa) " \u20AC"))

(defn euro-opt
  "Formatoi euromäärän tai tyhjä, jos nil."
  [summa]
  (if summa
    (euro summa)
    ""))

(defn kayttaja
  "Formatoi käyttäjän nimen."
  [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn kayttaja-opt
  "Formatoi käyttäjän nimen tai tyhjä, jos nil."
  [k]
  (if k
    (kayttaja k)
    ""))

(defn pvm
  "Formatoi päivämärään"
  [pvm]
  (pvm/pvm pvm))

(defn pvm-opt
  "Formatoi päivämäärän tai tyhjä, jos nil."
  [p]
  (if p
    (pvm p)
    ""))

    
  
