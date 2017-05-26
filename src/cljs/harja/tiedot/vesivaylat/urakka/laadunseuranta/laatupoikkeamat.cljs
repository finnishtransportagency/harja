(ns harja.tiedot.vesivaylat.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.vayla :as va]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false
         :laatupoikkeamat [{:id 1
                            :pvm (t/now) ;; TODO namespaceta avvaimet
                            :turvalaite {::tu/nimi "Siitenluoto (16469)"}
                            :vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                    ::va/id 1}
                            :kuvaus "Valo ei näy"
                            :tekija "Antti Ahti"
                            :paatos :tarkstetaan}
                           {:id 2
                            :pvm (t/now)
                            :turvalaite {::tu/nimi "Siitenluoto (16469)"}
                            :vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                    ::va/id 1}
                            :kuvaus "Valo ei näy täälläkään"
                            :tekija "Antti Ahti"
                            :paatos :tarkstetaan}
                           {:id 3
                            :pvm (t/now)
                            :turvalaite {::tu/nimi "Siitenluoto (16469)"}
                            :vayla {::va/nimi "Varkaus, Kuopion väylä"
                                    ::va/id 2}
                            :kuvaus "Mikäs näitä valoja nyt vaivaa"
                            :tekija "Antti Ahti"
                            :paatos :tarkstetaan}]}))

(defrecord Nakymassa? [nakymassa?])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?)))
