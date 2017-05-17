(ns harja.palvelin.integraatiot.reimari.toimenpidehaku
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet :as sanoma]
            [harja.palvelin.tyokalut.lukot :as lukko]))





(defn hae-toimenpiteet* [x]
  (log/debug "hae-toimenpiteet* - parametri:" x)
  )

(defn hae-toimenpiteet [db integraatioloki]
  (lukko/yrita-ajaa-lukon-kanssa
   db "reimari-hae-toimenpiteet"
   (fn []
     (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "reimari" "hae-toimenpiteet"
      (hae-toimenpiteet* db)))))
