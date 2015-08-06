(ns harja.transit
  "Harjan transit laajennokset"
  (:require [cognitect.transit :as t]
            [harja.domain.roolit :as roolit]
            #?(:clj
               [harja.geo :as geo]

               :cljs
               [harja.pvm :as pvm]))
  (:import #?(:clj
              (java.text SimpleDateFormat)

              :cljs
              (goog.date DateTime UtcDateTime))))



#?(:clj (def +fi-date-time-format+ "dd.MM.yyyy HH:mm:ss")
   :cljs (deftype DateTimeHandler []
           Object
           (tag [_ v] "dt")
           (rep [_ v] (pvm/pvm-aika-sek v))))

(def write-optiot {:handlers
                   #?(:clj
                      {java.util.Date
                       (t/write-handler (constantly "dt")
                                        #(.format (SimpleDateFormat. +fi-date-time-format+) %))
                       
                       java.math.BigDecimal
                       (t/write-handler (constantly "bd") double)
                       
                       org.postgresql.geometric.PGpoint
                       (t/write-handler (constantly "pp") geo/pg->clj)

                       harja.domain.roolit.EiOikeutta
                       (t/write-handler (constantly "eo") #(:syy %))}
                      
                      :cljs
                      {DateTime (DateTimeHandler.)
                       UtcDateTime (DateTimeHandler.)})})

(def read-optiot {:handlers
                  #?(:clj
                     {"dt" (t/read-handler #(.parse (SimpleDateFormat. +fi-date-time-format+) %))}

                     :cljs
                     {"dt" #(pvm/->pvm-aika-sek %)

                      ;; Serveri lähettää java.math.BigDecimal typen doubleksi
                      ;; muunnettuna, joten tässä kelpaa identity
                      "bd" identity

                      ;; Serveri lähettää PGpoint tyypit muunnettuna [x y] vektoreiksi, jotka
                      ;; kelpaa meille sellaisenaan
                      "pp" js->clj

                      ;; EiOikeutta tulee serveriltä "eo" tägillä ja pelkkänä syy stringiä
                      "eo" #(roolit/->EiOikeutta %)})})


(defn clj->transit
  "Muuntaa Clojure tietorakenteen Transit+JSON merkkijonoksi."
  [data]
  #?(:clj
     (with-open [out (java.io.ByteArrayOutputStream.)]
       (t/write (t/writer out :json write-optiot) data)
       (str out))

     :cljs
     (t/write (t/writer :json write-optiot) data)))


(defn lue-transit
  "Lukee Transit+JSON muotoisen tiedon annetusta inputista."
  [in]
  #?(:clj
     (t/read (t/reader in :json read-optiot))

     :cljs
     (t/read (t/reader :json read-optiot) in)))

  
