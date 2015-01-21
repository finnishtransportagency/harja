(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [ajax.core :refer [ajax-request transit-request-format transit-response-format]]
            [cljs.core.async :refer [put! close! chan]]
            [harja.asiakas.tapahtumat :as tapahtumat]))



(defn post!
  "Lähetä palvelupyyntö palvelimelle ja palauta kanava, josta vastauksen voi lukea. 
Kolmen parametrin versio ottaa lisäksi callbackin jota kutsua vastausarvolla eikä palauta kanavaa."
  ([service payload] (post! service payload nil))
  ([service payload callback-fn]
     (let [chan (when-not callback-fn
                  (chan))
           cb (fn [[_ vastaus]]
                (if callback-fn
                  (callback-fn vastaus)
                  (do (put! chan vastaus)
                      (close! chan))))]
       (ajax-request
        {:uri (str "/_/" (name service))
         :method :post
         :params payload
         :format (transit-request-format)
         :response-format (transit-response-format)
         :handler cb
         :error-handler (fn [[_ error]]
                          (tapahtumat/julkaise! (assoc error :aihe :palvelinvirhe))
                          (when chan (close! chan)))})
       chan)))

  
(defn ^:export test []
  (request! :ping {:foo 1} #(.log js/console "sain takaisin: " %)))
