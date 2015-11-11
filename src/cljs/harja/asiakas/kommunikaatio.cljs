(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [ajax.core :refer [ajax-request transit-request-format transit-response-format]]
            [cljs.core.async :refer [put! close! chan]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.pvm :as pvm]
            [cognitect.transit :as t]
            [harja.loki :refer [log]]
            [harja.transit :as transit]
            [harja.domain.roolit :as roolit]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def +polku+ (let [host (.-host js/location)]
               (if (#{"localhost" "localhost:3000" "localhost:8000" "harja-test.solitaservices.fi"} host)
                 "/"
                 "/harja/")))
(defn polku []
  (str +polku+ "_/"))

(defn get-csrf-token []
  (-> (.getElementsByTagName js/document "body")
      (aget 0)
      (.getAttribute "data-anti-csrf-token")))

(defn- kysely [palvelu metodi parametrit transducer]
  (let [chan (chan)
        cb (fn [[_ vastaus]]
             (when-not (nil? vastaus)
               (put! chan (if transducer (into [] transducer vastaus) vastaus)))
             (close! chan))]

    ;(log "X-XSRF-Token on " (.-anti_csrf_token js/window))
    
    (ajax-request {:uri             (str (polku) (name palvelu))
                   :method          metodi
                   :params          parametrit
                   :headers         {"X-CSRF-Token" (get-csrf-token)}
                   :format          (transit-request-format transit/write-optiot)
                   :response-format (transit-response-format {:reader (t/reader :json transit/read-optiot)
                                                              :raw    true})
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
  "Tarkastaa sisältääkö palvelimen vastaus :failure avaimen, statuksen 500 tai on EiOikeutta viesti"
  [vastaus]
  (or (roolit/ei-oikeutta? vastaus)
      (and (map? vastaus)
           (or
             (true? (some #(= :failure %) (keys vastaus)))      ; Aiemmin oli hellempi tapa tarkistaa, että näiden avainten
             (true? (some #(= :virhe %) (keys vastaus)))        ; sisällä on jokin loogisesti tosi arvo. On kuitenkin mahdollista,
             (true? (some #(= :error %) (keys vastaus)))))))    ; että serveri palauttaa esim. {:error nil}

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
              (case (.-status request)
                200 (let [transit-json (.-responseText request)
                          transit (transit/lue-transit transit-json)]
                      (put! ch transit))
                413 (do
                      (log "Liitelähetys epäonnistui: " (pr-str (.-responseText request)))
                      (put! ch {:error :liitteen-lahetys-epaonnistui :viesti "liite on liian suuri, max. koko 32MB"}))
                500 (do
                      (log "Liitelähetys epäonnistui: "  (pr-str  (.-responseText request)))
                      (put! ch {:error :liitteen-lahetys-epaonnistui :viesti "tiedostotyyppi ei ole sallittu"}))
                (do
                  (log "Liitelähetys epäonnistui: " (pr-str (.-responseText request)))
                  (put! ch {:error :liitteen-lahetys-epaonnistui})))
              (close! ch))))

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

(defn pdf-url [tyyppi & parametrit]
  (str (polku) "pdf?_=" (name tyyppi)
       "&" (str/join "&"
                     (map (fn [[nimi arvo]]
                            (str (name nimi) "=" arvo))
                          (partition 2 parametrit))))) 

(defn wmts-polku []
  (str +polku+ "wmts/"))
