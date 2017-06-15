(ns harja.tuck-apurit
  "Tuckin omat testityökalut"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck]
            [cljs.core.async :as async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tuck-apurit :refer [vaadi-async-kutsut]]))

(defn e!
  "Prosessoi konstruktoidun tuck-eventin, joko tyhjää tai annettua tilaa vasten.

  (tuck-apurit/e! (->Tapahtuma true))
  (tuck-apurit/e! (->ToinenTapahtuma false 5) {:tosi? true :id 3})"
  ([event] (e! event {}))
  ([event tila]
   (tuck/process-event event tila)))

(defn palvelukutsu [palvelu argumentit {:keys [onnistui epaonnistui]}]
  (let [onnistui! (when onnistui (tuck/send-async! onnistui))
        epaonnistui! (when epaonnistui (tuck/send-async! epaonnistui))]
    (try
      (go
        (let [vastaus (<! (k/post! palvelu argumentit))]
          (if (k/virhe? vastaus)
            (when epaonnistui! (epaonnistui! vastaus))
            (when onnistui! (onnistui! vastaus)))))
      (catch :default e
        (when epaonnistui! (epaonnistui! nil))
        (throw e)))))epaonnistui!