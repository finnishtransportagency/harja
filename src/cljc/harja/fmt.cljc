(ns harja.fmt
  "Yleisiä apureita erityyppisen datan formatointiin."
  (:require [harja.pvm :as pvm]
            #?(:cljs [goog.i18n.currencyCodeMap])
            #?(:cljs [goog.i18n.NumberFormatSymbols])
            #?(:cljs [goog.i18n.NumberFormatSymbols_fi_FI])
            #?(:cljs [goog.i18n.NumberFormat]))
  #?(:clj (:import (java.text NumberFormat))))

#?(:cljs
   (set! goog.i18n.NumberFormatSymbols goog.i18n.NumberFormatSymbols_fi_FI))

#?(:cljs
   (def euro-number-format (doto (goog.i18n.NumberFormat. (.-DECIMAL goog.i18n.NumberFormat/Format))
                             (.setShowTrailingZeros false)
                             (.setMinimumFractionDigits 2)
                             (.setMaximumFractionDigits 2))))

(defn euro [eur]
  "Formatoi summan euroina näyttämistä varten. Tuhaterottimien ja euromerkin kanssa."
  #?(:cljs
     ;; NOTE: lisätään itse perään euro symboli, koska googlella oli jotain ihan sotkua.
     ;; Käytetään googlen formatointia, koska toLocaleString tukee tarvittavia optioita, mutta
     ;; vasta IE11 versiosta lähtien. 
     (str (.format euro-number-format eur) " \u20AC")

     :clj
     (.format (NumberFormat/getCurrencyInstance) eur)))
   

(defn euro-opt
  "Formatoi euromäärän tai tyhjä, jos nil."
  [summa]
  (if summa
    (euro summa)
    ""))

(defn euro-indeksikorotus
  "Formatoi euromäärän tai tyhjä, jos nil."
  [summa]
  (if summa
    (euro summa)
    "Indeksi puuttuu"))

(defn pikseleina
  [arvo]
  (str arvo "px"))

(defn asteina [arvo]
  (str arvo " \u2103"))

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


(defn pvm-vali [[alku loppu]]
  (str (pvm/pvm alku)
       " \u2014 "
       (pvm/pvm loppu)))

(defn pvm-vali-opt [vali]
  (if vali
    (pvm-vali vali)
    ""))
