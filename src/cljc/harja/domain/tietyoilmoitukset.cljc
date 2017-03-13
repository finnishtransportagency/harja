(ns harja.domain.turvallisuuspoikkeamat
  (:require [clojure.string :as str]))

(def kaistajarjestelyt
  {:tyotapaturma "Työtapaturma"
   :vaaratilanne "Vaaratilanne"
   :turvallisuushavainto "Turvallisuushavainto"
   :muu "Muu"})