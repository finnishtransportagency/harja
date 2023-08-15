(ns harja.palvelin.integraatiot.api.tyokalut.parametrit
  "Apureita parametrien käsittelyyn"
  (:require [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :refer [heita-puutteelliset-parametrit-poikkeus]])
  (:import (java.text ParseException SimpleDateFormat)
           (java.util Date)))

(def pvm-aika-muoto "yyyy-MM-dd'T'HH:mm:ssX")
(def pvm-aika-muotoZ "yyyy-MM-dd'T'HH:mm:ssZ")
(def esimerkki-aika (.format (SimpleDateFormat. pvm-aika-muoto) (Date.)))

(defn pvm-aika-opt
  "Parsii parametrina saadun ajan. Heittää parametripoikkeuksen, jos pvm ei ole validi. 
Sallii puuttuvan parametrin, jolloin palautetaan nil."
  ([string] (pvm-aika-opt string "Anna päivämäärä ja aika muodossa: \"yyyy-MM-dd'T'HH:mm:ssX\" (esim. " esimerkki-aika "), sain: " string))
  ([string & viestit]
   (when string
     (try
       (.parse (SimpleDateFormat. pvm-aika-muoto) string)
       (catch ParseException e
         (heita-puutteelliset-parametrit-poikkeus
          {:koodi "virheellinen-pvm-aika-muoto"
           :viesti (apply str viestit)}))))))

(defn pvm-aika
  "Sama kuin pvm-aika-opt mutta ei salli puuttuvaa arvoa"
  ([string] (pvm-aika string "Anna pvm ja aika parametri"))
  ([string & virheet]
   (if-let [aika (pvm-aika-opt string)]
     aika
     (heita-puutteelliset-parametrit-poikkeus
      {:koodi "pakollinen-pv-aika-parametri-puuttuu"
       :viesti (apply str virheet)}))))
