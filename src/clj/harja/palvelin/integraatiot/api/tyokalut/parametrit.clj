(ns harja.palvelin.integraatiot.api.tyokalut.parametrit
  "Apureita parametrien käsittelyyn"
  (:require [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :refer [heita-puutteelliset-parametrit-poikkeus]])
  (:import (java.text ParseException SimpleDateFormat)
           (java.util Date)))

(def esimerkki-aika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.)))

(defn pvm-aika-opt
  "Parsii parametrina saadun ajan. Heittää parametripoikkeuksen, jos pvm ei ole validi. 
Sallii puuttuvan parametrin, jolloin palautetaan nil."
  ([string] (pvm-aika-opt string "Anna päivämäärä ja aika muodossa: \"yyyy-MM-dd'T'HH:mm:ssX\" (esim. " esimerkki-aika ")"))
  ([string & virheet]
   (try
     (pvm-string->java-sql-date string)
     (catch ParseException e
       (heita-puutteelliset-parametrit-poikkeus
        (apply str virheet))))))

(defn pvm-aika 
  "Sama kuin pvm-aika-opt mutta ei salli puuttuvaa arvoa"
  ([string] (pvm-aika string "Anna pvm ja aika parametri"))
  ([string & virheet]
   (if-let [aika (pvm-aika-opt string)]
     aika
     (heita-puutteelliset-parametrit-poikkeus (apply str virheet)))))
