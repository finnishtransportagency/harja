(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [reagent.core :as r]
            [ajax.core :refer [ajax-request transit-request-format transit-response-format] :as ajax]
            [cljs.core.async :refer [put! close! chan timeout]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.pvm :as pvm]
            [cognitect.transit :as t]
            [harja.loki :refer [log]]
            [harja.transit :as transit]
            [harja.domain.roolit :as roolit]
            [clojure.string :as str]
            [cljs-time.core :as time]
            [goog.string :as gstr])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(def +polku+ (let [host (.-host js/location)]
               (if (or (gstr/startsWith host "10.")
                       (re-matches #".*\.lxd:8000$" host)
                       (#{"localhost" "localhost:3000" "localhost:8000"
                          "harja-test.solitaservices.fi"} host))
                 "/"
                 "/harja/")))

(log "polku" +polku+ "koska" (pr-str (re-matches #".*\.lxd:8000$" (.-host js/location))))

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
  "Tarkastaa onko vastaus tyhjä, sisältääkö se :failure, :virhe, tai :error avaimen, tai on EiOikeutta viesti"
  [vastaus]
  (or (nil? vastaus)
      (roolit/ei-oikeutta? (:response vastaus))
      (and (map? vastaus)
           (some (partial contains? vastaus) [:failure :virhe :error]))))

;; Testmoden voi asettaa funktioksi, jolle annetaan palvelu ja payload ja palauttaa kanavan
(def testmode (atom nil))

(declare kasittele-yhteyskatkos)

(defn- kasittele-palvelinvirhe [palvelu vastaus]
  ;; Normaalitilanteessa ei pitäisi koskaan tulla ei oikeutta -virhettä. Voi tulla esim. jos frontin
  ;; ja backendin oikeustarkistukset eivät ole yhteneväiset. Tällöin halutaan näyttää käyttäjälle tieto
  ;; puutteellisista oikeuksista, jotta tiedetään virheen johtuvan nimenomaan oikeustarkistuksesta.
  (when (roolit/ei-oikeutta? (:response vastaus))
    (tapahtumat/julkaise! {:aihe :ei-oikeutta
                           :viesti (str "Puutteelliset oikeudet kutsuttaessa palvelua " (pr-str palvelu))}))

  (if (= 0 (:status vastaus))
    ;; 0 status tulee kun ajax kutsu epäonnistuu, verkko on poikki
    ;; PENDING: tässä tilanteessa voisimme jättää requestin pendaamaan ja yrittää sitä uudelleen
    ;; kun verkkoyhteys on taas saatu takaisin.
    (kasittele-yhteyskatkos vastaus)

    ;; muuten, logita virhe
    (log "Palvelu " (pr-str palvelu) " palautti virheen: " (pr-str vastaus)))
  (tapahtumat/julkaise! (assoc vastaus :aihe :palvelinvirhe)))

(declare kasittele-istunto-vanhentunut)

(defn extranet-virhe? [vastaus]
  (= 0 (:status vastaus)))

(defn- kysely [palvelu metodi parametrit
               {:keys [transducer paasta-virhe-lapi? chan yritysten-maara] :as opts}]
  (let [cb (fn [[_ vastaus]]
             (when (some? vastaus)
               (cond
                 (= (:status vastaus) 302)
                 (do (kasittele-istunto-vanhentunut)
                     (close! chan))

                 (and (virhe? vastaus) (not paasta-virhe-lapi?))
                 (do (kasittele-palvelinvirhe palvelu vastaus)
                     (close! chan))

                 (and (extranet-virhe? vastaus) (contains? #{:post :get} metodi) (< yritysten-maara 4))
                 (kysely palvelu metodi parametrit (update opts :yritysten-maara (fnil inc 0)))

                 :default
                 (do (put! chan (if transducer (into [] transducer vastaus) vastaus))
                     (close! chan)))
               ;; else
               (close! chan)))]
    (go
      (if-let [testipalvelu @testmode]
        (do
          (log "Haetaan testivastaus palvelulle: " palvelu)
          (if-let [testivastaus-ch (testipalvelu palvelu parametrit)]
            (>! chan (<! testivastaus-ch))
            (close! chan)))
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
  ([service payload transducer paasta-virhe-lapi?] (post! service payload transducer paasta-virhe-lapi? (chan) 0))
  ([service payload transducer paasta-virhe-lapi? kanava yritysten-maara]
   (kysely service :post payload {:transducer transducer
                                  :paasta-virhe-lapi? paasta-virhe-lapi?
                                  :chan kanava
                                  :yritysten-maara yritysten-maara})))

(defn post!*
  "Läheta HTTT POST -palvelupyyntö ja anna optiot mäpissä."
  [service payload options]
  (kysely service :post payload (merge {:chan (chan)} options)))

(defn get!
  "Lähetä HTTP GET -palvelupyyntö palvelimelle ja palauta kanava, josta vastauksen voi lukea.
Kahden parametrin versio ottaa lisäksi transducerin jolla tulosdata vektori muunnetaan ennen kanavaan kirjoittamista."
  ([service] (get! service nil false))
  ([service transducer] (get! service transducer false))
  ([service transducer paasta-virhe-lapi?] (get! service transducer paasta-virhe-lapi? (chan) 0))
  ([service transducer paasta-virhe-lapi? kanava yritysten-maara]
   (kysely service :get nil {:transducer transducer
                             :paasta-virhe-lapi? paasta-virhe-lapi?
                             :chan kanava
                             :yritysten-maara yritysten-maara})))

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
                500 (let [txt (.-responseText request)]
                      (log "Liitelähetys epäonnistui: "  txt)
                      (put! ch {:error :liitteen-lahetys-epaonnistui
                                :viesti (if (= txt "Virus havaittu")
                                          txt
                                          "tiedostotyyppi ei ole sallittu")}))
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

(defn excel-url [tyyppi & parametrit]
  (str (polku) "excel?_=" (name tyyppi) "&"
       (yhdista-parametrit parametrit)))

(defn wmts-polku []
  (str +polku+ "wmts/"))

(defn karttakuva-url [& parametrit]
  (str (polku) "karttakuva?" (yhdista-parametrit parametrit)))

(defn pingaa-palvelinta []
  (post! :ping {}))

(def yhteys-palautui-hetki-sitten (r/atom false))
(def yhteys-katkennut? (r/atom false))
(def istunto-vanhentunut? (r/atom false))
(def pingaus-kaynnissa? (r/atom false))
(def normaali-pingausvali-millisekunteina (* 1000 20))
(def yhteys-katkennut-pingausvali-millisekunteina 2000)
(def nykyinen-pingausvali-millisekunteina (r/atom normaali-pingausvali-millisekunteina))

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
               (<! (timeout 3000))
               (reset! yhteys-palautui-hetki-sitten false))
             (<! (timeout @nykyinen-pingausvali-millisekunteina))
             (let [pingauskanava (pingaa-palvelinta)
                   sallittu-viive (timeout 10000)]
               (alt!
                 pingauskanava ([vastaus] (when (= vastaus :pong)
                                            (kasittele-onnistunut-pingaus)))
                 sallittu-viive ([_] (kasittele-yhteyskatkos nil)))
               (recur)))))

(defn url-parametri
  "Muuntaa annetun Clojure datan transitiksi ja URL enkoodaa sen"
  [clj-data]
  (-> clj-data
      transit/clj->transit
      gstr/urlEncode))

(defn varustekortti-url [alkupvm tietolaji tunniste]
  (->
    "https://testiextranet.liikennevirasto.fi/trkatselu/TrKatseluServlet?page=varuste&tpvm=<pvm>&tlaji=<tietolaji>&livitunniste=<tunniste>&act=haku"
    (str/replace "<pvm>" (pvm/pvm alkupvm))
    (str/replace "<tietolaji>" tietolaji)
    (str/replace "<tunniste>" tunniste)))


(defn kehitysymparistossa? []
  "Tarkistaa ollaanko kehitysympäristössä"
  (let [host (.-host js/location)]
    (or (gstr/startsWith host "10.10.")
        (#{"localhost" "localhost:3000" "localhost:8000" "harja-c7-dev.lxd:8000"
           "harja-test.solitaservices.fi"
           "harja-dev1" "harja-dev2" "harja-dev3" "harja-dev4" "harja-dev5" "harja-dev6"
           "testiextranet.liikennevirasto.fi"} host))))
