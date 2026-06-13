(ns harja.palvelin.palvelut.kartta-cache
  "Cachetus Harjan karttaan, skipataan 200-3000ms kuvahaut kun kartta liikkuu"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [com.stuartsierra.component :as component]

            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]])

  (:import [java.io File]
           [java.nio.file CopyOption Files StandardCopyOption]
           [java.security MessageDigest]))

(def ^{:doc "Jos asetuksissa ei ole määritelty upstream osoitetta"}
  ^:private oletus-upstream
  "https://tiles.kartat.kapsi.fi/taustakartta")


(defn- sha256
  "Upstream-URL kyselyparametreineen toimii cache-avaimena.
  Sama query tuottaa aina saman tiedostonimen."
  [^String arvo]
  (let [digest (.digest
                 (MessageDigest/getInstance "SHA-256")
                 (.getBytes arvo "UTF-8"))]
    (apply str
      (map #(format "%02x" (bit-and % 0xff))
        digest))))


(defn- ttl-ms
  "Asetuksissa annettu cache-aika päivistä -> millisekunneiksi. Default 21 päivää."
  [asetukset]
  (* (long (or (:ttl-paivat asetukset) 21)) 24 60 60 1000))


(defn- cache-hakemisto
  "Palauttaa cache-hakemiston ja luo sen tarvittaessa. Polkuna toimii projektin root."
  [asetukset]
  (doto
    (io/file
      (or (:hakemisto asetukset)
        (str (System/getProperty "java.io.tmpdir")
          File/separator
          "harja-kartta-cache")))
    (.mkdirs)))


(defn- tuore?
  "Cache-osuma hyväksytään jos tiedosto löytyy ja on TTL:n sisällä.
  Vanhentunut tiedosto jätetään levylle STALE-vastausta varten."
  [^File tiedosto ttl]
  (and (.isFile tiedosto)
    (< (- (System/currentTimeMillis)
         (.lastModified tiedosto))
      ttl)))


(defn- kuvavastaus
  "Muodostetaan PNG-tiedostosta ringuli vastauksen.
  X-Map-Cache kertoo selaimessa, tuliko vastaus cachesta vai upstreamista."
  [^File tiedosto ttl cache-tila]
  {:status 200
   :headers {"Content-Type" "image/png"
             "Content-Length" (str (.length tiedosto))
             "Cache-Control" (str "public, max-age=" (quot ttl 1000))
             "X-Map-Cache" cache-tila}
   :body (Files/readAllBytes (.toPath tiedosto))})


(defn- tallenna!
  "Tallennetaan ensin väliaikaistiedostoon ja siirretään lopuksi 
  varsinaiseksi cache-tiedostoksi, jotta keskeneräistä PNG:tä ei tarjoilla käyttäjille"
  [^File cache-tiedosto ^bytes data]
  (let [temp-tiedosto
        (File/createTempFile
          "kartta-"
          ".tmp"
          (.getParentFile cache-tiedosto))]
    (try
      (with-open [ulos (io/output-stream temp-tiedosto)]
        (.write ulos data))

      (Files/move
        (.toPath temp-tiedosto)
        (.toPath cache-tiedosto)
        (into-array
          CopyOption
          [StandardCopyOption/REPLACE_EXISTING]))

      (finally
        (when (.exists temp-tiedosto)
          (.delete temp-tiedosto))))))


(defn- poista-vanhat!
  "Poistetaan TTL:n ylittäneet tiedostot.
  Tätä kutsutaan tällä hetkellä vain komponentin käynnistyessä."
  [^File hakemisto ttl]
  (doseq [^File tiedosto (or (seq (.listFiles hakemisto)) [])]
    (when (and (.isFile tiedosto)
            (not (tuore? tiedosto ttl)))
      (.delete tiedosto))))


(defn- hae-karttakuva
  "Ring-käsittelijä karttakuvan hakemiseen ja tiedostocachetukseen."
  [asetukset request]
  ;; Käyttäjä on todennettu, karttakuvan lukeminen
  ;; ei tarvitse erillistä toimintokohtaista oikeustarkistusta
  (oikeudet/ei-oikeustarkistusta!)

  (cond
    (not= :get (:request-method request))
    {:status 405
     :headers {"Allow" "GET"}
     :body "Method not allowed"}

    (str/blank? (:query-string request))
    {:status 400
     :body "WMS query parameters missing"}

    :else
    ;; Muodostetaan cache tiedostonimi
    ;; Jos parametrien järjestys muuttuu, syntyy eri välimuistitiedosto
    (let [query-string (:query-string request)
          upstream-url (or (:upstream-url asetukset) oletus-upstream)
          url (str upstream-url "?" query-string)
          hakemisto (cache-hakemisto asetukset)
          ttl (ttl-ms asetukset)
          cache-tiedosto (io/file hakemisto (str (sha256 url) ".png"))]

      ;; Palautetaan tuore tiedosto ilman ulkoista kutsua
      (if (tuore? cache-tiedosto ttl)
        (kuvavastaus cache-tiedosto ttl "HIT")

        ;; Cache osumaa ei ollut, joten haetaan kuva 
        (let [{:keys [status headers body error]}
              @(http/get url
                 {:as :byte-array
                  :headers {"User-Agent" "Mozilla/5.0"
                            "Accept" "image/png,image/*;q=0.8,*/*;q=0.5"}
                  :connect-timeout 5000
                  :timeout 20000})]

          (when-not (= status 200)
            (log/error "Kapsi palautti virheen:"
              {:status status
               :headers headers
               :error error
               :body (when body (String. ^bytes body "UTF-8"))
               :url url}))

          (cond
            ;; Onnistunut vastaus tallennetaan välimuistiin
            (and (nil? error) (= status 200))
            (do
              (tallenna! cache-tiedosto body)
              (kuvavastaus cache-tiedosto ttl "MISS"))

            ;; Upstream alhaalla, mutta vanha kuva löytyy
            (.isFile cache-tiedosto)
            (do
              (log/warn error "Kartan lataus epäonnistui, palautetaan vanha cache: " status)
              (kuvavastaus cache-tiedosto ttl "STALE"))

            :else
            (do
              (log/error error "Kartan lataus epäonnistui: " status url)
              {:status 502
               :body "Taustakartan lataaminen epäonnistui"})))))))


(defrecord KarttaCache [asetukset]
  component/Lifecycle
  (start [{:keys [http-palvelin] :as this}]
    (let [hakemisto (cache-hakemisto asetukset)
          ttl (ttl-ms asetukset)]

      ;; Siivotaan vanhentuneet tiedostot kerran komponentin käynnistyessä
      (poista-vanhat! hakemisto ttl)

      ;; Käytetään Ring-käsittelijää, koska vastaus on binary png
      (julkaise-palvelu http-palvelin :kartta-cache
        (fn [request]
          (hae-karttakuva asetukset request))
        {:ring-kasittelija? true
         :ei-todennettava false
         :lokita-kysely? false})

      (log/info "Karttacache käynnistetty: " (.getAbsolutePath hakemisto))
      this))

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin :kartta-cache)
    this))
