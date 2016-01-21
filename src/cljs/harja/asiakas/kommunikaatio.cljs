(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [ajax.core :refer [ajax-request transit-request-format transit-response-format]]
            [cljs.core.async :refer [put! close! chan timeout]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.pvm :as pvm]
            [cognitect.transit :as t]
            [harja.loki :refer [log]]
            [harja.transit :as transit]
            [harja.domain.roolit :as roolit]
            [clojure.string :as str]
            [harja.virhekasittely :as vk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def +polku+ (let [host (.-host js/location)]
               (if (#{"10.0.2.2" "10.0.2.2:8000" "10.0.2.2:3000" "localhost" "localhost:3000" "localhost:8000" "harja-test.solitaservices.fi"} host)
                 "/"
                 "/harja/")))
(defn polku []
  (str +polku+ "_/"))

(defn get-csrf-token []
  (-> (.getElementsByTagName js/document "body")
      (aget 0)
      (.getAttribute "data-anti-csrf-token")))

(defn csrf-token []
  (go-loop [token (get-csrf-token)]
    (if token
      token
      (do (<! (timeout 100))
          (recur (get-csrf-token))))))

(defn virhe?
  "Tarkastaa sisältääkö palvelimen vastaus :failure avaimen, statuksen 500 tai on EiOikeutta viesti"
  [vastaus]
  (or (roolit/ei-oikeutta? vastaus)
      (and (map? vastaus)
           (some (partial contains? vastaus) [:failure :virhe :error]))))
           ; Aiemmin oli hellempi tapa tarkistaa, että näiden avainten
           ; sisällä on jokin loogisesti tosi arvo. On kuitenkin mahdollista,
           ; että serveri palauttaa esim. {:error nil}

(def testmode {})

(defn- kysely [palvelu metodi parametrit transducer paasta-virhe-lapi?]
  (let [chan (chan)
        cb (fn [[_ vastaus]]
             (when-not (nil? vastaus)
               (if (and (virhe? vastaus) (not paasta-virhe-lapi?))
                 (do (log "Palvelu " (pr-str palvelu) " palautti virheen: " (pr-str vastaus))
                     (tapahtumat/julkaise! (assoc vastaus :aihe :palvelinvirhe))
                     ;; kaataa seleniumin testiajon, oikea ongelma taustalla, pidä toistaiseksi kommentoituna
                     #_(vk/arsyttava-virhe (str "Palvelinkutsussa virhe: " vastaus)))
                 (put! chan (if transducer (into [] transducer vastaus) vastaus))))
             (close! chan))]
    (go
      (if (testmode palvelu)
        (>! chan (testmode palvelu))
        (ajax-request {:uri             (str (polku) (name palvelu))
                       :method          metodi
                       :params          parametrit
                       :headers         {"X-CSRF-Token" (<! (csrf-token))}
                       :format          (transit-request-format transit/write-optiot)
                       :response-format (transit-response-format {:reader (t/reader :json transit/read-optiot)
                                                                  :raw    true})
                       :handler         cb
                       :error-handler   (fn [[_ error]]
                                          (tapahtumat/julkaise! (assoc error :aihe :palvelinvirhe))
                                          (close! chan))})))
    chan))

(defn post!
  "Lähetä HTTP POST -palvelupyyntö palvelimelle ja palauta kanava, josta vastauksen voi lukea. 
Kolmen parametrin versio ottaa lisäksi transducerin, jolla tulosdata vektori muunnetaan ennen kanavaan kirjoittamista."
  ([service payload] (post! service payload nil false))
  ([service payload transducer] (post! service payload transducer false))
  ([service payload transducer paasta-virhe-lapi?]
   (kysely service :post payload transducer paasta-virhe-lapi?)))



(defn get!
  "Lähetä HTTP GET -palvelupyyntö palvelimelle ja palauta kanava, josta vastauksen voi lukea. 
Kahden parametrin versio ottaa lisäksi transducerin jolla tulosdata vektori muunnetaan ennen kanavaan kirjoittamista."
  ([service] (get! service nil false))
  ([service transducer] (get! service transducer false))
  ([service transducer paasta-virhe-lapi?]
   (kysely service :get nil transducer paasta-virhe-lapi?)))

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

(defn pingaa-palvelinta []
  (post! :ping {}))

(def pingausvali-millisekunteina (* 1000 30))

(defn kaynnista-palvelimen-pingaus []
  (go
    (loop []
      (<! (timeout pingausvali-millisekunteina))
      (log/debug "Pingataan palvelinta.")
      (let [vastaus (<! (pingaa-palvelinta))]
        (if (= vastaus :pong)
          (log/debug "Pingaus onnistui. Vastaus: " (pr-str vastaus))
          (log/debug "Pingaus epäonnistui! Vastaus: " (pr-str vastaus)))
        (recur)))))


(defn wmts-polku []
  (str +polku+ "wmts/"))
