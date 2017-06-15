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

(defn palvelukutsu [{:keys [onnistui epaonnistui palvelu argumentit uusi-tila]}]
  (let [tulos! (tuck/send-async! onnistui)
        fail! (tuck/send-async! epaonnistui)]
    (try
      (go
        (let [vastaus (<! (k/post! palvelu argumentit))]
          (if (k/virhe? vastaus)
            (fail! vastaus)
            (tulos! vastaus))))

      uusi-tila

      (catch :default e
        (fail! nil)
        (throw e)))))