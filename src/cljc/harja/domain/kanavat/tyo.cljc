(ns harja.domain.kanavat.tyo
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    [harja.domain.toimenpidekoodi :as tpk]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_tyo" ::toimenpiteen-tyo
   {"toimenpide" ::toimenpide-id
    "toimenpidekoodi-id" ::toimenpidekoodi-id
    ::toimenpidekoodi (specql.rel/has-one
                       ::toimenpidekoodi-id
                       ::tpk/toimenpidekoodi
                       ::tpk/id)}
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   ])

;; Löysennetään tyyppejä numeroiksi, koska JS-maailmassa ei ole BigDeccejä
(s/def ::maara number?)




(def perustiedot
  #{::id
    ::maara
    ::toimenpide-id
    ::toimenpidekoodi-id})

(def viittaus-idt
  #{::hinnoittelu-id
    ::toimenpidekoodi})

(def perustiedot-viittauksineen (clojure.set/union perustiedot viittaus-idt))

(def metatiedot m/muokkauskentat)

(defn toiden-kokonaishinta [tyot suunnitellut-tyot]
  (reduce + 0
          (map (fn [tyo]
                 (let [tyon-tpk (tpk/toimenpidekoodi-tehtavalla
                                  suunnitellut-tyot
                                  (::toimenpidekoodi-id tyo))]
                   (* (::maara tyo) (:yksikkohinta tyon-tpk))))
               tyot)))

(defn paivita-tyon-tiedot-idlla
  "Päivittää töiden joukosta yksittäisen työn, jolla annettu id."
  [tyot tiedot]
  (mapv (fn [tyo]
          (if (= (::id tyo) (::id tiedot))
            (merge tyo tiedot)
            tyo))
        tyot))
