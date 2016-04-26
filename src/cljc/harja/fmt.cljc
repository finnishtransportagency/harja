(ns harja.fmt
  "Yleisiä apureita erityyppisen datan formatointiin."
  (:require [harja.pvm :as pvm]
            #?(:cljs [goog.i18n.currencyCodeMap])
            #?(:cljs [goog.i18n.NumberFormatSymbols])
            #?(:cljs [goog.i18n.NumberFormatSymbols_fi_FI])
            #?(:cljs [goog.i18n.NumberFormat])
            [clojure.string :as str])
  #?(:clj (:import (java.text NumberFormat))))

#?(:cljs
   (set! goog.i18n.NumberFormatSymbols goog.i18n.NumberFormatSymbols_fi_FI))

#?(:cljs
   (def euro-number-format (doto (goog.i18n.NumberFormat. (.-DECIMAL goog.i18n.NumberFormat/Format))
                             (.setShowTrailingZeros false)
                             (.setMinimumFractionDigits 2)
                             (.setMaximumFractionDigits 2))))

(defn euro
  "Formatoi summan euroina näyttämistä varten. Tuhaterottimien ja valinnaisen euromerkin kanssa."
  ([eur] (euro true eur))
  ([nayta-euromerkki eur]
  #?(:cljs
     ;; NOTE: lisätään itse perään euro symboli, koska googlella oli jotain ihan sotkua.
     ;; Käytetään googlen formatointia, koska toLocaleString tukee tarvittavia optioita, mutta
     ;; vasta IE11 versiosta lähtien.
     (str (.format euro-number-format eur) " \u20AC")

     :clj
     (.format (doto
                (if nayta-euromerkki
                  (NumberFormat/getCurrencyInstance)
                  (NumberFormat/getNumberInstance))
                (.setMaximumFractionDigits 2)
                (.setMinimumFractionDigits 2)) eur))))



(defn euro-opt
  "Formatoi euromäärän tai tyhjä, jos nil."
  ([summa] (euro-opt true summa))
  ([nayta-euromerkki summa]
   (if summa
     (euro nayta-euromerkki summa)
     "")))

(def roomalaisena-numerona {1 "I"
                            2 "II"
                            3 "III"})

(defn euro-indeksikorotus
  "Formatoi euromäärän tai stringin Indeksi puuttuu, jos nil."
  [summa]
  (if summa
    (euro summa)
    "Indeksi puuttuu"))


(defn euro-ei-voitu-laskea
  "Formatoi euromäärän tai sanoo ei voitu laskea, jos nil."
  [summa]
  (if summa
    (euro false summa)
    "Ei voitu laskea"))

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

#?(:cljs
   (def desimaali-fmt
     (into {}
           (zipmap (range 1 4)
                   (map #(doto (goog.i18n.NumberFormat.
                                (.-DECIMAL goog.i18n.NumberFormat/Format))
                           (.setShowTrailingZeros false)
                           (.setMinimumFractionDigits %)
                           (.setMaximumFractionDigits %))
                        (range 1 4))))))

#?(:clj (def desimaali-symbolit
          (doto (java.text.DecimalFormatSymbols.)
            (.setGroupingSeparator \ ))))

(defn desimaaliluku
  ([luku] (desimaaliluku luku 2 false))
  ([luku tarkkuus] (desimaaliluku luku tarkkuus false))
  ([luku tarkkuus ryhmitelty?]
   #?(:cljs
      (let [formatoitu (.format (desimaali-fmt tarkkuus) luku)]
        (if-not ryhmitelty?
          (str/replace formatoitu #" " "")
          formatoitu))
      :clj
      (.format (doto (java.text.DecimalFormat.)
                 (.setDecimalFormatSymbols desimaali-symbolit)
                 (.setMinimumFractionDigits tarkkuus)
                 (.setMaximumFractionDigits tarkkuus)
                 (.setGroupingSize (if ryhmitelty? 3 0)))

               (double luku)))))

(defn desimaaliluku-opt
  ([luku] (desimaaliluku-opt luku 2 false))
  ([luku tarkkuus] (desimaaliluku-opt luku tarkkuus false))
  ([luku tarkkuus ryhmitelty?]
   (if luku
     (desimaaliluku luku tarkkuus ryhmitelty?)
     "")))

(defn prosentti
  ([luku] (prosentti luku 1))
  ([luku tarkkuus]
   (str (desimaaliluku luku tarkkuus) "%")))

(defn prosentti-opt
  ([luku] (prosentti-opt luku 1))
  ([luku tarkkuus]
   (if luku
     (prosentti luku tarkkuus)
     "")))

(defn trimmaa-puhelinnumero
  "Ottaa suomalaisen puhelinnumeron teksimuodossa ja palauttaa sen yksinkertaistetussa numeromuodossa ilman etuliitettä
  Esim. +358400-123-456 -> 0400123456
        +358500123123 -> 0500123123
        0400-123123 -> 0400123123"
  [numero-string]
  (let [puhdas-numero (apply str (filter
                                   #(#{\0, \1, \2, \3, \4, \5, \6, \7, \8, \9, \+} %)
                                   numero-string))
        siivottu-etuliite (if (= (str (first puhdas-numero)) "+")
                            (str "0" (subs puhdas-numero 4 (count puhdas-numero)))
                            puhdas-numero)]
    siivottu-etuliite))

(defn pituus [metria]
  (if (< metria 1000)
    (str (desimaaliluku metria 0) " m")
    (str (desimaaliluku (/ metria 1000.0) 2) " km")))

(defn pituus-opt [metria]
  (if (nil? metria)
    ""
    (pituus metria)))

(defn luku-indeksikorotus
  "Formatoi luvun ilman yksikköä tai stringin Indeksi puuttuu, jos nil."
  [summa]
  (if summa
    (euro false summa)
    "Indeksi puuttuu"))
