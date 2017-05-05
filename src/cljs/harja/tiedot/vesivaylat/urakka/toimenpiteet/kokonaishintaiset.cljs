(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false
         ;; TODO Testidataa vain
         :toimenpiteet [{::to/id 0
                         ::to/alue "Kopio, Iisalmen väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/tyolaji "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/vikakorjaus true
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 1
                         ::to/alue "Kuopio, Iisalmen väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/tyolaji "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 2
                         ::to/alue "Kopio, Iisalmen väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/tyolaji "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 3
                         ::to/alue "Varkaus, Kuopion väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/tyolaji "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 4
                         ::to/alue "Varkaus, Kuopion väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/tyolaji "Tykityöt"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 5
                         ::to/alue "Varkaus, Kuopion väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/tyolaji "Poljut"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}]}))

(defrecord Nakymassa? [nakymassa?])
(defrecord ValitseToimenpide [tiedot])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))
  
  ValitseToimenpide
  (process-event [{tiedot :tiedot} {:keys [toimenpiteet] :as app}]
    (let [toimenpide-id (:id tiedot)
          valinta (:valinta tiedot)
          paivitetty-toimenpide (-> (to/toimenpide-idlla toimenpiteet toimenpide-id)
                                    (assoc :valittu? valinta))]
      (assoc app :toimenpiteet (mapv #(if (= (::to/id %) toimenpide-id) paivitetty-toimenpide %)
                                     toimenpiteet)))))