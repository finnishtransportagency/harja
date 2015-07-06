(ns harja.domain.laadunseuranta
  "Validin tarkastuksen skeema"
  (:require [schema.core :as s]
            [harja.domain.skeema :refer [pvm-tyyppi] :as skeema]
            [harja.domain.yleiset :refer [Tierekisteriosoite Osapuoli Teksti Sijainti]]
            #?(:cljs [harja.loki :refer [log]])))

(def Havainto
  {:kuvaus Teksti
   :tekija Osapuoli
   (s/optional-key :selvitys-pyydetty) s/Bool})

  
(def Tarkastustyyppi (s/enum :tiesto :talvihoito :soratie))

(def Talvihoitomittaus
  {:lampotila s/Num
   :epatasaisuus s/Num
   :kitka s/Num
   :lumimaara s/Num})

(def Soratiemittaus
  {:hoitoluokka (s/enum 1 2)
   :polyavyys (s/enum 1 2 3 4 5)
   :tasaisuus (s/enum 1 2 3 4 5)
   :kiinteys (s/enum 1 2 3 4 5)
   :sivukaltevuus s/Num})


(def Tarkastus
  {(s/optional-key :uusi?) s/Bool
   :aika pvm-tyyppi
   :tr Tierekisteriosoite
   (s/optional-key :sijainti) Sijainti
   :tyyppi Tarkastustyyppi
   :tarkastaja Teksti
   (s/optional-key :mittaaja) Teksti
   (s/optional-key :talvihoitomittaus) Talvihoitomittaus
   (s/optional-key :soratiemittaus) Soratiemittaus
   (s/optional-key :havainto) Havainto})

(defn validoi-tarkastus [data]
  (skeema/tarkista Tarkastus data))

(defn validi-tarkastus? [data]
  (let [virheet (validoi-tarkastus data)]
    #?(:cljs (log "virheet: " (pr-str virheet)))
    (nil? virheet)))
  
