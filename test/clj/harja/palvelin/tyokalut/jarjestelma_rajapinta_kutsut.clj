(ns harja.palvelin.tyokalut.jarjestelma-rajapinta-kutsut
  (:require [harja.palvelin.jarjestelma-rajapinta :as jarjestelma-rajapinta]))

(defn kutsu-palvelua [palvelu & args]
  (apply jarjestelma-rajapinta/kutsu palvelu args))
