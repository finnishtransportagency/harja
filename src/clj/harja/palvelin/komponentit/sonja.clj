(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylään liittymiseksi."
  (:require [com.stuartsierra.component :as component]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z])
  (:import (progress.message.jclient QueueConnectionFactory)
           (javax.jms Session Destination Queue TextMessage)))


;; SONJA JMS jonoihin kytkeytyminen, alustavaa testailukoodia tulevaisuuden lähtökohdaksi
;; kytkeydy jonoon:
;; ssh -L2511:192.83.32.231:2511 harja-mule1-stg
;;
;; (def qcf (QueueConnectionFactory. "localhost:2511"))
;; (def conn (.createConnection qcf "harja" "harjaxx"))
;; (def s (session conn))
;; (def s-to-h (queue s +sampo-to-harja+))
;; (def c (consumer s s-to-h))
;; (.start conn)
;; Viestin vastaanotto: (blokkaa, jos viestiä ei ole odottamassa)
;; (def m (.receive c))



(defn sonja-connection-factory [url]
  (QueueConnectionFactory. host port user password))

(defn connection [factory username password]
  (.createConnection factory username password))

(def +sampo-to-harja+ "Harja13-16.SampoToHarja.Msg") ;; SAMPO -> Harja (SAMPOn hankkeet,urakat, jne)
(def +sampo-to-harja-ack+ "Harja13-16.HarjaToSampo.Ack") ;; Harjan vastausviestit edellisiin


(def +harja-to-sampo+ "Harja13-16.HarjaToSampo.Msg")
(def +harja-to-sampo-ack+ "Harja13-16.SampoToHarja.Ack")



(defn session [c]
  (.createSession c false Session/AUTO_ACKNOWLEDGE))


(defn producer [s q]
  (.createProducer s q))

(defn queue [s name]
  (.createQueue s name))

(defn text-message [s payload]
  (doto (.createTextMessage s)
    (.setText payload)))

(defn consumer [s q]
  (.createConsumer s q))

(defn parse-resource [r]
  {:etunimi (z/xml1-> r (z/attr :first_name))
   :sukunimi (z/xml1-> r (z/attr :last_name))})

(defn parse-date [d]
  (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") d))

(defn parse-program [p]
  {:sampo-id (z/xml1-> p (z/attr :id))
   :alkupvm (z/xml1-> p (z/attr :schedule_start)
                      parse-date)
   :loppupvm (z/xml1-> p (z/attr :schedule_finish) parse-date)
   :nimi (z/xml1-> p (z/attr :name))})

(defn parse-sampo [payload]
  (let [xml (xml-zip (parse (java.io.ByteArrayInputStream. (.getBytes payload "UTF-8"))))]
    {:resurssit (z/xml-> xml
                         :Resource
                         parse-resource)
     :hankkeet (z/xml-> xml :Program
                        parse-program)}))

  
