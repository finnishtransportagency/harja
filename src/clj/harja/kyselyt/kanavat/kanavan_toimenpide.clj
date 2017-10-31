(ns harja.kyselyt.kanavat.kanavan-toimenpide
  (:require [specql.core :refer [fetch]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]))

(defn hae-kanavatoimenpiteet [db hakuehdot]
  (fetch db ::toimenpide/kanava-toimenpide toimenpide/kaikki-kentat hakuehdot))



