(ns harja.tiedot.urakka.toteumat.mhu-akilliset-hoitotyot
  (:require [tuck.core :as tuck]
            [harja.domain.toteuma :as t]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.loki :as loki]))

(defrecord PaivitaLomake [lomake])
(defrecord LahetaLomake [lomake])
(defrecord TyhjennaLomake [lomake])
(defrecord LisaaToteuma [lomake])
(defrecord LomakkeenLahetysOnnistui [tulos])
(defrecord LomakkeenLahetysEpaonnistui [tulos])

(def oletuslomake {})

(def uusi-toteuma {})

(extend-protocol
  tuck/Event
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
                         {:tehtava    tehtava
                          :urakka-id  urakka-id
                          :toimenpide toimenpide}
                         {:onnistui    ->LomakkeenLahetysOnnistui
                          :epaonnistui ->LomakkeenLahetysEpaonnistui}))
    app)
  LisaaToteuma
  (process-event [{lomake :lomake} app]
    (let [lomake (update lomake ::t/toteumat conj uusi-toteuma)]
      (assoc app :lomake lomake)))
  LomakkeenLahetysEpaonnistui
  (process-event [_ app]
    app)
  LomakkeenLahetysOnnistui
  (process-event [_ app]
    app)
  PaivitaLomake
  (process-event [{{useampi? ::t/useampi-toteuma
                    tyyppi ::t/tyyppi
                    sijainti ::t/sijainti
                    ei-sijaintia ::t/ei-sijaintia
                    :as lomake} :lomake} app]
    ; WIP tää pitää korjata sijaintien osalta jos on yksi toteuma
    (let [useampi-aiempi? (get-in app [:lomake ::t/useampi-toteuma])]
      (-> app
          (assoc :lomake lomake)
          (update :lomake (if (and
                                (= tyyppi :maaramitattava)
                                (not= useampi? useampi-aiempi?))
                            (fn [lomake]
                              (if (true? useampi?)
                                (update lomake ::t/toteumat conj uusi-toteuma)
                                (update lomake ::t/toteumat #(conj [] (first %)))))
                            identity)))))
  TyhjennaLomake
  (process-event [{lomake :lomake} app]
    app))