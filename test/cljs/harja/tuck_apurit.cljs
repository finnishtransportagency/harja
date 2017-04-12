(ns harja.tuck-apurit
  "Tuckin omat testityökalut"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn e!
  "Prosessoi tuck-eventin annettuun tilaan käyttäen annettua payloadia"
  [event tila & payload]
  (tuck/process-event (apply event payload) tila))