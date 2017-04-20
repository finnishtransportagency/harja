(ns harja.tuck-apurit
  "Tuckin omat testityökalut"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tuck-apurit :refer [vaadi-async-kutsut]]))

(defn e-tila!
  [event & payload-and-state]
  "Prosessoi tuck-eventin, käyttäen annettuja payloadia ja tilaa"
  (tuck/process-event (apply event (butlast payload-and-state)) (last payload-and-state)))

(defn e!
  [event & payload]
  "Prosessoi tuck-eventin, käyttäen annettua payloadia ja tyhjä tilaa"
  (tuck/process-event (apply event payload) {}))