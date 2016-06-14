(ns harja.fmt
  "Yleisiä apureita erityyppisen datan formatointiin."
  (:require [harja.pvm :as pvm]
            #?(:cljs [goog.i18n.currencyCodeMap])
            #?(:cljs [goog.i18n.NumberFormatSymbols])
            #?(:cljs [goog.i18n.NumberFormatSymbols_fi_FI])
            #?(:cljs [goog.i18n.NumberFormat])
            [clojure.string :as str])
  #?(:clj
     (:import (java.text NumberFormat)
              (java.util Locale))))

#?(:clj
   (Locale/setDefault (Locale. "fi" "FI")))

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

(defn lyhenna-keskelta
  "Lyhentää tekstijonon haluttuun pituuteen siten, että
  pituutta otetaan pois keskeltä, ja korvataan kahdella pisteellä .."
  [haluttu-pituus teksti]
  (if (>= haluttu-pituus (count teksti))
    teksti

    (let [patkat (split-at (/ (count teksti) 2) teksti)
          eka (apply str (first patkat))
          ;; Ekan pituus pyöristetään ylöspäin, tokan alaspäin
          eka-haluttu-pituus (int (Math/ceil (/ haluttu-pituus 2)))
          toka (apply str (second patkat))
          toka-haluttu-pituus (int (Math/floor (/ haluttu-pituus 2)))]
      (str
        ;; Otetaan haluttu pituus -1, jotta pisteet mahtuu mukaan
        (apply str (take (dec eka-haluttu-pituus) eka))
        ".."
        (apply str (take-last (dec toka-haluttu-pituus) toka))))))

(def urakan-nimen-oletuspituus 30)

(defn lyhennetty-urakan-nimi
  "Lyhentää urakan nimen haluttuun pituuteen, lyhentämällä
  aluksi tiettyjä sanoja (esim urakka -> ur.), ja jos nämä eivät
  auta, leikkaamalla keskeltä kirjaimia pois ja korvaamalla leikatut
  kirjaimet kahdella pisteellä .."
  ([nimi] (lyhennetty-urakan-nimi nimi urakan-nimen-oletuspituus))
  ([nimi pituus]
    (loop [nimi nimi]
      (if (>= pituus (count nimi))
        nimi

        ;; Tänne voi lisätä lisää korvattavia asioita
        ;; Päällimmäiseksi yleisemmät korjaukset,
        ;; viimeiseksi "last resort" tyyppiset ratkaisut
        (recur
          (cond
            ;; Ylimääräiset välilyönnit pois
            (re-find #"\s\s+" nimi)
            (str/replace nimi #"\s\s+" " ")

            ;; "  - " -> "-"
            ;; Täytyy etsiä nämä kaksi erikseen, koska
            ;; \s*-\s* osuisi myös korjattuun "-" merkkijonoon,
            ;; ja "\s+-\s+" osuisi vain jos molemmilla puolilla on välilyönti.
            (or (re-find #"\s+-" nimi) (re-find #"-\s+" nimi))
            (str/replace nimi #"\s*-\s*" "-")

            ;; (?i) case insensitive ei toimi str/replacessa
            ;; cljs puolella. Olisi mahdollista käyttää vain
            ;; clj puolella käyttäen reader conditionaleja, mutta
            ;; samapa se on toistaa kaikki näin.
            (re-find #"alueurakka" nimi)
            (str/replace nimi #"alueurakka" "ur.")

            (re-find #"Alueurakka" nimi)
            (str/replace nimi #"Alueurakka" "ur.")

            (re-find #"ALUEURAKKA" nimi)
            (str/replace nimi #"ALUEURAKKA" "ur.")

            (re-find #"urakka" nimi)
            (str/replace nimi #"urakka" "ur.")

            (re-find #"Urakka" nimi)
            (str/replace nimi #"Urakka" "ur.")

            (re-find #"URAKKA" nimi)
            (str/replace nimi #"URAKKA" "ur.")

            (re-find #"kunnossapidon" nimi)
            (str/replace nimi #"kunnossapidon" "kun.pid.")

            (re-find #"Kunnossapidon" nimi)
            (str/replace nimi #"Kunnossapidon" "kun.pid.")

            (re-find #"KUNNOSSAPIDON" nimi)
            (str/replace nimi #"KUNNOSSAPIDON" "kun.pid.")

            ;; ", " -> " "
            (re-find #"\s*,\s*" nimi)
            (str/replace nimi #"\s*,\s*" " ")

            ;; Jos vieläkin liian pitkä, niin lyhennetään kun.pid. entisestään
            (re-find #"kun.pid." nimi)
            (str/replace nimi #"kun.pid." "kp.")

            (re-find #"POP" nimi)
            (str/replace nimi #"POP" "")

            :else (lyhenna-keskelta pituus nimi)))))))

(defn lyhennetty-urakan-nimi-opt
  ([nimi] (lyhennetty-urakan-nimi-opt nimi urakan-nimen-oletuspituus))
  ([nimi pituus]
    (when nimi (lyhennetty-urakan-nimi nimi pituus))))

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
      ; Jostain syystä ei voi formatoida desimaalilukua nollalla desimaalilla. Aiheuttaa poikkeuksen.
      (if (= tarkkuus 0)
        (.toFixed luku 0)
        (let [formatoitu (.format (desimaali-fmt tarkkuus) luku)]
         (if-not ryhmitelty?
           (str/replace formatoitu #" " "")
           formatoitu)))
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

(defn totuus [arvo]
  (if arvo
    "Kyllä"
    "Ei"))
