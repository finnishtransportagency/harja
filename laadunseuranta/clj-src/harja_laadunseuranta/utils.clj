(ns harja-laadunseuranta.utils)


(defn select-non-nil-keys [c keys]
  (into {} (filterv #(not (nil? (second %))) (into [] (select-keys c keys)))))
