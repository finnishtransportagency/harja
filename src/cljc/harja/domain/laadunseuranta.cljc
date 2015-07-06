(ns harja.domain.laadunseuranta
  "Validin tarkastuksen skeema"
  (:require [schema.core :as s]
            [harja.domain.skeema :refer [pvm-tyyppi] :as skeema]
            [harja.domain.yleiset :refer [Tierekisteriosoite Osapuoli Teksti Sijainti]]
            #?(:cljs [harja.loki :refer [log]])))

(def Havainto
  {:kuvaus Teksti
   :tekija Osapuoli
   (s/optional-key :selvitys-pyydetty) (s/maybe s/Bool)
   (s/optional-key :id) s/Int})

  
(def Tarkastustyyppi (s/enum :tiesto :talvihoito :soratie))

(def Talvihoitomittaus
  {:lampotila s/Num
   :epatasaisuus s/Num
   :kitka s/Num
   :lumimaara s/Num
   (s/optional-key :hoitoluokka) (s/maybe s/Str)
   (s/optional-key :ajosuunta) (s/maybe s/Int)})

(def Soratiemittaus
  {:hoitoluokka (s/enum 1 2)
   :polyavyys (s/enum 1 2 3 4 5)
   :tasaisuus (s/enum 1 2 3 4 5)
   :kiinteys (s/enum 1 2 3 4 5)
   :sivukaltevuus s/Num})


(def Tarkastus
  {(s/optional-key :uusi?) s/Bool
   (s/optional-key :id) s/Int
   :aika pvm-tyyppi
   :tr Tierekisteriosoite
   (s/optional-key :sijainti) Sijainti
   :tyyppi Tarkastustyyppi
   :tarkastaja Teksti
   (s/optional-key :mittaaja) (s/maybe Teksti)
   (s/optional-key :talvihoitomittaus) Talvihoitomittaus
   (s/optional-key :soratiemittaus) Soratiemittaus
   (s/optional-key :havainto) Havainto})

(defn validoi-tarkastus [data]
  (skeema/tarkista Tarkastus data))

(defn validi-tarkastus? [data]
  (let [virheet (validoi-tarkastus data)]
    #?(:cljs (log "virheet: " (pr-str virheet)))
    (nil? virheet)))
  
(comment
  {:tunniste "tarkastus1234"
   :aika "2015-07-07T12:30:22Z"
   :sijainti {:tie {:numero 1234
                    :aosa 1
                    :aet 100
                    :losa 73
                    :let 20}
              :koordinaatit {:x 430780
                             :y 72330530}}
   :tarkastaja {:id 1233232
                :etunimi "Taneli"
                :sukunimi "Tarkastaja"}
   
   :soratiemittaus {:hoitoluokka 1
                    :polyavyys 2
                    :tasaisuus 3
                    :kiinteys 5
                    :sivukaltevuus 17.4}

   :havainto {:kuvaus "Jotain outoa"}}})

                    
