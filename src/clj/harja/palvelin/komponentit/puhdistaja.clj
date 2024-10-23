(ns harja.palvelin.komponentit.puhdistaja
  (:require [clojure.walk :as walk]))

;; Määritellään henkilötietoja sisältävät avaimet
(def poistettavat-avaimet #{:etunimi, :sukunimi, :sahkoposti, :puhelin})

;; Poistaa henkilötiedot ennen lokitusta.
(defn kayttaja-ilman-henkilotietoja [kayttaja]
  (walk/postwalk
    #(if (map? %) (apply dissoc % poistettavat-avaimet) %)
    kayttaja))