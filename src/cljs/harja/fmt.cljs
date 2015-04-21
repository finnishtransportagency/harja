(ns harja.fmt
  "Yleisiä apureita erityyppisen datan formatointiin.")

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


  
