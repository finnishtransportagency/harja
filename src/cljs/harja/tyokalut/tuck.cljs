(ns harja.tyokalut.tuck
  "Tuck-apureita"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck]
            [cljs.core.async :as async :refer [<! chan]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- palvelukutsu*
  [app palvelu argumentit {:keys [onnistui onnistui-parametrit epaonnistui epaonnistui-parametrit]}]
  (let [onnistui! (when onnistui (apply tuck/send-async! onnistui onnistui-parametrit))
        epaonnistui! (when epaonnistui (apply tuck/send-async! epaonnistui epaonnistui-parametrit))]
    (try
      (go
        (let [vastaus (if argumentit
                        (<! (k/post! palvelu argumentit))
                        (<! (k/get! palvelu)))]
          (if (k/virhe? vastaus)
            (when epaonnistui! (epaonnistui! vastaus))
            (when onnistui! (onnistui! vastaus)))))
      (catch :default e
        (when epaonnistui! (epaonnistui! nil))
        (throw e)))
    app))

(defn get!
  ([palvelu optiot]
   (get! nil palvelu optiot))
  ([app palvelu optiot]
   (palvelukutsu* app palvelu nil optiot)))

(defn post!
  ([palvelu argumentit optiot]
   (post! nil palvelu argumentit optiot))
  ([app palvelu argumentit optiot]
   (palvelukutsu* app palvelu argumentit optiot)))

(defn e-kanavalla!
  "Antaa paluukanavan tuck-eventille. Palauttaa kanavan, josta vastauksen voi lukea.
   Tällä voi integroida esim. Gridin tallennuksen helposti Tuck-eventtiin, kunhan myös itse eventti
   tukee paluukanavan käsittelyä."
  [e! tapahtuma & tapahtuma-args]
  (let [ch (chan)]
    (e! (apply tapahtuma (conj (vec tapahtuma-args) ch)))
    ch))