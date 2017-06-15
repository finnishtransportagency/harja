(ns harja.tyokalut.tuck
  "Tuck-apureita"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck]
            [cljs.core.async :as async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn palvelukutsu
  ([palvelu argumentit optiot]
   (palvelukutsu nil palvelu argumentit optiot))
  ([app palvelu argumentit {:keys [onnistui epaonnistui]}]
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
         (throw e)))
     app)))