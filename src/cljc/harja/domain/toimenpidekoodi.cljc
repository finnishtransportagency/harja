(ns harja.domain.toimenpidekoodi
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.pvm :as pvm]
    [harja.domain.muokkaustiedot :as m]
    [harja.kyselyt.specql]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["hinnoittelutyyppi" ::hinnoittelutype (specql.transform/transform (specql.transform/to-keyword))]
  ["toimenpidekoodi" ::toimenpidekoodi
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {"yksiloiva_tunniste" ::yksiloiva-tunniste}
   #?(:clj {::yksiloiva-tunniste (specql.transform/transform (harja.kyselyt.specql/->UUIDTransform))})
   {"emo" ::emo-id}
   {::toimenpidekoodi-join (specql.rel/has-one
                             ::emo-id
                             ::toimenpidekoodi
                             ::id)}])

(def perustiedot #{::id ::nimi})
(def viittaukset #{::toimenpidekoodi-id ::hinnoittelu-id})

(defn tuotteen-jarjestys [t2-koodi]
  (case t2-koodi
    "23100" 1 ; Talvihoito ensimmäisenä
    "23110" 2 ; Liikenneympäristön hoito toisena
    "23120" 3 ; Soratien hoito kolmantena
    ;; kaikki muut sen jälkeen
    4))


(defn tuotteen-jarjestys-mhu [t2-koodi]
  (case t2-koodi
    "23100" 1 ; Talvihoito ensimmäisenä
    "23110" 2 ; Liikenneympäristön hoito toisena
    "23120" 3 ; Soratien hoito kolmantena
    "20100" 4 ; Päällyste
    "20190" 5 ; MHU Ylläpito
    "14300" 6 ; MHU Korvausinvestointi
    "23150" 7 ; MHU ja HJU hoidon johto
    ;; kaikki muut sen jälkeen
    8))

(defn toimenpidekoodi-tehtavalla [rivit tehtava]
  (first (filter #(= (:tehtava %) tehtava) rivit)))

(defn aikavalin-hinnalliset-suunnitellut-tyot [suunnitellut-tyot valittu-aikavali]
      (filter
        #(and (:yksikkohinta %)
              (pvm/sama-pvm? (:alkupvm %) (first valittu-aikavali))
              (pvm/sama-pvm? (:loppupvm %) (second valittu-aikavali)))
        suunnitellut-tyot))
