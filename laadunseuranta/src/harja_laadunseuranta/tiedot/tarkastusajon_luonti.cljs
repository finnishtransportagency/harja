(ns harja-laadunseuranta.tiedot.tarkastusajon-luonti
  (:require [harja-laadunseuranta.ui.paanavigointi :refer [paanavigointi]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [cljs.core.async :refer [<! timeout]]
            [harja-laadunseuranta.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- aseta-tarkastusajo-sovelluksen-tilaan [sovellus ajo-id]
  (assoc sovellus
    :tarkastusajo-id ajo-id
    :tarkastusajo-kaynnissa? true))

(defn- kaynnista-tarkastusajo [ajo-id]
  (swap! s/sovellus #(aseta-tarkastusajo-sovelluksen-tilaan % ajo-id)))

(defn luo-ajo! []
  (reset! s/tarkastusajo-alkamassa? true)
  (go-loop []
    (if-let [id (-> (<! (comms/luo-ajo!)) :ok :id)]
      (do
        (kaynnista-tarkastusajo id)
        (reset! s/tarkastusajo-alkamassa? false))
      ;; yritä uudelleleen kunnes onnistuu
      (do (<! (timeout 1000))
          (recur)))))

(defn jatka-ajoa! []
  (let [ajo @s/palautettava-tarkastusajo]
    (reset! s/reittipisteet (mapv utils/keywordize-map (js->clj (get ajo "reittipisteet"))))
    (reset! s/kirjauspisteet (mapv utils/keywordize-map (js->clj (get ajo "tarkastuspisteet"))))
    (reset! s/tarkastusajo-id (get ajo "tarkastusajo"))
    (reset! s/tarkastusajo-kaynnissa? true))
  (reset! s/palautettava-tarkastusajo nil))

