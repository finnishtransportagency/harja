(ns harja.palvelin.integraatiot.api.tyokalut.kutsukasittely
  "API:n kutsujen käsittely funktiot"
  (:require [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [clojure.walk :as walk]
            [clojure.core.async :refer [<! go thread]]
            [org.httpkit.server :refer [with-channel on-close send!]]
            [harja.tyokalut.json-validointi :as json]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sonja-sahkoposti-sanomat]
            [harja.tyokalut.avaimet :as avaimet]
            [harja.kyselyt.kayttajat :as kayttajat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import [java.sql SQLException]
           (java.io StringWriter PrintWriter)
           (java.util.zip GZIPInputStream)
           (org.httpkit BytesInputStream)))


(defn lisaa-request-headerit-cors
  "Palautetaan kutsujalle lisäksi pari Cross-Origin Resource Sharing headeria, jotta kutsuja voi hyödyntää
  Harjan palauttamia tietoja sisällön esittämiseksi omassa domainissaan sijaitsevalla sivustolla."
  [response-headerit request-origin]
      (conj response-headerit
            {"Access-Control-Allow-Origin" (if (empty? request-origin) "*" request-origin),
             "Vary"                        "Origin"}))

(defn lisaa-request-headerit
      "Palautetaan kutsujalle sanoman content-typen mukaiset headerit"
      [xml? request-origin]
      (lisaa-request-headerit-cors (if xml? {"Content-Type" "application/xml"}
                                            {"Content-Type" "application/json"})
                                   request-origin))

(defn kutsun-formaatti
  "Analysoidaan kutsusta, onko se JSON vai XML formaattia. Palautetaan nil, mikäli ei passaa kumpaankaan."
  [request]
  (let [content-type (-> request :headers (get "content-type"))]
    (-> request :headers (get "content-type") (= "application/x-www-form-urlencoded"))
    (cond
      (= content-type "application/x-www-form-urlencoded") "form"
      (= content-type "application/xml") "xml"
      (= content-type "text/xml") "xml"
      (= content-type "application/json") "json"
      (= content-type "text/json") "json"
      :default nil)))

(defn parsi-skeeman-polku
  "XML validaattori vaatii polun skeemaan. Joten ylläpidetään mäppiä skeeman"
  [polku]
  (let [polun-osat (str/split polku #"\/")
        alku-polku (str (str/join "/" (reverse (rest (reverse polun-osat)))) "/")
        skeeman-nimi (last polun-osat)]
    [alku-polku skeeman-nimi]))

(defn tee-kirjausvastauksen-body
  "Ottaa kirjausvastauksen tiedot (mappi, jossa id, ilmoitukset, varoitukset ja virheet) ja tekee vastauksen bodyn.
   Sisällyttää body-mappiin vain ne tiedot, jotka eivät ole nil"
  [{:keys [id ilmoitukset varoitukset virheet muut-tiedot] :as tiedot}]
  (merge
    {}
    (when id {:id id})
    (when ilmoitukset {:ilmoitukset ilmoitukset})
    (when varoitukset {:varoitukset varoitukset})
    (when virheet {:virheet virheet})
    muut-tiedot))

(defn tee-lokiviesti [suunta body viesti]
  {:suunta suunta
   :sisaltotyyppi "application/json"
   :siirtotyyppi "HTTP"
   :sisalto body
   :otsikko (str (walk/keywordize-keys (:headers viesti)))
   :parametrit (str (:params viesti))})

(defn poista-liitteet-logituksesta
  "Etsii avainpolun, joka päättyy avaimeen liitteet. Käsittelee sen alta löytyvät liitteet tyhjentäen niiden
   sisällön"
  [body xml?]
  (try+
    (let [body-clojure-mappina (if xml?
                                 (xml/lue body "UTF-8")
                                 (cheshire/decode body true))
          avainpolut (avaimet/keys-in body-clojure-mappina)
          avainpolku-liitteet (first (filter
                                       (fn [avainpolku]
                                         (= (last avainpolku) :liitteet))
                                       avainpolut))
          liitteet-ilman-sisaltoja (when avainpolku-liitteet
                                     (mapv (fn [liite]
                                             (if xml?
                                               (assoc-in liite [:liite :sisalto] " Liitettä ei logiteta ")
                                               (assoc-in liite [:liite :sisalto] "< Liitettä ei logiteta >")))
                                           (get-in body-clojure-mappina avainpolku-liitteet)))
          body-ilman-liittteiden-sisaltoa (when liitteet-ilman-sisaltoja
                                            (assoc-in body-clojure-mappina avainpolku-liitteet liitteet-ilman-sisaltoja))]
      (if avainpolku-liitteet
        (if xml?
          body-ilman-liittteiden-sisaltoa
          (cheshire/encode body-ilman-liittteiden-sisaltoa))
        (if (> (count body) 10000)
          (str/join (take 10000 body))
          body)))
    (catch Exception e
      (log/debug "Ei voida poistaa liitteitä bodystä: " (.getMessage e))
      body)))

(defn tee-xml-lokiviesti [suunta body viesti]
  {:suunta suunta
   :sisaltotyyppi "application/xml"
   :siirtotyyppi "HTTP"
   :sisalto (poista-liitteet-logituksesta body true)
   :otsikko (str (walk/keywordize-keys (:headers viesti)))
   :parametrit (str (:params viesti))})

(defn lokita-kutsu
  ([integraatioloki resurssi request body]
   (lokita-kutsu integraatioloki resurssi request body "api"))
  ([integraatioloki resurssi request body integraatio]
   (let [xml? (= (kutsun-formaatti request) "xml")
         loki-viesti (if xml?
                       (tee-xml-lokiviesti "sisään" body request)
                       (tee-lokiviesti "sisään" body request))]
     ;(log/debug "Vastaanotetiin kutsu resurssiin:" resurssi ".")
     ;(log/debug "Kutsu:" request)
     ;(log/debug "Parametrit: " (:params request))
     ;(log/debug "Headerit: " (:headers request))
     ;(log/debug "Otsikko: " (str (walk/keywordize-keys (:headers request))))
     ;(log/debug "Sisältö:" (poista-liitteet-logituksesta body xml?))
     ;(log/debug "Logitusviesti:" loki-viesti)

     (integraatioloki/kirjaa-alkanut-integraatio integraatioloki integraatio (name resurssi) nil loki-viesti))))

(defn lokita-vastaus [integraatioloki resurssi response tapahtuma-id]
  ;(log/debug "Lähetetään vastaus resurssiin:" resurssi "kutsuun.")
  ;(log/debug "Vastaus:" response)
  ;(log/debug "Headerit: " (:headers response))
  ;(log/debug "Sisältö:" (:body response))

  (if (= 200 (:status response))
    (do
      ;(log/debug "Kutsu resurssiin:" resurssi "onnistui. Palautetaan vastaus:" response)
      (integraatioloki/kirjaa-onnistunut-integraatio
        integraatioloki
        (tee-lokiviesti "ulos" (:body response) response) nil tapahtuma-id nil))
    (do
      (log/warn "Kutsu resurssiin:" resurssi "epäonnistui. Palautetaan vastaus:" response)
      (integraatioloki/kirjaa-epaonnistunut-integraatio
        integraatioloki
        (tee-lokiviesti "ulos" (:body response) response) nil tapahtuma-id nil))))

(defn tee-virhevastaus
  "Luo virhevastauksen annetulla statuksella ja asettaa vastauksen bodyksi JSON muodossa virheet."
  [status virheet request-origin]
  (let [body (cheshire/encode
               {:virheet
                (mapv (fn [virhe]
                        {:virhe
                         {:koodi (:koodi virhe)
                          :viesti (:viesti virhe)}})
                      virheet)})]
    {:status status
     :headers (lisaa-request-headerit false request-origin)
     :body body}))

(defn tee-sisainen-kasittelyvirhevastaus
  [virheet request-origin]
  (tee-virhevastaus 500 virheet request-origin))

(defn tee-viallinen-kutsu-virhevastaus
  [virheet request-origin]
  (tee-virhevastaus 400 virheet request-origin))

(defn tee-ei-hakutuloksia-virhevastaus
  [virheet request-origin]
  (tee-virhevastaus 404 virheet request-origin))

(defn tee-sisainen-autentikaatiovirhevastaus [virheet request-origin]
  (tee-virhevastaus 403 virheet request-origin))

(defn tee-vastaus
  "Luo JSON/XML-vastauksen joko annetulla statuksella tai oletuksena statuksella 200 (ok).
  Payload on Clojure dataa, joka muunnetaan JSON/XML-dataksi.
  Jokainen payload validoidaan annetulla skeemalla. Jos payload ei ole validi,
  palautetaan status 500 (sisäinen käsittelyvirhe)."
  ([skeema payload] (tee-vastaus 200 skeema payload nil false))
  ([status skeema payload request-origin xml?]
   (if payload
     (let [vastaus (if xml?
                     (xml/tee-xml-sanoma payload)
                     (cheshire/encode (spec-apurit/poista-nil-avaimet payload false)))]
       (if skeema
         (do
           (if (fn? skeema)
             (skeema vastaus)
             (if xml?
               (xml/validoi-xml (first (parsi-skeeman-polku skeema)) (second (parsi-skeeman-polku skeema)) vastaus)
               (json/validoi skeema vastaus)))
           {:status  status
            :headers (lisaa-request-headerit xml? request-origin)
            :body    vastaus})
         {:status  status
          :headers (lisaa-request-headerit xml? request-origin)}))
     (if skeema
       (throw+ {:type virheet/+sisainen-kasittelyvirhe+
                :virheet [{:koodi virheet/+tyhja-vastaus+
                           :viesti "Tyhjä vastaus vaikka skeema annettu"}]})
       {:status status
        :headers (lisaa-request-headerit xml? request-origin)}))))

(defn tee-optimoitu-json-vastaus
  "Luo JSON-vastauksen joko annetulla statuksella tai oletuksena statuksella 200 (ok).
  Payload on Clojure dataa, joka muunnetaan JSON-dataksi."
  [status payload request-origin]
  (if payload
    {:status status
     :headers (lisaa-request-headerit false request-origin)
     :body (cheshire/generate-string payload)}
    {:status status
     :headers (lisaa-request-headerit false request-origin)}))

(defn kasittele-invalidi-json [resurssi otsikot kutsu virheet]
  (if (> (count kutsu) 10000)
    (log/warn (format "Resurssin: %s kutsun JSON on invalidi: %s. JSON:n 10000 ensimmäistä merkkiä: %s. " resurssi virheet (pr-str (str/join (take 10000 kutsu)))))
    (log/warn (format "Resurssin: %s kutsun JSON on invalidi: %s. JSON: %s. " resurssi virheet (pr-str kutsu))))
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tee-viallinen-kutsu-virhevastaus virheet (get otsikot "origin")))

(defn kasittele-viallinen-kutsu [resurssi parametrit otsikot kutsu virheet]
      (log/warn (format "Resurssin: %s kutsu on viallinen: %s. Parametrit: %s. Kutsu: %s." resurssi virheet parametrit (pr-str (str/join (take 10000 kutsu)))))
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tee-viallinen-kutsu-virhevastaus virheet (get otsikot "origin")))

(defn kasittele-ei-hakutuloksia [resurssi otsikot virheet]
  (log/warn (format "Resurssin: %s kutsu ei palauttanut hakutuloksia: %s " resurssi virheet))
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tee-ei-hakutuloksia-virhevastaus virheet (get otsikot "origin")))

(defn kasittele-puutteelliset-parametrit [resurssi otsikot virheet]
  (log/warn (format "Resurssin: %s kutsussa puutteelliset parametrit: %s " resurssi virheet))
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tee-viallinen-kutsu-virhevastaus virheet (get otsikot "origin")))

(defn kasittele-sisainen-kasittelyvirhe [resurssi otsikot virheet]
  (log/error (format "Resurssin: %s kutsussa tapahtui sisäinen käsittelyvirhe: %s" resurssi virheet))
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tee-sisainen-kasittelyvirhevastaus virheet (get otsikot "origin")))

(defn kasittele-sisainen-autentikaatio-virhe [resurssi otsikot virheet]
  (log/error (format "Resurssin: %s kutsussa tapahtui autentikaatiovirhe: %s" resurssi virheet))
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tee-sisainen-autentikaatiovirhevastaus virheet (get otsikot "origin")))

(defn tarkista-tyhja-kutsu [skeema body]
  (when (and (fn? skeema) (nil? body))
    (throw+ {:type virheet/+invalidi-json+
             :virheet [{:koodi virheet/+invalidi-json-koodi+
                        :viesti "JSON on tyhjä"}]})))

(defn lue-kutsu
  "Lukee kutsun bodyssä tulevan datan, mikäli kyseessä on POST-, DELETE- tai PUT-kutsu.
  Muille kutsuille palauttaa arvon nil.
  Validoi annetun kutsun JSON/XML-datan ja mikäli data on validia, palauttaa datan Clojure dataksi muunnettuna.
  Jos annettu data ei ole validia, palautetaan nil."
  [xml? skeema request body]
  ;(log/debug "Luetaan kutsua")
  (when (or (= :post (:request-method request))
            (= :put (:request-method request))
            (= :delete (:request-method request)))
    (tarkista-tyhja-kutsu skeema body)
    (if (fn? skeema)
      (skeema body)
      (if xml?
        ;; XML datalle välitetään skeemana vain skeeman polku, josta luetaan itse xml skeema lennosta
        (xml/validoi-xml (first (parsi-skeeman-polku skeema)) (second (parsi-skeeman-polku skeema)) body)
        (json/validoi skeema body)))
    (if xml?
      ;; Sisääntuleva sähköposti käsitellään eri tavalla kuin muut xml viestits
      (if (str/includes? body ":sahkoposti")
        (sonja-sahkoposti-sanomat/lue-sahkoposti body)
        (xml/lue body "UTF-8"))
      (cheshire/decode body true))))

(defn hae-kayttaja [db kayttajanimi]
  (let [kayttaja (first (kayttajat/hae-kayttaja-kayttajanimella db kayttajanimi))]
    (if kayttaja
      (konv/array->set (konv/organisaatio kayttaja) :roolit)
      (do
        (log/error "Tuntematon käyttäjätunnus: " kayttajanimi)
        (throw+ {:type virheet/+tuntematon-kayttaja+
                 :virheet [{:koodi virheet/+tuntematon-kayttaja-koodi+
                            :viesti (str "Tuntematon käyttäjätunnus: " kayttajanimi)}]})))))


(defn vaadi-jarjestelmaoikeudet [db kayttaja vaadi-analytiikka-oikeus?]
  (let [on-oikeus (if vaadi-analytiikka-oikeus?
                    (kayttajat/onko-jarjestelma-ja-analytiikka? db {:kayttajanimi (:kayttajanimi kayttaja)})
                    (kayttajat/onko-jarjestelma? db {:kayttajanimi (:kayttajanimi kayttaja)}))]
    (if (nil? on-oikeus)
      (do
        (log/error "Käyttäjällä ei ole järjestelmäoikeuksia: " (:kayttajanimi kayttaja))
        (throw+ {:type virheet/+tuntematon-kayttaja+
                 :virheet [{:koodi virheet/+tuntematon-kayttaja-koodi+
                            :viesti (str "Tuntematon käyttäjätunnus: " (:kayttajanimi kayttaja))}]}))
      true)))


(defn aja-virhekasittelyn-kanssa [resurssi parametrit headerit body ajo]
  (try+
    (ajo)
    ;; Tunnetut poikkeustilanteet, virhetiedot voidaan julkaista
    (catch [:type virheet/+invalidi-json+] {:keys [virheet]}
      (kasittele-invalidi-json resurssi headerit body virheet))
    (catch [:type virheet/+viallinen-kutsu+] {:keys [virheet]}
      (kasittele-viallinen-kutsu parametrit resurssi headerit body virheet))
    (catch [:type virheet/+ei-hakutuloksia+] {:keys [virheet]}
      (kasittele-ei-hakutuloksia resurssi headerit virheet))
    (catch [:type virheet/+puutteelliset-parametrit+] {:keys [virheet]}
      (kasittele-puutteelliset-parametrit resurssi headerit virheet))
    (catch [:type virheet/+sisainen-kasittelyvirhe+] {:keys [virheet]}
      (kasittele-sisainen-kasittelyvirhe resurssi headerit virheet))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (kasittele-sisainen-kasittelyvirhe resurssi headerit virheet))
    (catch [:type virheet/+virheellinen-liite+] {:keys [virheet]}
      (kasittele-sisainen-kasittelyvirhe resurssi headerit virheet))
    (catch [:type virheet/+tuntematon-kayttaja+] {:keys [virheet]}
      (kasittele-sisainen-autentikaatio-virhe resurssi headerit virheet))
    (catch [:type virheet/+kayttajalla-puutteelliset-oikeudet+] {:keys [virheet]}
      (kasittele-sisainen-autentikaatio-virhe resurssi headerit virheet))
    (catch #(get % :virheet) poikkeus
      (kasittele-sisainen-kasittelyvirhe resurssi headerit (:virheet poikkeus) ))
    ;; Odottamattomat poikkeustilanteet (virhetietoja ei julkaista):
    (catch SQLException e
      (log/error e (format "Resurssin kutsun: %s yhteydessä tapahtui SQL-poikkeus: %s." resurssi e))
      (let [w (StringWriter.)]
        (loop [ex (.getNextException e)]
          (when (not (nil? ex))
            (.printStackTrace ex (PrintWriter. w))
            (recur (.getNextException ex))))
        (log/error "Sisemmät virheet: " (.toString w)))
      (kasittele-sisainen-kasittelyvirhe
        resurssi
        headerit
        [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
          :viesti "Sisäinen käsittelyvirhe"}]))
    (catch Exception e
      (log/error e (format "Resurssin kutsun: %s yhteydessä tapahtui poikkeus: %s." resurssi e))
      (kasittele-sisainen-kasittelyvirhe
        resurssi
        headerit
        [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
          :viesti "Sisäinen käsittelyvirhe"}]))
    (catch Object e
      (log/error (:throwable &throw-context) (format "Resurssin kutsun: %s yhteydessä tapahtui poikkeus: %s." resurssi e))
      (kasittele-sisainen-kasittelyvirhe
        resurssi
        headerit
        [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
          :viesti "Sisäinen käsittelyvirhe"}]))))

(defn- lue-body [request]
  (let [body (:body request)]
    (when body
      (if (= (get-in request [:headers "content-encoding"]) "gzip")
        (with-open [gzip (GZIPInputStream. body)]
          (slurp gzip))
        (slurp body)))))

(defn kasittele-kutsu
  "Käsittelee synkronisesti annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu ja
  lähetetty data on JSON/ tai XML -formaatissa, joka muunnetaan Clojure dataksi ja toisin päin. Sekä sisääntuleva, että ulos
  tuleva data validoidaan käyttäen annettuja JSON/XML -skeemoja.

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 = kutsun data on viallista & 500 = sisäinen
  käsittelyvirhe."

  [db integraatioloki resurssi request kutsun-skeema vastauksen-skeema kasittele-kutsu-fn]
  (if (-> request :headers (get "content-type") (= "application/x-www-form-urlencoded"))
    {:status 415
     :headers (lisaa-request-headerit-cors {"Content-Type" "text/plain"} (get (:headers request) "origin"))
     :body "Virhe: Saatiin kutsu lomakedatan content-typellä\n"}
    (let [xml? (= (kutsun-formaatti request) "xml")
          body (lue-body request)
          tapahtuma-id (when integraatioloki
                         (lokita-kutsu integraatioloki resurssi request body))
          parametrit (:params request)
          headerit (:headers request)
          vastaus (aja-virhekasittelyn-kanssa
                   resurssi
                   parametrit
                   headerit
                   body
                   #(let
                        [kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))
                         origin-header (get (:headers request) "origin")
                         kutsun-data (lue-kutsu xml? kutsun-skeema request body)
                         vastauksen-data (kasittele-kutsu-fn parametrit kutsun-data kayttaja db)]
                      (tee-vastaus 200
                                   vastauksen-skeema
                                   vastauksen-data
                                   origin-header
                                   xml?)))]
      (when integraatioloki
        (lokita-vastaus integraatioloki resurssi vastaus tapahtuma-id))
      vastaus)))

(defn kasittele-sahkoposti-kutsu
  "Käsittelee synkronisesti annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu ja
  lähetetty data on XML -formaatissa, joka muunnetaan Clojure dataksi ja toisin päin. Sisään tuleva data validoidaan
  käyttäen annettuja XML -skeemoja.

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 = kutsun data on viallista & 500 = sisäinen
  käsittelyvirhe.
  Hox. Tämä lähes identtinen muiden kasittele-kutsu funktioiden kanssa, mutta on eriytetty, jotta sähköpostiapin hallinta
  yksinkertaistuu"

  [db integraatioloki resurssi request kutsun-skeema vastauksen-skeema kasittele-kutsu-fn]
  (if-not (= (kutsun-formaatti request) "xml")
    {:status 415
     :headers (lisaa-request-headerit-cors {"Content-Type" "text/plain"} (get (:headers request) "origin"))
     :body "Virhe: Väärä content-type. Käytä application/xml\n"}
    (let [xml? (= (kutsun-formaatti request) "xml")
          body (lue-body request)
          tapahtuma-id (when integraatioloki
                         (lokita-kutsu integraatioloki resurssi request body))
          parametrit (:params request)
          headerit (:headers request)
          vastaus (aja-virhekasittelyn-kanssa
                    resurssi
                    parametrit
                    headerit
                    body
                    #(let
                       [kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))
                        origin-header (get (:headers request) "origin")
                        kutsun-data (lue-kutsu xml? kutsun-skeema request body)
                        vastauksen-data (kasittele-kutsu-fn parametrit kutsun-data kayttaja db)]

                       ;; Jos vain mahdollista, eikä todella vakavia virheitä satu, raportoidaan 200 ok vastaukseksi, vaikka
                       ;; itse käsittely ei ole välttämättä onnistunut
                       {:status 200
                        :headers (lisaa-request-headerit false origin-header)
                        :body vastauksen-data}))]
      (when integraatioloki
        (lokita-vastaus integraatioloki resurssi vastaus tapahtuma-id))
      vastaus)))

(defn kasittele-get-kutsu
  "Käsittelee synkronisesti annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu data
   tulee GET pyyntönä parametrien kanssa. Lähetetty data on JSON/ tai XML -formaatissa, joka muunnetaan Clojure dataksi ja toisin päin.
   Vain ulospäin lähtevä data validoidaan annetun scheman mukaisesti. Tämä siis poikkeaa hieman toisest kasittele-kutsu
   funktiosta.

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 = kutsun data on viallista & 500 = sisäinen
  käsittelyvirhe."

  [db integraatioloki resurssi request vastauksen-skeema kasittele-kutsu-fn vaadi-analytiikka-oikeus?]
  (if (-> request :headers (get "content-type") (= "application/x-www-form-urlencoded"))
    {:status 415
     :headers {"Content-Type" "text/plain"}
     :body "Virhe: Saatiin kutsu lomakedatan content-typellä\n"}
    (let [kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))
          xml? (= (kutsun-formaatti request) "xml")
          tapahtuma-id (when integraatioloki
                         (lokita-kutsu integraatioloki resurssi request nil))
          otsikot (:headers request)
          parametrit (:params request)
          vastaus (aja-virhekasittelyn-kanssa
                    resurssi
                    parametrit
                    otsikot
                    nil
                    #(let
                       [_ (vaadi-jarjestelmaoikeudet db
                            (hae-kayttaja db (get (:headers request) "oam_remote_user")) vaadi-analytiikka-oikeus?)
                        vastauksen-data (kasittele-kutsu-fn parametrit kayttaja db)]
                       (tee-vastaus 200 vastauksen-skeema vastauksen-data (get (:headers request) "origin") xml?)))]
      (when integraatioloki
        (lokita-vastaus integraatioloki resurssi vastaus tapahtuma-id))
      vastaus)))

(defn kasittele-kevyesti-get-kutsu
  "Käsittelee synkronisesti annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu data
   tulee GET pyyntönä parametrien kanssa. Lähetetty data on JSON/ tai XML -formaatissa, joka muunnetaan Clojure dataksi ja toisin päin.
   Vain ulospäin lähtevä data validoidaan annetun scheman mukaisesti. Tämä siis poikkeaa hieman toisest kasittele-kutsu
   funktiosta.

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 = kutsun data on viallista & 500 = sisäinen
  käsittelyvirhe."

  [db integraatioloki resurssi request kasittele-kutsu-fn vaadi-analytiikka-oikeus?]
  (if (-> request :headers (get "content-type") (= "application/x-www-form-urlencoded"))
    {:status 415
     :headers {"Content-Type" "text/plain"}
     :body "Virhe: Saatiin kutsu lomakedatan content-typellä\n"}
    (let [kutsun-kesto-alkaa (System/currentTimeMillis)
          request-origin (get (:headers request) "origin")
          kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))
          tapahtuma-id (when integraatioloki
                         (lokita-kutsu integraatioloki resurssi request nil))
          parametrit (:params request)
          vastaus (aja-virhekasittelyn-kanssa
                    resurssi
                    parametrit
                    (:headers request)
                    nil
                    #(let
                       [_ (vaadi-jarjestelmaoikeudet db kayttaja vaadi-analytiikka-oikeus?)]
                       (tee-optimoitu-json-vastaus 200 (kasittele-kutsu-fn parametrit kayttaja db) request-origin)))]
      (when integraatioloki
        (lokita-vastaus integraatioloki resurssi {:status (:status vastaus)
                                                  :body "Liian iso logitettavaksi"} tapahtuma-id))
      (do
        (log/info (str "Optimoitu kutsu: " resurssi " ajoaika: " (- (System/currentTimeMillis) kutsun-kesto-alkaa) " ms"))
        vastaus))))

(defn kasittele-kutsu-async
  "Käsittelee asynkronisesti annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu ja
  lähetetty data on JSON-formaatissa, joka muunnetaan Clojure dataksi ja toisin päin. Sekä sisääntuleva, että ulos
  tuleva data validoidaan käyttäen annettuja JSON-skeemoja.

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 = kutsun data on viallista & 500 = sisäinen
  käsittelyvirhe."
  [db integraatioloki resurssi request kutsun-skeema vastauksen-skeema kasittele-kutsu-fn]

  ;; mekanismi ei toimi asyncin kanssa, joten tämän alla oikeustarkistukset jäävät tarkistamatta
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)

  (with-channel request channel
    (go
      (let [vastaus (<! (thread (kasittele-kutsu db
                                                 integraatioloki
                                                 resurssi
                                                 request
                                                 kutsun-skeema
                                                 vastauksen-skeema
                                                 kasittele-kutsu-fn)))]
        (send! channel vastaus)))))


(defn kasittele-sampo-kutsu
  "Samanlainen käsittelijä, kuin ylläolevat. Räätälöity Sampo viestiin, koska se on logiikaltaan niin erilainen."
  [db integraatioloki resurssi request kutsun-skeema kasittele-kutsu-fn integraatio]
  (if (not= (kutsun-formaatti request) "xml")
    {:status 415
     :headers (lisaa-request-headerit-cors {"Content-Type" "text/plain"} (get (:headers request) "origin"))
     :body "Error: Wrong content type. Please use: application/xml\n"}
    (let [xml? (= (kutsun-formaatti request) "xml")
          body (lue-body request)
          tapahtuma-id (when integraatioloki
                         (lokita-kutsu integraatioloki resurssi request body integraatio))
          parametrit (:params request)
          vastaus (aja-virhekasittelyn-kanssa
                    resurssi
                    parametrit
                    (:headers request)
                    body
                    #(let
                       [kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))
                        origin-header (get (:headers request) "origin")
                        kutsun-data (lue-kutsu xml? kutsun-skeema request body)
                        vastauksen-data (kasittele-kutsu-fn db kutsun-data tapahtuma-id)
                        purettu-vastauksen-data (xml/lue vastauksen-data "UTF-8")
                        ;; Kutsujalle pyritään vastaamaan aina, joten päätellään itse viestistä, että onko
                        ;; käsittely onnistunut
                        mahdollinen-virhe (get-in (first (:content (first purettu-vastauksen-data))) [:attrs :ErrorMessage])
                        status (if-not (str/blank? mahdollinen-virhe) 400 200)]

                       {:status status
                        :headers (lisaa-request-headerit false origin-header)
                        :body vastauksen-data}))]
      (when integraatioloki
        (lokita-vastaus integraatioloki resurssi vastaus tapahtuma-id))
      vastaus)))
