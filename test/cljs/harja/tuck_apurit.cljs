(ns harja.tuck-apurit
  "Tuckin omat testityökalut"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tuck-apurit :refer [vaadi-async-kutsut]]))

(defn e!
  "Prosessoi tuck-eventin annettuun tilaan käyttäen annettua payloadia"
  [tila event & payload]
  (tuck/process-event (apply event payload) tila))