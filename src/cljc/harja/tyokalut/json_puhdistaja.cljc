(ns harja.tyokalut.json-puhdistaja)

; Määritellään henkilötietoja sisältävät avaimet
(def poistettavat-henkilotietoavaimet #{"etunimi" "sukunimi" "sahkoposti" "puhelin"})

; Funktio, joka poistaa henkilötiedot rekursiivisesti
(defn poista-henkilotiedot
  "Poistaa henkilötiedot annetusta tietorakenteesta."
  [kayttajatiedot poistettavat-henkilotietoavaimet]
  (cond
    (map? kayttajatiedot)
      (into {} (for [[k v] kayttajatiedot
                     :when (not (contains? poistettavat-henkilotietoavaimet (name k)))]
                 [k (poista-henkilotiedot v poistettavat-henkilotietoavaimet)]))

    (coll? kayttajatiedot)
      (map #(poista-henkilotiedot % poistettavat-henkilotietoavaimet) kayttajatiedot)

    :else kayttajatiedot))