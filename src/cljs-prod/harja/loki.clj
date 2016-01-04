(ns harja.loki)


(defmacro mittaa-aika [nimi & body]
  `(let [nimi# ~nimi]
     (try 
       (when harja.loki/+lokitetaan+
         (.time js/console nimi#))
       ~@body
       (finally
         (when harja.loki/+lokitetaan+
           (.timeEnd js/console nimi#))))))
