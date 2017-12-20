(ns harja.palvelin.palvelut.kanavat.hinnat
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [specql.core :as specql]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.hinta :as hinta]))

(defn hae-hinnat [db user urakka-id toimenpide-idt]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-lisatyot user urakka-id)
  (let [hinta-mapit (specql/fetch db ::hinta/toimenpide<->hinta
                                     #{::hinta/hinta}
                                     {::hinta/toimenpide (specql.op/in toimenpide-idt)})
        hinta-idt (mapv ::hinta/hinta hinta-mapit)]
    (specql/fetch db ::hinta/toimenpiteen-hinta (specql/columns ::hinta/toimenpiteen-hinta) {::hinta/id (specql.op/in hinta-idt)})))
