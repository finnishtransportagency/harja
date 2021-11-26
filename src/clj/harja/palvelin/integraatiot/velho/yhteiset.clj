(ns harja.palvelin.integraatiot.velho.yhteiset
  (:import (javax.net.ssl X509TrustManager SNIHostName SNIServerName SSLContext SSLParameters TrustManager)
           (java.net URI)
           (java.security.cert X509Certificate))
  (:require [clojure.data.json :as json]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn hae-velho-token [token-url kayttajatunnus salasana konteksti virhe-fn]
  (try+
    (let [ssl-engine (try
                       (let [tm (reify javax.net.ssl.X509TrustManager
                                  (getAcceptedIssuers [this] (make-array X509Certificate 0))
                                  (checkClientTrusted [this chain auth-type])
                                  (checkServerTrusted [this chain auth-type]))
                             client-context (SSLContext/getInstance "TLSv1.2")
                             token-uri (URI. token-url)
                             _ (.init client-context nil
                                      (-> (make-array TrustManager 1)
                                          (doto (aset 0 tm)))
                                      nil)
                             ssl-engine (.createSSLEngine client-context)
                             ^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
                         (.setServerNames ssl-params [(SNIHostName. (.getHost token-uri))])
                         (.setSSLParameters ssl-engine ssl-params)
                         (.setUseClientMode ssl-engine true)
                         ssl-engine)
                       (catch Throwable e
                         (log/warn (str "Velho komponentti ssl-engine ei toiminnassa, exception " (.getMessage e)))
                         (.printStackTrace e)
                         nil))
          otsikot {"Content-Type" "application/x-www-form-urlencoded"}
          http-asetukset {:metodi :POST
                          :url token-url
                          :kayttajatunnus kayttajatunnus
                          :salasana salasana
                          :otsikot otsikot
                          :httpkit-asetukset {:sslengine ssl-engine}}
          kutsudata "grant_type=client_credentials"
          vastaus (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)
          vastaus-body (json/read-str (:body vastaus))
          token (get vastaus-body "access_token")
          error (get vastaus-body "error")]
      (if (and token
               (nil? error))
        token
        (do
          (virhe-fn (str "Token pyyntö virhe " error))
          nil)))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "Velho token pyyntö epäonnistui. Virheet: " virheet)
      (virhe-fn (str "Token epäonnistunut " virheet))
      nil)))
