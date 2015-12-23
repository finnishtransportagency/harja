(ns harja.domain.ilmoitusapurit
  "Selain- ja palvelinpuolen yhteisiä ilmoituksiin liittyviä asioita"
  (:require
    [clojure.string :as str]
    #?(:cljs [harja.loki :refer [log]])))


(def +ilmoitustyypit+ #{:kysely :toimenpidepyynto :tiedoitus})

(defn ilmoitustyypin-nimi
  [tyyppi]
  (case tyyppi
    :kysely "Kysely"
    :toimenpidepyynto "Toimenpide\u00ADpyyntö"
    :tiedoitus "Tiedoksi"))

(defn ilmoitustyypin-lyhenne
  [tyyppi]
  (case tyyppi
    :kysely "URK"
    :toimenpidepyynto "TPP"
    :tiedoitus "TUR"))

(defn ilmoitustyypin-lyhenne-ja-nimi
  [tyyppi]
  (str (ilmoitustyypin-lyhenne tyyppi) " (" (ilmoitustyypin-nimi tyyppi) ")"))

(def +ilmoitustilat+ #{:suljetut :avoimet})