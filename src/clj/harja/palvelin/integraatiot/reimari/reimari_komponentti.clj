(ns harja.palvelin.integraatiot.reimari.reimari-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.toimenpidehaku :as toimenpidehaku]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukko]))


(defprotocol HaeToimenpiteet
  (hae-toimenpiteet [this jarjestelma]))

(defrecord Reimari [pohja-url]
  (component/Lifecycle
    (start [this]
           (log/info "Käynnistetään Reimari-komponentti, pohja-url" pohja-url))
    (stop [this]
          (log/debug "Sammutetaan Reimari-komponentti")
          this)

    HaeToimenpiteet
    (hae-toimenpiteet [jarjestelma]
                      (toimenpidehaku/hae-toimenpiteet (:db jarjestelma) (:integraatioloki jarjestelma)))))
