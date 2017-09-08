(ns harja.tyokalut.big
  "Työkaluja abitrary precision desimaalilukujen laskentaan.
  Valitettavasti java.math.BigDecimal on transit kerroksessa muunnettu JS numeroksi, koska yleensä
  ei tarvita big decimal laskenta.

  Tämän takia clj puolella on wrapper record."
  (:refer-clojure :exclude [+ - * /])
  #?(:clj (:import java.math.BigDecimal))
  (:require [clojure.string :as str]
            #?@(:cljs [[cljsjs.big]])))


(defprotocol BigDecOps
  (plus [b1 b2])
  (minus [b1 b2])
  (mul [b1 b2])
  (div [b1 b2])
  (eq [b1 b2])
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

  (eq [{b1 :b} {b2 :b}]
    #?(:clj (= b1 b2)
       :cljs (.eq b1 b2)))

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
