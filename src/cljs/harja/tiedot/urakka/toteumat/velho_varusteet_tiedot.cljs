(ns harja.tiedot.urakka.toteumat.velho-varusteet-tiedot
  (:require [tuck.core :as tuck]))

(defrecord PaivitaHoitovuosiValinta [vuosi])

(extend-protocol tuck/Event

  PaivitaHoitovuosiValinta
  (process-event [{hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (do
      ; Muuta tila
      ; Kutsu post
      #_(tuck-apurit/post! :hae-toimenpiteen-tehtava-yhteenveto
                           {:urakka-id (-> @tila/yleiset :urakka :id)
                            :tehtavaryhma (:id rivi)
                            :hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
                           {:onnistui ->HaeToimenpiteenTehtavaYhteenvetoOnnistui
                            :epaonnistui ->HaeToimenpiteenTehtavaYhteenvetoEpaonnistui})
      ;Palauta uusi tila
      #_(assoc app :toimenpiteet-lataa true)
      app)))
