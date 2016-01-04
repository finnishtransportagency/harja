(ns harja.loki)


(defmacro mittaa-aika [nimi & body]
  `(let [nimi# ~nimi]
     (try 
       (when harja.loki/+lokitus-paalla+
         (.time js/console nimi#))
       ~@body
       (finally
         (when harja.loki/+lokitus-paalla+
           (.timeEnd js/console nimi#))))))
