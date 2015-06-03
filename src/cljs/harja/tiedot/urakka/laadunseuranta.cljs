(ns harja.tiedot.urakka.laadunseuranta
  "Urakan tarkastukset: tiestötarkastukset, talvihoitotarkastukset sekä soratietarkastukset."
  (:require [harja.asiakas.kommunikaatio :as k]))

(defn hae-urakan-tarkastukset
  "Hakee annetun urakan tarkastukset urakka id:n ja ajan perusteella."
  [urakka-id alkupvm loppupvm]
  (k/post! :hae-urakan-tarkastukset {:urakka-id urakka-id
                                     :alkupvm alkupvm
                                     :loppupvm loppupvm}))

(defn hae-urakan-havainnot
  "Hakee annetun urakan havainnot urakka id:n ja aikavälin perusteella."
  [urakka-id alkupvm loppupvm]
  (k/post! :hae-urakan-havainnot {:urakka-id urakka-id
                                  :alkupvm alkupvm
                                  :loppupvm loppupvm}))

(defn hae-tarkastus
  "Hakee tarkastuksen kaikki tiedot urakan id:n ja tarkastuksen id:n perusteella. Tähän liittyy havainnot sekä niiden reklamaatiot."
  [urakka-id tarkastus-id]
  (k/post! :hae-tarkastus {:urakka-id urakka-id
                           :tarkastus-id tarkastus-id}))


  
  
