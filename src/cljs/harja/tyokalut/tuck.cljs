(ns harja.tyokalut.tuck
  "Tuck-apureita"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck]
            [cljs.core.async :as async :refer [<! chan]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn palvelukutsu
  ([palvelu argumentit optiot]
   (palvelukutsu nil palvelu argumentit optiot))
  ([app palvelu argumentit {:keys [onnistui onnistui-parametrit epaonnistui epaonnistui-parametrit]}]
   (let [onnistui! (when onnistui (apply tuck/send-async! onnistui onnistui-parametrit))
         epaonnistui! (when epaonnistui (apply tuck/send-async! epaonnistui epaonnistui-parametrit))]
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

(defn e-paluukanavalla!
  "Antaa paluukanavan tuck-eventille. Palauttaa kanavan, josta vastauksen voi lukea.
   Tällä voi integroida esim. Gridin tallennuksen helposti Tuck-eventtiin, kunhan myös itse eventti
   tukee paluukanavan käsittelyä."
  [e! tapahtuma & tapahtuma-args]
  (let [ch (chan)]
    (e! (apply tapahtuma (conj (vec tapahtuma-args) ch)))
    ch))