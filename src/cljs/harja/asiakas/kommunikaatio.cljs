(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [ajax.core :refer [ajax-request transit-request-format transit-response-format]]
            [cljs.core.async :refer [put! close! chan]]
            [harja.asiakas.tapahtumat :as tapahtumat]))



(defn- kysely [palvelu metodi parametrit transducer]
  (let [chan (chan)
        cb (fn [[_ vastaus]]
             (put! chan (if transducer (into [] transducer vastaus) vastaus))
             (close! chan))]
    (ajax-request {:uri (str "/_/" (name palvelu))
                 :method metodi
                 :params parametrit
                 :format (transit-request-format)
                 :response-format (transit-response-format)
                 :handler cb
                 :error-handler (fn [[_ error]]
                                  (tapahtumat/julkaise! (assoc error :aihe :palvelinvirhe))
                                  (close! chan))})
    chan))

(defn post!
  "Lähetä HTTP POST -palvelupyyntö palvelimelle ja palauta kanava, josta vastauksen voi lukea. 
Kolmen parametrin versio ottaa lisäksi transducerin, jolla tulosdata vektori muunnetaan ennen kanavaan kirjoittamista."
  ([service payload] (post! service payload nil))
  ([service payload transducer]
     (kysely service :post payload transducer)))



(defn get!
  "Lähetä HTTP GET -palvelupyyntö palvelimelle ja palauta kanava, josta vastauksen voi lukea. 
Kahden parametrin versio ottaa lisäksi transducerin jolla tulosdata vektori muunnetaan ennen kanavaan kirjoittamista."
  ([service] (get! service nil))
  ([service transducer]
     (kysely service :get nil transducer)))

