(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.domain.vesivaylat.toimenpide :as t]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false
         ;; TODO Testidataa vain
         :toimenpiteet [{::t/id 0
                         ::t/alue "Kopio, Iisalmen väylä"
                         ::t/tyoluokka "Asennus ja huolto"
                         ::t/toimenpide "Huoltotyö"
                         ::t/turvalaitetyyppi "Viitat"
                         ::t/pvm (pvm/nyt)
                         ::t/vikakorjaus true
                         ::t/turvalaite "Siitenluoto (16469)"}
                        {::t/id 1
                         ::t/alue "Kopio, Iisalmen väylä"
                         ::t/tyoluokka "Asennus ja huolto"
                         ::t/toimenpide "Huoltotyö"
                         ::t/turvalaitetyyppi "Viitat"
                         ::t/pvm (pvm/nyt)
                         ::t/turvalaite "Siitenluoto (16469)"}
                        {::t/id 2
                         ::t/alue "Kopio, Iisalmen väylä"
                         ::t/tyoluokka "Asennus ja huolto"
                         ::t/toimenpide "Huoltotyö"
                         ::t/turvalaitetyyppi "Viitat"
                         ::t/pvm (pvm/nyt)
                         ::t/turvalaite "Siitenluoto (16469)"}
                        {::t/id 45
                         ::t/alue "Varkaus, Kuopion väylä"
                         ::t/tyoluokka "Asennus ja huolto"
                         ::t/toimenpide "Huoltotyö"
                         ::t/turvalaitetyyppi "Viitat"
                         ::t/pvm (pvm/nyt)
                         ::t/turvalaite "Siitenluoto (16469)"}
                        {::t/id 3
                         ::t/alue "Varkaus, Kuopion väylä"
                         ::t/tyoluokka "Asennus ja huolto"
                         ::t/toimenpide "Huoltotyö"
                         ::t/turvalaitetyyppi "Tykityöt"
                         ::t/pvm (pvm/nyt)
                         ::t/turvalaite "Siitenluoto (16469)"}
                        {::t/id 4
                         ::t/alue "Varkaus, Kuopion väylä"
                         ::t/tyoluokka "Asennus ja huolto"
                         ::t/toimenpide "Huoltotyö"
                         ::t/turvalaitetyyppi "Poljut"
                         ::t/pvm (pvm/nyt)
                         ::t/turvalaite "Siitenluoto (16469)"}]}))

(defrecord Nakymassa? [nakymassa?])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?)))