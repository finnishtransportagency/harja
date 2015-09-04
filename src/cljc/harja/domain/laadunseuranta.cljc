(ns harja.domain.laadunseuranta
  "Validin tarkastuksen skeema"
  (:require [schema.core :as s]
            [harja.domain.skeema :refer [pvm-tyyppi] :as skeema]
            [harja.domain.yleiset :refer [Tierekisteriosoite Osapuoli Teksti Sijainti]]
    #?(:cljs [harja.loki :refer [log]])))

(def Kasittelytapa (s/enum :tyomaakokous :puhelin :kommentit :muu))
(def Paatostyyppi (s/enum :sanktio :ei_sanktiota :hylatty))

(def Sakkolaji (s/enum :A :B :C :muistutus))
(def Sanktiotyyppi
  {:id              s/Num
   :nimi            s/Str
   :toimenpidekoodi (s/maybe s/Num)
   :laji            #{s/Keyword}})

(def Sanktio
  {:sakko?                               s/Bool
   :id                                   s/Num
   :perintapvm                           pvm-tyyppi
   :laji                                 Sakkolaji
   :tyyppi                               Sanktiotyyppi
   (s/optional-key :toimenpideinstanssi) s/Any              ;; FIXME: tpi instanssin skeema
   (s/optional-key :summa)               s/Num
   (s/optional-key :indeksi)             s/Str})

(def Paatos
  {:kasittelyaika                     pvm-tyyppi
   :kasittelytapa                     Kasittelytapa
   (s/optional-key :muukasittelytapa) (s/maybe s/Str)
   :paatos                            Paatostyyppi
   :perustelu                         s/Str
   })

(def Havainto
  {(s/optional-key :aika)              pvm-tyyppi           ;; ei ole, jos on tarkastuksen havainto
   :kuvaus                             Teksti
   :tekija                             Osapuoli
   (s/optional-key :kohde)             (s/maybe s/Str)
   (s/optional-key :urakka)            s/Any
   (s/optional-key :tekijanimi)        s/Str
   (s/optional-key :kommentit)         s/Any                ;; FIXME: kommentit skeema
   (s/optional-key :uusi-liite)    s/Any
   (s/optional-key :selvitys-pyydetty) (s/maybe s/Bool)
   (s/optional-key :id) s/Int
   (s/optional-key :paatos) Paatos
   (s/optional-key :sanktiot) {s/Num Sanktio}
   (s/optional-key :uusi-kommentti) s/Any
   (s/optional-key :liitteet) s/Any})

(def Tarkastustyyppi (s/enum :tiesto :talvihoito :soratie :laatu :pistokoe))

(def Talvihoitomittaus
  {:lampotila                    s/Num
   :tasaisuus                 s/Num
   :kitka                        s/Num
   :lumimaara                    s/Num
   (s/optional-key :hoitoluokka) (s/maybe s/Str)
   (s/optional-key :ajosuunta)   (s/maybe s/Int)})

(def Soratiemittaus
  {:hoitoluokka   (s/enum 1 2)
   :polyavyys     (s/enum 1 2 3 4 5)
   :tasaisuus     (s/enum 1 2 3 4 5)
   :kiinteys      (s/enum 1 2 3 4 5)
   :sivukaltevuus s/Num})


(def Tarkastus
  {(s/optional-key :uusi?)             s/Bool
   (s/optional-key :id)                s/Int
   :aika                               pvm-tyyppi
   :tr                                 Tierekisteriosoite
   (s/optional-key :sijainti)          Sijainti
   :tyyppi                             Tarkastustyyppi
   :tarkastaja                         Teksti
   (s/optional-key :mittaaja)          (s/maybe Teksti)
   (s/optional-key :talvihoitomittaus) Talvihoitomittaus
   (s/optional-key :soratiemittaus)    Soratiemittaus
   (s/optional-key :havainto)          Havainto})

(defn validoi-tarkastus [data]
  (skeema/tarkista Tarkastus data))

(defn validi-tarkastus? [data]
  #?(:cljs (log (pr-str data)))
  (let [virheet (validoi-tarkastus data)]
    #?(:cljs (log "tarkastus virheet: " (pr-str virheet)))
    (nil? virheet)))

(defn validoi-havainto [data]
  (skeema/tarkista Havainto data))

(defn validi-havainto? [data]
  (let [virheet (validoi-havainto data)]
    #?(:cljs (log "havainto virheet: " (pr-str virheet)))
    (nil? virheet)))
                    
