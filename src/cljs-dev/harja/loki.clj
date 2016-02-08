(ns harja.loki)


(defmacro mittaa-aika [nimi & body]
  `(let [nimi# ~nimi
         mittaa-aika?# harja.loki/+mittaa-aika+]
     (try 
       (when mittaa-aika?#
         (.time js/console nimi#))
       ~@body
       (finally
         (when mittaa-aika?#
           (.timeEnd js/console nimi#))))))
