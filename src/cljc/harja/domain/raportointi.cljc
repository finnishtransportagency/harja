(ns harja.domain.raportointi)

(def virhetyypit #{:info :varoitus :virhe})

(defn virhe? [solu]
  (and (vector? solu) (virhetyypit (first solu))))

(defn virheen-viesti [solu] (second solu))