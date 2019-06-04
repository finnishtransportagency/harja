(ns harja.tyokalut.avaimet)

(defn keys-in
  "Palauttaa mapin kaikki avainpolut"
  [m]
  (if (map? m)
    (vec
      (mapcat (fn [[k v]]
                (let [sub (keys-in v)
                      nested (map #(into [k] %) (filter (comp not empty?) sub))]
                  (if (seq nested)
                    nested
                    [[k]])))
              m))
    []))
