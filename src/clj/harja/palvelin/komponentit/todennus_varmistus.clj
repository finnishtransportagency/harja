(ns harja.palvelin.komponentit.todennus-varmistus
  "Authentikoinnin varmistus (JWT Tokenit)"
  (:require [clojure.string :as str]
            [clojure.core.cache :as cache]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as http]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.binary Base64)
           [java.security KeyFactory]
           [java.security.spec X509EncodedKeySpec]
           [org.bouncycastle.util.io.pem PemReader]
           [java.io StringReader]))

;; Accesstokenin public key (jwk=json key set)
(defonce accesstoken-jwk-cache (atom nil))
;; Iam-data (käyttäjäroolit) public key (PEM)
(defonce iam-data-pk-cache (atom nil))
;; Public avainten päivitys intervalli minuuteissa
(def +public-key-cache-paivitys-min+ 120)
;; Pidetään käyttäjätietoja muistissa vartti
(def +kayttaja-varmistus-cache-min+ 15)
;; Annetaan Cognitolle GET kutsuun user-agent tietoja 
(def user-agent-headers "HARJA/0.0.1-SNAPSHOT (JWT signature/harja.palaute@solita.fi)")


(defn- tunnistetiedot
  "Palauttaa dekoodatut tunnistetiedot käyttäjästä, sisältää roolit yms (x-iam-data)
   Tämä palautetaan silloin kun käyttäjän todennus onnistui, ja jatketaan authentikointia"
  [iam-data]
  (some-> iam-data
    (str/split #"\.") ;; HEADER.PAYLOAD.SIGNATURE
    second ;; Meillä kiinnostaa tässä kohti vain payload
    Base64/decodeBase64
    String.
    cheshire/decode))


(defn parsi-PEM-public-key
  "Cognito antaa X-Iam-data public avaimen PEM muodossa
   Se täytyy muuntaa sellaiseen java muotoon joka kelpaa signaturen tarkastukseen"
  [pem-key]
  (let [reader (PemReader. (StringReader. pem-key))
        pem-object (.readPemObject reader)
        key-bytes (.getContent pem-object)
        key-spec (X509EncodedKeySpec. key-bytes)
        key-factory (KeyFactory/getInstance "EC")] ;; "RSA" RSA avaimille, "EC" ECDSA avaimille
    (.generatePublic key-factory key-spec)))


(defn- public-key-vanhentunut?
  "Hakee uuden public keyn, jos sitä ei ole haettu vähään aikaan
   Cognitolla on ominaisuus rotatoida public avaimia 
   Tämä tehdään myös sen yhteydessä, jos käyttäjän todennus epäonnistuu (eli avain mahdollisesti rotatoitunut)"
  [cache-atom paivitys-intervalli]
  (> (- (System/currentTimeMillis) (:fetched-at @cache-atom)) (* paivitys-intervalli 60 1000)))


(defn hae-public-key
  "Tekee GET kutsun joka hakee public avaimen Cognitolta (x-iam-accesstoken & x-iam-data)
   Jos avain on jo cachessa, palautetaan tallennettu arvo
   Jos avaimet rotatoituu cachen aikana, ne päivitetään automaattisesti"
  [paivita? lx-kayttaja api-url PEM? cache-atom paivitys-intervalli]
  (if
    ;; Jos public avainta ei ole päivitetty x minuuttiin, päivitetään se
    ;; Tällainen cachetus vielä, koska yksi backend, monta käyttäjää, Cognito ei rotatoi public avaimia kovin tiheään 
    (or
      paivita? ;; Tämä on true jos käyttäjä ei päässyt sisälle, yritetään yhden kerran uudelleen
      (nil? @cache-atom)
      (public-key-vanhentunut? cache-atom paivitys-intervalli))
    (try
      (let [response @(http/get api-url {:headers {;; Lisää Cognitolle tiedoksi mistä pyyntö tulee
                                                   "User-Agent" (if paivita?
                                                                    ;; Koska authentikointi epäonnistui, laita LX tunnus mukaan 
                                                                  (str user-agent-headers " (update public-key/" lx-kayttaja ")")
                                                                  user-agent-headers)
                                                   "Content-Type" "application/json"}})
            response (if PEM?
                       (-> response :body (slurp) (parsi-PEM-public-key))
                       (-> response :body (json/read-json) :keys))
            _ (reset! cache-atom {:key response :fetched-at (System/currentTimeMillis)})]
        response)
      (catch Exception e
        (log/error (str "Failed to refresh public key cache: " (.getMessage e) " PEM?: " PEM?))))
    ;; Public avaimet on ajan tasalla, palauta tallennettu arvo 
    (-> @cache-atom :key)))


(defn hae-ja-suodata-accesstoken-public-key
  "Kutsuttu rajapinta palauttaa Accesstokenin public avaimen
   Mukana on 2 avainta, joten suodatetaan vaan haluttu avain (key identifier)"
  [kid issuer paivita? lx-kayttaja]
  (some #(when (= kid (:kid %)) %)
    (hae-public-key 
      paivita? 
      lx-kayttaja 
      (str issuer "/.well-known/jwks.json") 
      false 
      accesstoken-jwk-cache 
      +public-key-cache-paivitys-min+)))


(defn- decode-base64-json [s]
  (-> s Base64/decodeBase64 String. (json/read-json)))


(defn dekoodaa-token
  "Dekoodaa enkoodatut headerit, ja palauttaa tiedot json stringinä"
  [token]
  (let [[header payload signature] (str/split token #"\.")]
    {:header  (decode-base64-json header)
     :payload (decode-base64-json payload)
     :signature signature}))


(defn varmista-jwt-tokenit
  "Varmistaa authentikoinnin oikellisuuden Cogniton antamista tokeneista
   Tokenit mitkä vahvistetaan: x-iam-accesstoken (tokenin metadata), x-iam-data (käyttäjän tiedot&roolit)
   https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html#amazon-cognito-user-pools-using-tokens-manually-inspect"
  [accesstoken iam-data iam-identity paivita? iam-data-public-url]
  (let [lx-kayttaja (-> iam-data dekoodaa-token :payload :custom:uid)
        accesstoken-header (-> accesstoken dekoodaa-token :header)
        accesstoken-kid (:kid accesstoken-header)
        accesstoken-issuer (-> accesstoken dekoodaa-token :payload :iss)
        iam-data-header (-> iam-data dekoodaa-token :header)
        iam-data-kid (:kid iam-data-header)
        ;; Hae accesstoken (tokenin metadata väitteet) public avain
        accesstoken-public-key (hae-ja-suodata-accesstoken-public-key accesstoken-kid accesstoken-issuer paivita? lx-kayttaja)
        ;; Muunna java muotoon, joka käy javan signature librarylle
        accesstoken-public-key (keys/jwk->public-key accesstoken-public-key)
        ;; Hae iam-data (jossa käyttäjärooli väitteet) public avain, tämä on PEM muodossa 
        iam-data-public-key (hae-public-key
                              paivita?
                              lx-kayttaja
                              (str iam-data-public-url iam-data-kid)
                              true
                              iam-data-pk-cache
                              +public-key-cache-paivitys-min+)
        ;; Avainten algoritmit, buddy kirjasto haluaa nämä lowercasena
        accesstoken-algoritmi (-> accesstoken-header :alg (str/lower-case) (keyword))
        iam-data-algoritmi (-> iam-data-header :alg (str/lower-case) (keyword))

        _ (println "\n accesstoken payload: " accesstoken-header (-> accesstoken dekoodaa-token :payload))
        _ (println "\n accesstoken header: " accesstoken-header (-> accesstoken dekoodaa-token :header))
        _ (println "\n accesstoken signature: " accesstoken-header (-> accesstoken dekoodaa-token :signature) " \n \n" " ---- \n")
        _ (println "\n iam-data payload: " (-> iam-data dekoodaa-token :payload))
        _ (println "\n iam-data header: " (-> iam-data dekoodaa-token :header))
        _ (println "\n iam-data signature: " (-> iam-data dekoodaa-token :signature) " \n \n" " ---- \n")
        _ (println "\n iam-identity payload: " iam-identity " \n ")
        _ (println "\n custom uid: " (-> iam-data dekoodaa-token :payload :custom:uid) " \n ")
        _ (println "\n iam-data-public-url: " iam-data-public-url "\n  algo: " accesstoken-algoritmi)

        ;; Verifioi tokenit kutsumalla unsign, joka tarkastaa saapuvien tietojen allekirjoituksen
        ;; Signaturen verifiointi tulee suoraan javalta (java.security.Signature), niitä ei clojurena ole suoraa näkyvillä
        _ (jwt/unsign iam-data iam-data-public-key {:alg iam-data-algoritmi}) ;; Sisältää käyttäjän tietoja & Roolit
        _ (jwt/unsign accesstoken accesstoken-public-key {:alg accesstoken-algoritmi})])) ;; Sisältää mm. user poolin url, client idt


(defn- authentikointi-epaonnistui
  "Käsitellään virhe, ohjaa käyttäjän 'Ei käyttöoikeutta Harjaan.'- näkymään
   Yleensä johtuu järjestelmävirheestä, joko Cognitossa, tai muualla
   
   'Message seems manipulated': 
     - Public avain voi olla väärin vaikka yritettiin hakea uusi (Ota Harjan kirjautumisen välittäjälle yhteyttä)
     - On mahdollista, että payloadia sorkittu (vaatii toimenpiteitä), tutki mitä logeissa lukee 
   
   'Token is expired'
     - Token mennyt vanhaksi, kestää yleensä noin 10 min, tätä ei pitäisi näkyä (välittäjälle yhteyttä)
     - Käyttäjän kirjautuminen on cachessa ainakin vartin, jonka jälkeen todennuksen pitäisi päivittää uusi token
   
   'Audience does not match'
     - Audience, eli järjestelmän tunnus mille token annettu, ei jostain syystä täsmää (välittäjälle yhteyttä)
   
   'The subject does not match'
     - Subjekti, kenelle token myönnetty, ei jostain syystä täsmää (välittäjälle yhteyttä)
   
   'Issuer does mot match' 
     - Tokenin välittäjä ei jostain syystä täsmää (taas, välittäjälle yhteyttä)"
  [e iam-data]
  ;; Laukaisee slack-hälytyksen, nämä halutaan aina tutkia TODO ... 
  (log/error (str
               "[JWT-ERROR] Authentikointi ei onnistunut (JWT signature): " (.getMessage e)))
  (log/error (str
               "Saatu JWT Header (iam-data): " (-> iam-data dekoodaa-token :header)
               "  -  "
               ;; Tässä on käyttäjän yritetyt roolit yms, jos logeilta löytyy roolissa jotain outoa, tee välittömästi toimenpiteitä 
               "Saatu payload (iam-data): " (-> iam-data dekoodaa-token :payload)))
  ;; Authentikointi ei mennyt läpi, poista kaikki oikeudet käyttäjältä
  ;; Tämä uudelleenohjaa "Ei käyttöoikeutta" näkymään 
  ;; TODO ... 
  (-> (tunnistetiedot iam-data)
    ;; Extranet_Kayttaja ei anna oikeutta Harjaan
    ;; Jokin rooli täytyy antaa, muuten tulee todennusvirhe 
    (assoc "custom:rooli" "Extranet_Kayttaja")))


;; Pidä käyttäjän tietoja tallessa x ajan, todennusta kutsutaan liian tiheästi muuten 
(def kayttaja-varmistettu-cache (atom (cache/ttl-cache-factory {} :ttl (* +kayttaja-varmistus-cache-min+ 60 1000))))

(defn vahvista-jwt-signaturet
  "Tekee JWT tokenien signaturen vahvistuksen
   Palauttaa dekoodatut tunnistetiedot, mikäli signaturet OK
   JWT (Json Web Token) sisältää pisteellä erotetut osiot (enkoodattuna): HEADER.PAYLOAD.SIGNATURE
   
   Tokenien osiot:
     Header: Metadata 
     Payload: Claims (väitteet, mitkä pitää todentaa oikeiksi)
     Signature: Kryptografinen allekirjoitus Cognitolta, perustuu alkuperäiseen header.payload hash-arvoon 

   1. Haetaan ja parsitaan public avaimet, joita käytetään signaturen vahvistukseen 
        X-Iam-accesstoken public avaimen linkki on headereissa sisällä :iss (issuer) 
        X-Iam-data public avain on erillinen staattinen linkki, joka on PEM muodossa (passataan asetukset.edn kautta) 
   
   2. Lasketaan odotettu hash, ja verrataan alkuperäiseen signaturen hashiin (jwt/unsign)
      Täällä myös katsotaan tokenin expiration yms, virhe heitetään jos mitään on väärin 
   3. Jos kaikki OK, palautetaan dekoodattu tunnusdata, ja jatketaan authentikointia 
   4. Tuloksena käyttäjän roolitiedot on vahvistettu oikeiksi, eikä mitään ole sorkittu matkalla"
  ([accesstoken iam-data iam-identity kehitysmoodi? public-key-url]
   ;; yrita-uudelleen? on defaulttina aina false
   ;; Jos authentikointi epäonnistuu, yritetään yhden kerran uudelleen päivittämällä public-avaimet
   ;; On mahdollista että public avaimet rotatoituu, jolloin signature ei enää täsmää
   (vahvista-jwt-signaturet accesstoken iam-data iam-identity kehitysmoodi? public-key-url false))
  ([accesstoken iam-data iam-identity kehitysmoodi? public-key-url yrita-uudelleen?]
   (let [cache-key accesstoken]
     ;; Todennusta kutsutaan ilman malttia, joten cachella kutsutaan vaan tarvittaessa per käyttäjä
     (get (swap! kayttaja-varmistettu-cache #(cache/through
                                               (fn [_]
                                                 (try
                                                   ;; Cachessa ei ole tietoja, varmistetaan signaturet 
                                                   (when-not kehitysmoodi?
                                                     (varmista-jwt-tokenit accesstoken iam-data iam-identity yrita-uudelleen? public-key-url))
                                                   ;; Jos todennus onnistui, palauta tiedot ja jatka authentikointia 
                                                   (tunnistetiedot iam-data)
                                                   ;; Todennus epäonnistui
                                                   (catch Exception e
                                                     (if
                                                       (and
                                                         (not yrita-uudelleen?)
                                                         (= (.getMessage e) "Message seems corrupt or manipulated"))
                                                       ;; Tarkista, onko public key rotatoitunut yrittämällä uudelleen
                                                       (vahvista-jwt-signaturet accesstoken iam-data iam-identity kehitysmoodi? public-key-url true)
                                                       ;; Public key on ajan tasalla, ja vieläkin tulee virhe, heitetään hälytys logiin 
                                                       (authentikointi-epaonnistui e iam-data)))))
                                               ;; Tiedot on jo cachessa, palauta ne 
                                               %
                                               cache-key))
       cache-key))))
