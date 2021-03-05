(ns harja.asiakas.kommunikaatio
  "Palvelinkommunikaation utilityt, transit lähettäminen."
  (:require [reagent.core :as r]
            [ajax.core :refer [POST ajax-request transit-request-format transit-response-format] :as ajax]
            [cljs.core.async :refer [<! >! put! close! chan timeout]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.pvm :as pvm]
            [cognitect.transit :as t]
            [harja.loki :refer [log]]
            [harja.transit :as transit]
            [harja.domain.roolit :as roolit]
            [clojure.string :as str]
            [cljs-time.core :as time]
            [goog.string :as gstr]
            [harja.tyokalut.local-storage :as local-storage])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(defonce ^{:doc
           "Tähän atomiin tallennetaan tieto katkenneista yhteyksistä ja lähetetään
            palvelimelle logitettavaksi, kunhan yhteys palautuu."}
         yhteyskatkokset
  (local-storage/local-storage-atom
    :yhteyskatkokset
    []
    nil))

(def sailyta-max-katkosta 1000)

(defn- tallenna-yhteyskatkos! [palvelu]
  (when (< (count @yhteyskatkokset) sailyta-max-katkosta)
    (swap! yhteyskatkokset conj {:aika (pvm/nyt)
                                 :palvelu palvelu})))

(def +polku+ (let [host (.-host js/location)]
               (if (or (gstr/startsWith host "10.")
                       (#{"localhost" "localhost:3000" "localhost:8000"
                          "harja-test.solitaservices.fi"} host)
                       (gstr/contains host "googleusercontent"))
                 "/"
                 "/harja/")))
(defn polku []
  (str +polku+ "_/"))

(defn get-csrf-token
  "Hakee CSRF-tokenin DOMista."
  []
  (-> (.getElementsByTagName js/document "body")
      (aget 0)
      (.getAttribute "data-anti-csrf-token")))

(defn csrf-token
  "Yrittää löytää CSRF-tokenin DOMista niin kauan, että se löytyy."
  []
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

(defn yhteysvirhe? [vastaus]
  ;; cljs-ajax kirjaston oma käsite: status 0 kun pyyntö ei mene lainkaan palvelimelle (verkkoyhteys poikki)
  (= 0 (:status vastaus)))

(defn- kasittele-palvelinvirhe [palvelu vastaus]
  ;; Normaalitilanteessa ei pitäisi koskaan tulla ei oikeutta -virhettä. Voi tulla esim. jos frontin
  ;; ja backendin oikeustarkistukset eivät ole yhteneväiset. Tällöin halutaan näyttää käyttäjälle tieto
  ;; puutteellisista oikeuksista, jotta tiedetään virheen johtuvan nimenomaan oikeustarkistuksesta.
  (when (roolit/ei-oikeutta? (:response vastaus))
    (tapahtumat/julkaise! {:aihe :ei-oikeutta
                           :viesti (str "Puutteelliset oikeudet kutsuttaessa palvelua " (pr-str palvelu))}))

  (if (yhteysvirhe? vastaus)
    (kasittele-yhteyskatkos palvelu vastaus)
    (log "Palvelu " (pr-str palvelu) " palautti virheen: " (pr-str vastaus)))
  (tapahtumat/julkaise! (assoc vastaus :aihe :palvelinvirhe)))

(declare kasittele-istunto-vanhentunut)

(defn- kysely [palvelu metodi parametrit
               {:keys [transducer paasta-virhe-lapi? chan yritysten-maara uudelleenyritys-timeout] :as opts}]
  (let [cb (fn [[_ vastaus]]
             (when (some? vastaus)
               (cond
                 (= (:status vastaus) 302)
                 (do (kasittele-istunto-vanhentunut) ; Extranet-kirjautuminen vanhentunut
                     (close! chan))

                 (= (:status vastaus) 403) ; Harjan anti-CSRF-sessio vanhentunut (tod.näk)
                 (do (kasittele-istunto-vanhentunut)
                     (close! chan))

                 ;; Yhteysvirhe, jota halutaan yrittää uudelleen jos yrityksiä on vielä jäljellä
                 (and (yhteysvirhe? vastaus) (contains? #{:post :get} metodi) (< yritysten-maara 5))
                 (kysely palvelu metodi parametrit (assoc opts
                                                     :yritysten-maara (let [yritysten-maara (:yritysten-maara opts)]
                                                                        (+ (or yritysten-maara 0) 1))
                                                     :uudelleenyritys-timeout
                                                     (let [timeout (:uudelleenyritys-timeout opts)]
                                                       (+ (or timeout 2000) 2000))))

                 (and (virhe? vastaus) (not paasta-virhe-lapi?))
                 (do (kasittele-palvelinvirhe palvelu vastaus)
                     (close! chan)) ; Kutsujalle palautuu nil

                 :default ; Pyyntö onnistui ja vastaus oli ok
                 (do (put! chan (if transducer (into [] transducer vastaus) vastaus))
                     (close! chan)))))]
    (go
      (when uudelleenyritys-timeout
        (<! (timeout uudelleenyritys-timeout)))
      (if-let [testipalvelu @testmode]
        (do
          (log "Haetaan testivastaus palvelulle: " palvelu)
          (if-let [testivastaus-ch (testipalvelu palvelu parametrit)]
            (if-let [testivastaus (<! testivastaus-ch)]
              (>! chan testivastaus)
              (close! chan))
            (close! chan)))
        (ajax-request {:uri (str (polku) (name palvelu))
                       :method metodi
                       :params parametrit
                       :headers {"X-CSRF-Token" (<! (csrf-token))}
                       :format (transit-request-format transit/write-optiot)
                       :response-format (transit-response-format {:reader (t/reader :json transit/read-optiot)
                                                                  :raw true})
                       :handler cb

                       :error-handler (fn [[resp error]]
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
                      (log "Liitelähetys epäonnistui: " txt)
                      (put! ch {:error :liitteen-lahetys-epaonnistui
                                :viesti (if (= txt "Virus havaittu")
                                          txt
                                          "tiedostotyyppi ei ole sallittu")}))
                0 (kasittele-yhteyskatkos :tallenna-liite (.-responseText request))
                (do
                  (log "Liitelähetys epäonnistui: " (pr-str (.-responseText request)))
                  (put! ch {:error :liitteen-lahetys-epaonnistui})))
              (close! ch))))

    (set! (.-onerror xhr)
          (fn [event]
            (let [request (.-target event)]
              (let [txt (.-responseText request)]
                (log "Liitelähetys epäonnistui: " txt)
                (put! ch {:error :liitteen-lahetys-epaonnistui})))))

    (set! (.-onprogress siirto)
          (fn [e]
            (when (.-lengthComputable e)
              (put! ch (* 100 (/ (.-loaded e) (.-total e)))))))

    (.send xhr form-data)
    ch))

(defn laheta-excel!
  "Lähettää liitetiedoston palvelimen liitepolkuun. Palauttaa kanavan, josta voi lukea edistymisen.
  Kun liite on kokonaan lähetetty, kirjoitetaan sen tiedot kanavaan ja kanava suljetaan."
  [url input-elementti urakka-id]
  (let [ch (chan)
        xhr (doto (js/XMLHttpRequest.)
              (.open "POST" (str +polku+ "_/" url)))
        siirto (.-upload xhr)
        form-data (js/FormData.)
        tiedostot (.-files input-elementti)]
    (.append form-data "urakka-id" urakka-id)
    (.append form-data "file" (aget tiedostot 0))

    (set! (.-onload xhr)
          (fn [event]
            (let [request (.-target event)]
              (case (.-status request)
                200 (let [transit-json (.-responseText request)
                          transit (transit/lue-transit transit-json)]
                      (put! ch transit))
                413 (do
                      (log "Tiedoston epäonnistui: " (pr-str (.-responseText request)))
                      (put! ch {:error :liitteen-lahetys-epaonnistui :viesti "liite on liian suuri, max. koko 32MB"}))
                500 (let [txt (.-responseText request)]
                      (log "Tiedoston epäonnistui: " txt)
                      (put! ch {:error :liitteen-lahetys-epaonnistui
                                :viesti (if (= txt "Virus havaittu")
                                          txt
                                          "tiedostotyyppi ei ole sallittu")}))
                0 (kasittele-yhteyskatkos :tallenna-liite (.-responseText request))
                (do
                  (log "Tiedoston epäonnistui: " (pr-str (.-responseText request)))
                  (put! ch {:error :liitteen-lahetys-epaonnistui})))
              (close! ch))))

    (set! (.-onerror xhr)
          (fn [event]
            (let [request (.-target event)]
              (let [txt (.-responseText request)]
                (log "Tiedoston epäonnistui: " txt)
                (put! ch {:error :liitteen-lahetys-epaonnistui})))))

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

(defn wmts-polku-mml []
  (str +polku+ "wmts/"))

(defn wmts-polku-livi []
  (str +polku+ "wmtslivi/"))

(defn karttakuva-url [& parametrit]
  (str (polku) "karttakuva?" (yhdista-parametrit parametrit)))

(defn pingaa-palvelinta []
  (post! :ping {}))

(def yhteys-palautui-hetki-sitten (r/atom false))
(def yhteys-katkennut? (r/atom false))
(def istunto-vanhentunut? (r/atom false))
(def pingaus-kaynnissa? (r/atom false))
(def yhteysvirheiden-lahetys-kaynnissa? (r/atom false))
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

(defn- kasittele-yhteyskatkos [palvelu vastaus]
  (log "Yhteys katkesi kutsuttaessa palvelua " (pr-str palvelu) ". Vastaus: " (pr-str vastaus))
  (when palvelu (tallenna-yhteyskatkos! palvelu))
  (reset! yhteys-katkennut? true)
  (reset! nykyinen-pingausvali-millisekunteina
          yhteys-katkennut-pingausvali-millisekunteina)
  (reset! yhteys-palautui-hetki-sitten false))

(defn- kasittele-istunto-vanhentunut []
  (reset! istunto-vanhentunut? true))

(defn kysy-pois-kytketyt-ominaisuudet! [pk-atomi]
  (when-not @pk-atomi
    (go
      (let [pko (<! (post! :pois-kytketyt-ominaisuudet {}))]
        (reset! pk-atomi pko)
        (log "pois kytketyt ominaisuudet:" (pr-str pko))))))

(defn lisaa-kuuntelija-selaimen-verkkotilalle []
  (.addEventListener js/window "offline" #(kasittele-yhteyskatkos nil nil)))

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
          sallittu-viive ([_] (kasittele-yhteyskatkos :ping nil)))
        (recur)))))

(defn kaynnista-yhteysvirheiden-raportointi []
  (when-not @yhteysvirheiden-lahetys-kaynnissa?
    (log "Käynnistetään yhteysvirheiden lähetys")
    (reset! yhteysvirheiden-lahetys-kaynnissa? true)
    (go-loop []
      (when-not (empty? @yhteyskatkokset)
        (log "Lähetetään yhteysvirheet: " (count @yhteyskatkokset) " kpl.")
        (let [vastaus (<! (post! :raportoi-yhteyskatkos {:yhteyskatkokset @yhteyskatkokset}))]
          (if-not (virhe? vastaus)
            (do (log "Yhteyskatkostiedot lähetetty!")
                (reset! yhteyskatkokset []))
            (log "Yhteysvirheitä ei voitu lähettää. Yritetään kohta uudelleen."))))
      (<! (timeout 10000))
      (recur))))

(defn url-parametri
  "Muuntaa annetun Clojure datan transitiksi ja URL enkoodaa sen"
  [clj-data]
  (-> clj-data
      transit/clj->transit
      gstr/urlEncode))

(defn varustekortti-url [alkupvm tietolaji tunniste]
  (-> 
    "https://extranet.vayla.fi/trkatselu/TrKatseluServlet?page=varuste&tpvm=<pvm>&tlaji=<tietolaji>&livitunniste=<tunniste>&act=haku"
    (str/replace "<pvm>" (pvm/pvm alkupvm))
    (str/replace "<tietolaji>" tietolaji)
    (str/replace "<tunniste>" tunniste)))


(defn kehitysymparistossa? []
  "Tarkistaa ollaanko kehitysympäristössä"
  (let [host (.-host js/location)]
    (or (gstr/startsWith host "10.10.")
        (#{"localhost" "localhost:3000" "localhost:8000" "harja-c7-dev.lxd:8000"
           "harja-test.solitaservices.fi"
           "testiextranet.vayla.fi"} host)
        (gstr/contains host "googleusercontent"))))
