(ns harja.tyokalut.merkkijono)

(defn leikkaa [merkkia merkkijonosta]
  (apply str (take merkkia merkkijonosta)))