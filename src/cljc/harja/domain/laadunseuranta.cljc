(ns harja.domain.laadunseuranta
  "Validin tarkastuksen skeema"
  (:require [schema.core :as s]
            [harja.domain.skeema :refer [pvm-tyyppi] :as skeema]
            [harja.domain.yleiset :refer [Tierekisteriosoite]]))


(def Tarkastustyyppi (s/enum :tiesto :talvihoito :soratie))

(def Talvihoitomittaus
  {:lampotila s/Num
   :epatasaisuus s/Num
   :kitka s/Num
   :lumimaara s/Num})

(def Soratiemittaus
  {:polyavyys (s/enum 1 2 3 4 5)
   :tasaisuus (s/enum 1 2 3 4 5)
   :kiinteys (s/enum 1 2 3 4 5)
   :sivukaltevuus s/Num})


(def Tarkastus
  {(s/optional-key :uusi?) s/Bool
   :aika pvm-tyyppi
   :tr Tierekisteriosoite
   :tyyppi Tarkastustyyppi
   (s/optional-key :talvihoitomittaus) Talvihoitomittaus
   (s/optional-key :soratiemittais) Soratiemittaus
   (s/optional-key :havainto) Havainto})


(defn validi-tarkastus? [data]
  (skeema/validoi Tarkastus data))
