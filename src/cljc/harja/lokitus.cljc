(ns harja.lokitus
  "Selain- ja palvelinpuolen yhteinen lokitus"
  (:require
    #?(:cljs [harja.loki :as loki])
    #?(:clj [taoensso.timbre :as log])))

#?(:cljs
(defn log
  "Lokittaa frontille"
  [& args]
  (apply loki/log (map pr-str args))))



#?(:clj
   (defn log
     "Lokittaa backille. Monen muuttujan lokitus ei toimi applyllä, koska log/debug on makro.
     Siksi multiarity-funktiona."
     ([a1] (log/debug a1))
     ([a1 a2]
      (log/debug a1 a2))
     ([a1 a2 a3]
      (log/debug a1 a2 a3))
     ([a1 a2 a3 a4]
      (log/debug a1 a2 a3 a4))
     ([a1 a2 a3 a4 a5]
      (log/debug a1 a2 a3 a4 a5))
     ([a1 a2 a3 a4 a5 a6]
      (log/debug a1 a2 a3 a4 a5 a6))))