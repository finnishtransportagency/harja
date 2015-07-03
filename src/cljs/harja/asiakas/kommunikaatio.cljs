(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [ajax.core :refer [ajax-request transit-request-format transit-response-format]]
            [cljs.core.async :refer [put! close! chan]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.pvm :as pvm]
            [cognitect.transit :as t]
            [harja.loki :refer [log]]
            [harja.transit :as transit])
  (:require-macros [cljs.core.async.macros :refer [go]])
  )

(def +polku+ (let [host (.-host js/location)]
               (if (#{"localhost" "localhost:3000" "harja-test.solitaservices.fi"} host)
                 "/"
                 "/harja/")))
(defn polku []
  (str +polku+ "_/"))


(defn- kysely [palvelu metodi parametrit transducer]
  (let [chan (chan)
        cb (fn [[_ vastaus]]
             (when-not (nil? vastaus)
               (put! chan (if transducer (into [] transducer vastaus) vastaus)))
             (close! chan))]

    (ajax-request {:uri             (str (polku) (name palvelu))
                   :method          metodi
                   :params          parametrit
                   :format          (transit-request-format transit/write-optiot)
                   :response-format (transit-response-format transit/read-optiot)
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
  [input-elementti urakka-id]
  (let [ch (chan)
        xhr (doto (js/XMLHttpRequest.)
              (.open "POST" (str +polku+ "_/tallenna-liite")))
        siirto (.-upload xhr)
        form-data (js/FormData.)
        tiedostot (.-files input-elementti)]
    (.append form-data "urakka" urakka-id)
    (dotimes [i (.-length tiedostot)]
      (.append form-data "liite" (aget tiedostot i)))

    (set! (.-onload xhr)
          (fn [event]
            (let [request (.-target event)]
              (if (= 200 (.-status request))
                (let [transit-json (.-responseText request)
                      transit (transit/lue-transit transit-json)]
                  (log "SAATIIN VASTAUS: " (pr-str transit))
                  (put! ch transit)
                  (close! ch))
                (do (put! ch {:error :liitteen-lahetys-epaonnistui})
                    (close! ch))))))

    (set! (.-onprogress siirto)
          (fn [e]
            (when (.-lengthComputable e)
              (put! ch (* 100 (/ (.-loaded e) (.-total e)))))))

    (.send xhr form-data)
    ch))

(defn liite-url [liite-id]
  (str (polku) "lataa-liite?id=" liite-id))

(defn pikkukuva-url [liite-id]
  (str (polku) "lataa-pikkukuva?id=" liite-id))

(defn wmts-polku []
  (str +polku+ "wmts/"))
