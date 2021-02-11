(ns harja.transit
  "Harjan transit laajennokset"
  (:require [cognitect.transit :as t]
            [harja.pvm :as pvm]
            [harja.domain.roolit :as roolit]
            #?(:clj
               [harja.geo :as geo]

               :cljs
               [harja.pvm :as pvm])
            [clojure.string :as str]
            [harja.fmt :as fmt]
            [harja.tyokalut.big :as big])
  (:import #?@(:clj
               [(java.text SimpleDateFormat)
                (java.time LocalTime)]

               :cljs
               [(goog.date DateTime UtcDateTime)])))



#?(:clj (def +fi-date-time-format+ "dd.MM.yyyy HH:mm:ss")
   :cljs (deftype DateTimeHandler []
           Object
           (tag [_ v] "dt")
           (rep [_ v] (pvm/pvm-aika-sek v))))

#?(:cljs
   (do
     (deftype AikaHandler []
       Object
       (tag [_ v] "aika")
       (rep [_ v]
         (fmt/aika v)))
     (defn luo-aika [aika]
       (let [[t m h] (map js/parseInt (str/split aika #":"))]
         (pvm/->Aika t m h)))))

#?(:cljs
   (do
     (deftype BigDecHandler []
       Object
       (tag [_ v] "big")
       (rep [_ v]
         (.toString (:b v))))))

#?(:clj
   (def write-optiot {:handlers

                      {java.util.Date
                       (t/write-handler (constantly "dt")
                                        #(.format (SimpleDateFormat. +fi-date-time-format+) %))

                       java.math.BigDecimal
                       (t/write-handler (constantly "bd") double)

                       harja.tyokalut.big.BigDec
                       (t/write-handler (constantly "big") (comp str :b))

                       org.postgresql.geometric.PGpoint
                       (t/write-handler (constantly "pp") geo/pg->clj)

                       org.postgis.PGgeometry
                       (t/write-handler "pg" geo/pg->clj)

                       harja.domain.roolit.EiOikeutta
                       (t/write-handler (constantly "eo") #(:syy %))

                       java.time.LocalTime
                       (t/write-handler "aika" str)}})

   :cljs
   (def write-optiot {:handlers
                      {DateTime (DateTimeHandler.)
                       UtcDateTime (DateTimeHandler.)
                       pvm/Aika (AikaHandler.)
                       big/BigDec (BigDecHandler.)
                       }}))
#?(:clj
   (def read-optiot {:handlers
                     {"dt" (t/read-handler #(.parse (SimpleDateFormat. +fi-date-time-format+) %))
                      "aika" (t/read-handler #(LocalTime/parse %))
                      "big" (t/read-handler big/parse)}})

   :cljs
   (def read-optiot {:handlers
                     {"dt" #(pvm/->pvm-aika-sek %)

                      ;; Serveri lähettää java.math.BigDecimal typen doubleksi
                      ;; muunnettuna, joten tässä kelpaa identity
                      "bd" identity

                      ;; Serveri lähettää PGpoint ja PGgeometry muunnettuina
                      ;; kelpaa meille sellaisenaan
                      "pp" js->clj
                      "pg" js->clj

                      ;; EiOikeutta tulee serveriltä "eo" tägillä ja pelkkänä syy stringiä
                      "eo" #(roolit/->EiOikeutta %)

                      "aika" luo-aika

                      "big" big/parse}}))


(defn clj->transit
  "Muuntaa Clojure tietorakenteen Transit+JSON merkkijonoksi."
  ([data] (clj->transit data nil))
  ([data wo]
   (let [write-optiot (or wo write-optiot)]
     #?(:clj
        (with-open [out (java.io.ByteArrayOutputStream.)]
          (t/write (t/writer out :json write-optiot) data)
          (str out))

        :cljs
        (t/write (t/writer :json write-optiot) data)))))


(defn lue-transit
  "Lukee Transit+JSON muotoisen tiedon annetusta inputista."
  ([in] (lue-transit in nil))
  ([in ro]
   (let [read-optiot (or ro read-optiot)]
     #?(:clj
        (t/read (t/reader in :json read-optiot))

        :cljs
        (t/read (t/reader :json read-optiot) in)))))

#?(:clj
   (defn lue-transit-string
     ([in] (lue-transit-string in nil))
     ([in ro]
      (lue-transit (java.io.ByteArrayInputStream. (.getBytes in)) ro))))
