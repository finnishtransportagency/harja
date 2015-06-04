(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [ajax.core :refer [ajax-request transit-request-format transit-response-format]]
            [cljs.core.async :refer [put! close! chan]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.pvm :as pvm]
            [cognitect.transit :as t]
            [harja.loki :refer [log]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import (goog.date DateTime UtcDateTime)))

(def +polku+ (let [host (.-host js/location)]
               (if (#{"localhost" "localhost:3000" "harja-test.solitaservices.fi"} host)
                 "/"
                 "/harja/")))
(defn polku []
  (str +polku+ "_/"))


(deftype DateTimeHandler []
  Object
  (tag [_ v] "dt")
  (rep [_ v] (pvm/pvm-aika-sek v)))

(defn- kysely [palvelu metodi parametrit transducer]
  (let [chan (chan)
        cb (fn [[_ vastaus]]
             (when-not (nil? vastaus)
               (put! chan (if transducer (into [] transducer vastaus) vastaus)))
             (close! chan))]

    (ajax-request {:uri             (str (polku) (name palvelu))
                   :method          metodi
                   :params          parametrit
                   :format          (transit-request-format {:handlers
                                                             {DateTime    (DateTimeHandler.)
                                                              UtcDateTime (DateTimeHandler.)}})
                   :response-format (transit-response-format {:handlers
                                                              {"dt" (fn [v]
                                                                      (pvm/->pvm-aika-sek v))}})
                   :handler         cb
                   :error-handler   (fn [[_ error]]
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

(defn virhe?
  "Tarkastaa sisältääkö palvelimen vastaus :failure avaimen, ja tai statuksen 500"
  [vastaus]
  (and (map? vastaus) (not (nil? (get vastaus :failure)))))

(defn laheta-liite!
  "Lähettää liitetiedoston palvelimen liitepolkuun. Palauttaa kanavan, josta voi lukea edistymisen.
  Kun liite on kokonaan lähetetty, kirjoitetaan sen tiedot kanavaan ja kanava suljetaan."
  [input-elementti]
  (let [ch (chan)
        xhr (doto (js/XMLHttpRequest.)
              (.open "POST" (str +polku+ "_/tallenna-liite")))
        siirto (.-upload xhr)
        form-data (js/FormData.)
        tiedostot (.-files input-elementti)]
    (dotimes [i (.-length tiedostot)]
      (.append form-data "liite" (aget tiedostot i)))

    (set! (.-onload xhr)
          (fn [result]
            (log "SAATIIN VASTAUS: " xhr)
            (put! ch {:nimi "rekka_kaatui.jpg"
                      :pikkukuva-url "/images/rekka_kaatui_thumbnail.jpg"
                      :tyyppi "image/jpeg"
                      :koko 70720})
            (close! ch)))

    (set! (.-onprogress siirto)
          (fn [e]
            (when (.-lengthComputable e)
              (put! ch (* 100 (/ (.-loaded e) (.-total e)))))))

    (.send xhr form-data)
    ch))


                        


                    
    
