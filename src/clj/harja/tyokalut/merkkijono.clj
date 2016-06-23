(ns harja.tyokalut.merkkijono
  (:import (java.text SimpleDateFormat)
           (java.text ParseException SimpleDateFormat)))

(defn leikkaa [merkkia merkkijonosta]
  (apply str (take merkkia merkkijonosta)))

(defn tayta-oikealle [pituus merkkijono]
  (format (str "%1$-" pituus "s") merkkijono))

(defn onko-kokonaisluku? [arvo]
  (try
    (Integer/parseInt arvo)
    true
    (catch NumberFormatException e false)))

(defn onko-paivamaara? [arvo]
  (try
    (.parse (SimpleDateFormat. "yyyy-MM-dd") arvo)
    true
    (catch ParseException e
      false)))