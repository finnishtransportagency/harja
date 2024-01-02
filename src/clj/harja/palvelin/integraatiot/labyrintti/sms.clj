(ns harja.palvelin.integraatiot.labyrintti.sms
  "Labyrintti SMS Gateway"
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST GET]]
            [cheshire.core :as cheshire]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol Sms
  (rekisteroi-kuuntelija! [this kasittely-fn])
  (laheta [this numero viesti otsikot]))

(defn kasittele-vastaus [body headers]
  (log/debug (format "LinkMobilityn LinkSMS-palvelu (entinen Labyrintti) vastasi: sisältö: %s, otsikot: %s" body headers))
  (when (and body (.contains (string/lower-case body) "error"))
    (throw+ {:type :sms-lahetys-epaonnistui
             :error body}))
  {:sisalto body :otsikot headers})

(defn laheta-sms [db integraatioloki kayttajatunnus salasana url sms-url apiavain numero viesti otsikot]
  (if (and (or (empty? kayttajatunnus) (empty? salasana) (empty? url))
        (or (empty? apiavain) (empty? sms-url)))
    (log/warn "Tunnistautumistietoja tai URLia LinkMobilityn LinkSMS-palveluun (entinen Labyrintti) ei ole annettu. Viestiä ei voida lähettää.
    Pilviympäristössä käytössä on api-avain ja sms-url. Vanhassa ympäristössä käyttäjätunnus, salasana ja url.")
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "labyrintti" "laheta"
      (fn [konteksti]
        ;; TODO: #yliheitto Poista yliheiton jälkeen vaihtoehto, jossa ei lähetetä apiavainta ja http-asetukset, joissa metodi on GET.
        (let [otsikot (merge
                        (if (empty? apiavain)
                          {"Content-Type" "application/x-www-form-urlencoded"}
                          {"Content-Type" "application/x-www-form-urlencoded"
                           "x-api-key" apiavain})
                        otsikot)
              parametrit {"dests" numero
                          "text" viesti}
              http-asetukset (if (empty? apiavain)
                               {:metodi :GET
                                :url url
                                :kayttajatunnus kayttajatunnus
                                :salasana salasana
                                :otsikot otsikot
                                :parametrit parametrit}
                               {:metodi :POST
                                :url sms-url
                                :otsikot otsikot
                                :lomakedatana? true})       ;; Pilviympäristössä parametrit lähetetään avain-arvo-pareina form-parametreissä
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset parametrit)]
          (kasittele-vastaus body headers))))))

(defn kasittele-epaonnistunut-viestin-kasittely [integraatioloki tapahtuma-id poikkeus]
  (log/error (format "Tekstiviestin vastaanotossa tapahtui poikkeus." poikkeus))
  (integraatioloki/kirjaa-epaonnistunut-integraatio
    integraatioloki
    "Tekstiviestin vastaanotossa tapahtui poikkeus"
    (.toString poikkeus)
    tapahtuma-id
    nil))

(defn vastaanota-tekstiviesti [integraatioloki kutsu kuuntelijat]
  (log/info (format "Vastaanotettiin tekstiviesti LinkMobilityn LinkSMS-palvelusta (entinen Labyrintti) : %s" (assoc-in kutsu [:headers "authorization"] "*****")))
  (let [url (:remote-addr kutsu)
        otsikot (:headers kutsu)
        ;; Jostain syystä parametrit katoavat, jos ne ovat form-enkoodattuna. Puretaan ne käsin bodystä.
        ;; Tämä on tarkoitettu alustavaksi testiksi teksiviesti-ongelmien korjaamiseen.
        ;; FIXME: Selvitä juurisyy form-enkoodattujen parametrien katoamiseen ja korjaa se.
        ;; Ongelman saa näkyviin, jos pistää debuggerin kiinni ring.middleware.params riville 44 ja ajetaan kutsu
        ;; paikalliseen ympäristöön esim. postmanilla. Nähdään, että form-parametrit ovat löydettävissä aluksi, mutta
        ;; kun debuggerin päästää eteenpäin, se pysähtyy samalle riville kolmesti ja viimeisellä kerralla parametrit
        ;; ovat kadonneet. Epäilys on, että jokin, mitä me tehdään reitityksessä tai middlewareissa, rikkoo
        ;; ringin wrap-params - middlewaren. / JLu, 27.2.2022
        parametrit (-> kutsu
                      :body
                      .bytes
                      (String.)
                      ring.util.codec/form-decode)
        viesti (integraatioloki/tee-rest-lokiviesti "sisään" url nil nil otsikot (str parametrit))
        tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "labyrintti" "vastaanota" nil viesti)
        numero (get parametrit "source")
        viesti (get parametrit "text")]
    (try
      ;; jos numero tai viesti on nil, tunnistetaan virhe ja heitetään poikkeus
      (when (or (nil? numero) (nil? viesti))
        (throw+ {:type :puhelinnumero-tai-viesti-puuttuu
                 :message (str "numero: " numero ", viesti: " viesti)}))
      (let [vastaukset (mapv #(% numero viesti) @kuuntelijat)
            vastausdata (if (empty? vastaukset) "" (str "text=" (string/join ", " vastaukset)))
            vastausviesti (integraatioloki/tee-rest-lokiviesti "ulos" url nil vastausdata nil nil)]
        (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki vastausviesti nil tapahtuma-id nil)
        {:status 200
         :body vastausdata
         :headers {"Content-Type" "application/x-www-form-urlencoded"
                   "Content-Length" (count vastausdata)}})
      (catch Exception e
        (kasittele-epaonnistunut-viestin-kasittely integraatioloki tapahtuma-id e)
        {:status 500}))))

(defrecord Labyrintti [url kayttajatunnus salasana sms-url apiavain kuuntelijat]
  component/Lifecycle
  (start [{http :http-palvelin integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :vastaanota-tekstiviesti
      (POST "/sms" request (vastaanota-tekstiviesti integraatioloki request kuuntelijat))
      true)
    (assoc this
      :url url
      :kayttajatunnus kayttajatunnus
      :salasana salasana
      :sms-url sms-url
      :apiavain apiavain))

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :vastaanota-tekstiviesti)
    (reset! kuuntelijat #{})
    this)

  Sms
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
    (swap! kuuntelijat conj kuuntelija-fn)
    #(swap! kuuntelijat disj kuuntelija-fn))

  (laheta [this numero viesti otsikot]
    (laheta-sms (:db this)
                (:integraatioloki this)
                (:kayttajatunnus this)
                (:salasana this)
                (:url this)
                (:sms-url this)
                (:apiavain this)
                numero
                viesti
                otsikot)))

(defn luo-labyrintti [asetukset]
  (->Labyrintti (:url asetukset) (:kayttajatunnus asetukset) (:salasana asetukset) (:sms-url asetukset) (:apiavain asetukset) (atom #{})))

(defrecord FeikkiLabyrintti []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  Sms
  (rekisteroi-kuuntelija! [this kasittelija]
    (log/info "Feikki Labyrintti EI tue kuuntelijan rekisteröintiä")
    #(log/info "Poistetaan Feikki Labyrintin kuuntelija"))
  (laheta [this numero viesti otsikot]
    (log/info "Feikki Labyrintti lähettää muka viestin numeroon " numero ": " viesti)))

(defn feikki-labyrintti []
  (->FeikkiLabyrintti))
