(ns harja.tuck-apurit
  "Tuckin omat testityökalut"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tuck-apurit :refer [vaadi-async-kutsut]]))

(defn e!
  "Prosessoi konstruktoidun tuck-eventin, joko tyhjää tai annettua tilaa vasten.

  (tuck-apurit/e! (->Tapahtuma true))
  (tuck-apurit/e! (->ToinenTapahtuma false 5) {:tosi? true :id 3})"
  ([event] (e! event {}))
  ([event tila]
   (tuck/process-event event tila)))