(ns harja.loki)


(defmacro mittaa-aika [nimi & body]
  `(let [nimi# ~nimi]
     (try 
       (.time js/console nimi#)
       ~@body
       (finally 
         (.timeEnd js/console nimi#)))))
