(ns harja.pvm
  "Yleiset päivämääräkäsittelyn asiat asiakaspuolella."
  (:require [cljs-time.format :as df]
            [cljs-time.core :as t]
            [cljs-time.extend] ;; että saadaan vertailut ja häshäys toimimaan oikein
            [harja.loki :refer [log]])
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
  (js/Date. (.getYear goog-date)
            (.getMonth goog-date)
            (.getDate goog-date)
            (.getHours goog-date)
            (.getMinutes goog-date)
            (.getSeconds goog-date)
            (.getMilliseconds goog-date)))

(defn nyt []
  (DateTime.))

(defn luo-pvm [vuosi kk pv]
  (DateTime. vuosi kk pv))

(defn luo-js-pvm [vuosi kk pv]
  (goog->js (luo-pvm vuosi kk pv)))

(defn sama-pvm? [eka toka]
  (and (= (t/year eka) (t/year toka))
       (= (t/month eka) (t/month toka))
       (= (t/day eka) (t/day toka))))
  
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
  (df/formatter "dd.MM.yyyy"))
            
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


;; hoidon alueurakoiden päivämääräapurit
(defn vuoden-eka-pvm [vuosi]
  "Palauttaa vuoden ensimmäisen päivän 1.1.vuosi"
  (luo-pvm vuosi 0 1)
  )

(defn vuoden-viim-pvm [vuosi]
  "Palauttaa vuoden viimeisen päivän 31.12.vuosi"
  (luo-pvm vuosi 11 31)
  )

(defn hoitokauden-alkupvm [vuosi]
  "Palauttaa hoitokauden alkupvm:n 1.10.vuosi"
  (luo-pvm vuosi 9 1)
  )

(defn hoitokauden-loppupvm [vuosi]
  "Palauttaa hoitokauden loppupvm:n 30.9.vuosi"
  (luo-pvm vuosi 8 30)
  )

