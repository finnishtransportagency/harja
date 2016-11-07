(ns harja-laadunseuranta.tiedot.tarkastusajon-luonti
  (:require [harja-laadunseuranta.ui.paatason-navigointi :refer [paatason-navigointi]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.utils :refer [flip erota-mittaukset erota-havainnot]]
            [cljs.core.async :refer [<! timeout]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn luonti-peruttu []
  (reset! s/tallennustilaa-muutetaan false)
  (reset! s/tallennus-kaynnissa false))

(defn luo-ajo [tarkastustyyppi]
  (go-loop []
    (if-let [id (-> (<! (comms/luo-ajo! tarkastustyyppi)) :ok :id)]
      (s/tarkastusajo-kayntiin! tarkastustyyppi id)
      ;; yritä uudleleen kunnes onnistuu
      (do (<! (timeout 1000))
          (recur)))))

(defn paattaminen-peruttu []
  (reset! s/tallennus-kaynnissa true)
  (reset! s/tallennustilaa-muutetaan false))


(defn paata-ajo []
  (go-loop []
    (if (<! (comms/paata-ajo! @s/tarkastusajo-id @s/valittu-urakka))
      (s/tarkastusajo-seis!)

      ;; yritä uudelleen kunnes onnistuu, spinneri pyörii
      (do (<! (timeout 1000))
          (recur)))))

(defn- jatka-ajoa []
  (let [ajo @s/palautettava-tarkastusajo]
    (js/console.log "Tarkastusajo palautetaan: " (pr-str ajo))
    (reset! s/reittipisteet (mapv utils/keywordize-map (js->clj (get ajo "reittipisteet"))))
    (reset! s/kirjauspisteet (mapv utils/keywordize-map (js->clj (get ajo "tarkastuspisteet"))))
    (reset! s/tarkastustyyppi (keyword (get ajo "tarkastustyyppi")))
    (reset! s/tarkastusajo-id (get ajo "tarkastusajo"))
    (reset! s/tallennus-kaynnissa true))
  (reset! s/palautettava-tarkastusajo nil))

(defn- pakota-ajon-lopetus []
  (let [ajo @s/palautettava-tarkastusajo]
    (reitintallennus/poista-tarkastusajo @s/idxdb (get ajo "tarkastusajo"))
    (reitintallennus/tyhjenna-reittipisteet @s/idxdb))
  (reset! s/palautettava-tarkastusajo nil))