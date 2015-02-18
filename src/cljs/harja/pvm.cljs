(ns harja.pvm
  "Yleiset päivämääräkäsittelyn asiat asiakaspuolella."
  (:require [cljs-time.format :as df])
  (:import (goog.date DateTime)))


(defn js->goog
  "Muunna Javascript päivämäärä goog muotoon"
  [js-date]
  (DateTime. (+ 1900 (.getYear js-date))
             (.getMonth js-date)
             (.getDate js-date)
             (.getHours js-date)
             (.getMinutes js-date)
             (.getSeconds js-date)
             (.getMilliseconds js-date)))

(defn goog->js
  "Muunna goog päivämäärä Javascript muotoon"
  [goog-date]
  (js/Date. (- (.getYear goog-date) 1900)
            (.getMonth goog-date)
             (.getDate goog-date)
             (.getHours goog-date)
             (.getMinutes goog-date)
             (.getSeconds goog-date)
             (.getMilliseconds goog-date)))

(defn muunna-aika
  "Muuntaa annetun mäpin annetut päivämääräkentät JS muodosta goog.date.DateTime instansseiksi."
  [obj & kentat]
  (loop [obj obj
         [k & kentat] kentat]
    (if-not k
      obj
      (recur (assoc obj k (js->goog (get obj k)))
             kentat))))

(defn muunna-aika-js
  "Muuntaa annetun mäpin annetut päivämääräkentät goog.date.DateTime muodosta JS date instansseiksi."
  [obj & kentat]
  (loop [obj obj
         [k & kentat] kentat]
    (if-not k
      obj
      (recur (assoc obj k (goog->js (get obj k)))
             kentat))))
(def fi-pvm
  "Päiväämäärän formatointi suomalaisessa muodossa"
  (df/formatter "d.MM.yyyy"))
            
(defn pvm
  "Formatoi päivämäärän suomalaisessa muodossa"
  [pvm]
  (df/unparse fi-pvm pvm))

(defn ->pvm [teksti]
  "Jäsentää tekstistä dd.MM.yyyy muodossa olevan päivämäärän. Jos teksti ei ole oikeaa muotoa, palauta nil."
  (try
    (df/parse fi-pvm teksti)
    (catch js/Error e
      (.log js/console "E: " e)
      nil)))
