(ns harja.tyokalut.big
  "Työkaluja abitrary precision desimaalilukujen laskentaan.
  Valitettavasti java.math.BigDecimal on transit kerroksessa muunnettu JS numeroksi, koska yleensä
  ei tarvita big decimal laskentaa.

  Tämän takia on wrapper record ja molemmilla puolilla toteutettu operaatiot bigdec
  käsittelyyn.

  Käytä funktiota `->big` kun haluat muuntaa merkkijonon tai normaalin numeron tähän
  muotoon ja `unwrap` (clj puolella) palauttaaksesi käärityn `java.math.BigDecimal`
  instanssin esim. tietokantaan tallentamista varten."

  (:refer-clojure :exclude [+ - * /])
  #?(:clj (:import java.math.BigDecimal
                   java.math.RoundingMode))
  (:require [clojure.string :as str]
            #?@(:cljs [[cljsjs.big]
                       [harja.tyokalut.yleiset :as yleiset]])))


(defprotocol BigDecOps
  (plus [b1 b2])
  (minus [b1 b2])
  (mul [b1 b2])
  (div [b1 b2])
  (div-decimal [b1 b2 decimals])
  (eq [b1 b2] "True, jos arvot yhtäsuuret")
  (lt [b1 b2] "True, jos b1 pienenmpi kuin b2")
  (gt [b1 b2] "True, jos b1 suurempi kuin b2")
  (lte [b1 b2] "True, jos b1 pienempi tai yhtäsuuri kuin b2")
  (gte [b1 b2] "True, jos b1 suurempi tai yhäsuuri kuin b2")
  (fmt [b1 decimals] "Formatoi annettuun desimaalitarkkuuten, poistaen loppunollat.")
  (fmt-full [b1 decimals] "Formatoi annetuun desimaalitarkkuuteen."))

(defrecord BigDec [b])

(defn big? [x]
  (instance? BigDec x))

(extend-protocol BigDecOps
  BigDec
  (plus [{b1 :b} {b2 :b}]
    (->BigDec (#?(:clj .add
                  :cljs .plus) b1 b2)))

  (minus [{b1 :b} {b2 :b}]
    (->BigDec (#?(:clj .subtract
                  :cljs .minus) b1 b2)))

  (mul [{b1 :b} {b2 :b}]
    (->BigDec (#?(:clj .multiply
                  :cljs .times) b1 b2)))

  (div [{b1 :b} {b2 :b}]
    (->BigDec (#?(:clj .divide
                  :cljs .div) b1 b2)))

  (div-decimal [{b1 :b} {b2 :b} decimals]
    #?(:clj (->BigDec (.divide b1 b2 decimals RoundingMode/HALF_UP))
       :cljs (->BigDec (yleiset/round2 decimals (.div b1 b2)))))

  (eq [{b1 :b} {b2 :b}]
    #?(:clj (= b1 b2)
       :cljs (.eq b1 b2)))

  (lt [{b1 :b} {b2 :b}] #?(:clj (< b1 b2) :cljs (.lt b1 b2)))
  (gt [{b1 :b} {b2 :b}] #?(:clj (> b1 b2) :cljs (.gt b1 b2)))
  (lte [{b1 :b} {b2 :b}] #?(:clj (<= b1 b2) :cljs (.lte b1 b2)))
  (gte [{b1 :b} {b2 :b}] #?(:clj (>= b1 b2) :cljs (.gte b1 b2)))

  (fmt [{b :b} decimals]
    #?(:clj
       (.format (doto (java.text.DecimalFormat.)
                  (.setMaximumFractionDigits decimals)
                  (.setMinimumFractionDigits 0))
                b)
       :cljs
       (-> (.toFixed b decimals)
           (str/replace #"\.0+$" "")
           (str/replace #"(\.[^0]+)([0]+)" second)
           (str/replace #"\." ","))))

  (fmt-full [{b :b} decimals]
    #?(:clj (.format (doto (java.text.DecimalFormat.)
                       (.setMaximumFractionDigits decimals)
                       (.setMinimumFractionDigits decimals))
                     b)
       :cljs
       (-> b (.toFixed decimals) (str/replace #"\." ",")))))

(defn ->big [arvo]
  (if (big? arvo)
    arvo
    (try
      (->BigDec #?(:clj (if (instance? java.math.BigDecimal arvo)
                          arvo
                          (java.math.BigDecimal. arvo))
                   :cljs (js/Big. arvo)))
      (catch #?(:clj Exception
                :cljs js/Error) e
          nil))))

(defn parse
  "Muuttaa merkkijonon big instanssiksi. Palauttaa nil, jos merkkijono ei kelpaa."
  [string]
  (-> string
      (str/replace #"," ".")
      (->big)))

#?(:clj
   (defn unwrap
     "Jos annettu numero on BigDec, palauttaa sen sisäisen BigDecimal numeron.
  Muussa tapauksessa palauttaa annetun parametrin sellaisenaan."
     [b]
     (if (big? b)
       (:b b)
       b)))
