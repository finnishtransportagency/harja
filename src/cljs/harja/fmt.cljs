(ns harja.fmt
  "Yleisiä apureita erityyppisen datan formatointiin."
  (:require [harja.pvm :as pvm]
            [goog.i18n.currencyCodeMap]
            [goog.i18n.NumberFormatSymbols]
            [goog.i18n.NumberFormatSymbols_fi_FI]
            [goog.i18n.NumberFormat]))

(set! goog.i18n.NumberFormatSymbols goog.i18n.NumberFormatSymbols_fi_FI)

(def euro-number-format (doto (goog.i18n.NumberFormat. (.-DECIMAL goog.i18n.NumberFormat/Format))
                          (.setShowTrailingZeros false)
                          (.setMinimumFractionDigits 2)
                          (.setMaximumFractionDigits 2)))

(defn euro [eur]
  "Formatoi summan euroina näyttämistä varten. Tuhaterottimien ja euromerkin kanssa."
  ;; NOTE: lisätään itse perään euro symboli, koska googlella oli jotain ihan sotkua.
  ;; Käytetään googlen formatointia, koska toLocaleString tukee tarvittavia optioita, mutta
  ;; vasta IE11 versiosta lähtien. 
  (str (.format euro-number-format eur) " \u20AC"))


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

    
  
