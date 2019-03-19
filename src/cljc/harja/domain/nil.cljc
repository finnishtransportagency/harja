(ns harja.domain.nil
  "Tämä ns on tarkoitettu speceille, joiden arvo tulee olla nil. Hyödyllinen silloin kun haluat varmistaa,
   että tietyn avaimen arvo on nil joissain tapauksissa ja kyseisellä avaimella on jo validi spec."
  (:require [clojure.spec.alpha :as s]))

(s/def ::tr-ajorata nil?)
(s/def ::tr-kaista nil?)
(s/def ::tr-loppuosa nil?)
(s/def ::tr-loppuetaisyys nil?)
