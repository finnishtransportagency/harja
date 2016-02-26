(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [reagent.core :refer [atom]]
            [ajax.core :refer [ajax-request transit-request-format transit-response-format]]
            [cljs.core.async :refer [put! close! chan timeout]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.pvm :as pvm]
            [cognitect.transit :as t]
            [harja.loki :refer [log]]
            [harja.transit :as transit]
            [harja.domain.roolit :as roolit]
            [clojure.string :as str]
            [harja.virhekasittely :as vk]
            [cljs-time.core :as time])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

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

(declare kasittele-yhteyskatkos)

(defn- kasittele-palvelinvirhe [palvelu vastaus]
  (if (= 0 (:status vastaus))
    ;; 0 status tulee kun ajax kutsu epäonnistuu, verkko on poikki
    ;; PENDING: tässä tilanteessa voisimme jättää requestin pendaamaan ja yrittää sitä uudelleen
    ;; kun verkkoyhteys on taas saatu takaisin.
    (kasittele-yhteyskatkos vastaus)

    ;; muuten, logita virhe
    (log "Palvelu " (pr-str palvelu) " palautti virheen: " (pr-str vastaus)))
  (tapahtumat/julkaise! (assoc vastaus :aihe :palvelinvirhe)))

(declare kasittele-istunto-vanhentunut)

(defn- kysely [palvelu metodi parametrit transducer paasta-virhe-lapi?]
  (let [chan (chan)
        cb (fn [[_ vastaus]]
             (when-not (nil? vastaus)
               (cond
                 (= (:status vastaus) 302)
                 (kasittele-istunto-vanhentunut)
                 (and (virhe? vastaus) (not paasta-virhe-lapi?))
                 (kasittele-palvelinvirhe palvelu vastaus)
                 :default
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
                       :error-handler   (fn [[resp error]]
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

(defn- yhdista-parametrit [parametrit]
  (str/join "&"
            (map (fn [[nimi arvo]]
                   (str (name nimi) "=" arvo))
                 (partition 2 parametrit))))

(defn pdf-url [tyyppi & parametrit]
  (str (polku) "pdf?_=" (name tyyppi) "&"
       (yhdista-parametrit parametrit)))

(defn wmts-polku []
  (str +polku+ "wmts/"))

(defn karttakuva-url [& parametrit]
  (str (polku) "karttakuva?" (yhdista-parametrit parametrit)))

(defn pingaa-palvelinta []
  (post! :ping {}))

(def yhteys-palautui-hetki-sitten (atom false))
(def yhteys-katkennut? (atom false))
(def istunto-vanhentunut? (atom false))
(def pingaus-kaynnissa? (atom false))
(def normaali-pingausvali-millisekunteina (* 1000 20))
(def yhteys-katkennut-pingausvali-millisekunteina 2000)
(def nykyinen-pingausvali-millisekunteina (atom normaali-pingausvali-millisekunteina))

(defn- kasittele-onnistunut-pingaus []
  (reset! istunto-vanhentunut? false)
  (when (true? @yhteys-katkennut?)
    (reset! yhteys-palautui-hetki-sitten true)
    (reset! nykyinen-pingausvali-millisekunteina
            normaali-pingausvali-millisekunteina)
    (reset! yhteys-katkennut? false)))

(defn- kasittele-yhteyskatkos [vastaus]
  (log "Yhteys katkesi! Vastaus: " (pr-str vastaus))
  (reset! yhteys-katkennut? true)
  (reset! nykyinen-pingausvali-millisekunteina
          yhteys-katkennut-pingausvali-millisekunteina)
  (reset! yhteys-palautui-hetki-sitten false))

(defn- kasittele-istunto-vanhentunut []
  (reset! istunto-vanhentunut? true))

(defn lisaa-kuuntelija-selaimen-verkkotilalle []
  (.addEventListener js/window "offline" #(kasittele-yhteyskatkos nil)))

(defn kaynnista-palvelimen-pingaus []
  (when-not @pingaus-kaynnissa?
    (log "Käynnistetään palvelimen pingaus " (/ @nykyinen-pingausvali-millisekunteina 1000) " sekunnin valein")
    (lisaa-kuuntelija-selaimen-verkkotilalle)
    (reset! pingaus-kaynnissa? true)
    (go-loop []
             (when @yhteys-palautui-hetki-sitten
               (<! (timeout 5000))
               (reset! yhteys-palautui-hetki-sitten false))
      (<! (timeout @nykyinen-pingausvali-millisekunteina))
      (let [pingauskanava (pingaa-palvelinta)
            sallittu-viive (timeout 10000)]
        (alt!
          pingauskanava ([vastaus] (when (= vastaus :pong)
                                     (kasittele-onnistunut-pingaus)))
          sallittu-viive ([_] (kasittele-yhteyskatkos nil)))
        (recur)))))

(defn varustekortti-url [alkupvm tietolaji tunniste]
  (->
    "https://testiextranet.liikennevirasto.fi/trkatselu/TrKatseluServlet?page=varuste&tpvm=<pvm>&tlaji=<tietolaji>&livitunniste=<tunniste>&act=haku"
    (str/replace "<pvm>" (pvm/pvm alkupvm))
    (str/replace "<tietolaji>" tietolaji)
    (str/replace "<tunniste>" tunniste)))

