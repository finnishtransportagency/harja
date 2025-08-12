(ns harja.kyselyt.vesivaylat.tyot
  (:require [clojure.set :as set]
            [specql.core :as specql]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.tyo :as tyo]
            [specql.op :as op]))

(defn hae-hinnoittelujen-tyot [db hinnoittelu-idt]
  (specql/fetch db
    ::tyo/tyo
    (set/union tyo/perustiedot tyo/viittaus-idt)
    {::tyo/hinnoittelu-id (op/in hinnoittelu-idt)
     ::m/poistettu? false}))
