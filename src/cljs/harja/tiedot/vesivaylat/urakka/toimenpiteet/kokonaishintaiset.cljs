(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
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
                         ::to/turvalaitetyyppi "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/vikakorjaus true
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 1
                         ::to/alue "Kuopio, Iisalmen väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/turvalaitetyyppi "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 2
                         ::to/alue "Kopio, Iisalmen väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/turvalaitetyyppi "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 3
                         ::to/alue "Varkaus, Kuopion väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/turvalaitetyyppi "Viitat"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 4
                         ::to/alue "Varkaus, Kuopion väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/turvalaitetyyppi "Tykityöt"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}
                        {::to/id 5
                         ::to/alue "Varkaus, Kuopion väylä"
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/turvalaitetyyppi "Poljut"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite "Siitenluoto (16469)"}]}))

(defrecord Nakymassa? [nakymassa?])
(defrecord ValitseToimenpide [tiedot])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))
  
  ValitseToimenpide
  (process-event [{:keys [id valinta]} {:keys [toimenpiteet] :as app}]
    (let [paivitetty-toimenpide (-> (to/toimenpide-idlla toimenpiteet id)
                                    (assoc :valittu? valinta))]
      (assoc app :toimenpiteet (mapv #(if (= (::to/id %) id) paivitetty-toimenpide %)
                                     toimenpiteet)))))