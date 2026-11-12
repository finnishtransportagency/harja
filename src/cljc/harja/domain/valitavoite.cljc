(ns harja.domain.valitavoite
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            #?(:clj [clj-time.coerce :as coerce])
            #?(:clj [clj-time.core :as t])
            #?(:cljs [cljs-time.core :as t])))

#?(:clj
   (defn ->datetime [pvm]
     (cond
       (instance? org.joda.time.DateTime pvm) pvm
       (instance? java.util.Date pvm) (coerce/from-date pvm)
       :else pvm))

   :cljs
   (defn ->datetime [pvm]
     pvm))

(defn valmiustila-fmt [{:keys [valmispvm takaraja] :as valitavoite} tila]
  (case tila
    :uusi "Uusi"
    :valmis "Valmistunut"
    :kesken
    (let [paivia-valissa (pvm/paivia-valissa (->datetime (pvm/nyt)) (->datetime takaraja))]
      (str "Ei valmis" (when (pos? paivia-valissa)
                         (str " (" (fmt/kuvaile-paivien-maara paivia-valissa
                                                              {:lyhenna-yksikot? true})
                              " jäljellä)"))))

    :myohassa
    (let [paivia-valissa (pvm/paivia-valissa (->datetime takaraja) (->datetime (pvm/nyt)))]
      (str "Myöhässä" (when (pos? paivia-valissa)
                        (str " (" (fmt/kuvaile-paivien-maara paivia-valissa
                                                             {:lyhenna-yksikot? true})
                             ")"))))))

(def valmiustila-fmt-yksinkertainen
  {:uusi "Uusi"
   :valmis "Valmistunut"
   :kesken "Ei valmis"
   :myohassa "Myöhässä"})

(defn valmiustila [{:keys [valmispvm takaraja] :as valitavoite}]
  (cond (nil? takaraja)
        :uusi

        (and takaraja valmispvm)
        :valmis

        (and takaraja (nil? valmispvm) (pvm/sama-tai-ennen? (->datetime (pvm/nyt)) (->datetime takaraja)))
        :kesken

        (and takaraja (nil? valmispvm) (t/after? (->datetime (pvm/nyt)) (->datetime takaraja)))
        :myohassa))

(defn valmiustilan-kuvaus [valitavoite]
  (valmiustila-fmt valitavoite (valmiustila valitavoite)))

(defn valmiustilan-kuvaus-yksinkertainen [valitavoite]
  (valmiustila-fmt-yksinkertainen (valmiustila valitavoite)))
