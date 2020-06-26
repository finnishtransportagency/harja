(ns harja.tiedot.urakka.toteumat.mhu-akilliset-hoitotyot
  (:require [tuck.core :as tuck]
            [harja.domain.toteuma :as t]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]))

(defrecord PaivitaLomake [lomake])
(defrecord LahetaLomake [lomake])
(defrecord TyhjennaLomake [lomake])
(defrecord LomakkeenLahetysOnnistui [tulos])
(defrecord LomakkeenLahetysEpaonnistui [tulos])

(def oletuslomake {})

(extend-protocol
  tuck/Event
  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (assoc app :lomake lomake))
  LomakkeenLahetysEpaonnistui
  (process-event [_ app]
    app)
  LomakkeenLahetysOnnistui
  (process-event [_ app]
    app)
  LahetaLomake
  (process-event [{lomake :lomake} app]
    (let [{kuvaus       ::t/kuvaus
           pvm          ::t/pvm
           tehtava      ::t/tehtava
           toimenpide   ::t/toimenpide
           ei-sijaintia ::t/ei-sijaintia
           sijainti     ::t/sijainti} lomake
          urakka-id (-> @tila/yleiset :urakka :id)]
      (tuck-apurit/post! :tallenna-toteuma
                         {:tehtava   tehtava
                          :urakka-id urakka-id}
                         {:onnistui    ->LomakkeenLahetysOnnistui
                          :epaonnistui ->LomakkeenLahetysEpaonnistui}))
    app)
  TyhjennaLomake
  (process-event [{lomake :lomake} app]
    app))