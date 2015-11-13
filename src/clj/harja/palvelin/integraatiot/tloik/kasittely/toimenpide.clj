(ns harja.palvelin.integraatiot.tloik.kasittely.toimenpide
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tloik.sanomat.toimenpide-sanoma :as toimenpide-sanoma]))

(defn muodosta-toimenpide [db toimenpide-id]
  (toimenpide-sanoma/muodosta))