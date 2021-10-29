(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:import (javax.net.ssl X509TrustManager SNIHostName SNIServerName SSLContext SSLParameters TrustManager)
           (java.net URI)
           (java.security.cert X509Certificate))
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [harja.kyselyt.koodistot :as koodistot]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.paallystys-kyselyt :as q-paallystys]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys]
            [harja.pvm :as pvm]
            [clojure.core.memoize :as memoize]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def +virhe-kohteen-lahetyksessa+ ::velho-virhe-kohteen-lahetyksessa)

(defprotocol PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]))

(defn hae-kohteen-tiedot [db kohde-id]
  (let [paallystysilmoitus (first (q-paallystys/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                    db
                                    {:paallystyskohde kohde-id}))
        _ (assert (= 2 (:versio paallystysilmoitus)) "Päällystysilmoituksen versio täytyy olla 2")
        poista-onnistuneet (fn [rivit]
                             (remove #(= "onnistunut" (:velho-rivi-lahetyksen-tila %)) rivit))
        paallystekerrokset (q-paallystys/hae-pot2-paallystekerrokset db {:pot2_id (:id paallystysilmoitus)})
        paallystekerrokset (poista-onnistuneet paallystekerrokset)
        alustat  (let [keep-some (fn [map-jossa-on-nil]
                                   (into {} (filter
                                              (fn [[_ arvo]] (some? arvo))
                                              map-jossa-on-nil)))
                       alustatoimet (->> (q-paallystys/hae-pot2-alustarivit db {:pot2_id (:id paallystysilmoitus)})
                                         (map keep-some)
                                         poista-onnistuneet
                                         (into []))]
                   alustatoimet)]
    {:paallystekerrokset (filter #(= 1 (:jarjestysnro %)) paallystekerrokset)
     :alustat alustat
     :paallystysilmoitus paallystysilmoitus}))

(defn kasittele-velhon-vastaus [sisalto otsikot paivita-fn]
  (log/debug (format "Velho palautti kirjauksille vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot))
  (let [vastaus (try (json/read-str sisalto :key-fn keyword)
                     (catch Throwable e
                       {:virheet [{:selite (.getMessage e)}]
                        :sanoman-lukuvirhe? true}))
        velho-oid (:oid vastaus)
        virheet (:virheet vastaus)                          ; todo emme tiedä miten virheet ilmoitetaan velholta
        onnistunut? (and (some? velho-oid) (empty? virheet))
        virhe-viesti (str "Velho palautti seuraavat virheet: " (str/join ", " virheet))]
    (if onnistunut?
      (do
        (log/info (str "Rivin lähetys velhoon onnistui " velho-oid))
        (paivita-fn "onnistunut" velho-oid)
        true)
      (do
        (log/error (str "Virheitä rivin lähetyksessä velhoon: " virheet))
        (paivita-fn "epaonnistunut" virhe-viesti)
        false))))

(defn hae-velho-token-velholta [token-url kayttajatunnus salasana ssl-engine konteksti virhe-fn]
                  (try+
                    (let [otsikot {"Content-Type" "application/x-www-form-urlencoded"}
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

(def hae-velho-token (memoize/ttl hae-velho-token-velholta :ttl/threshold 3000000))

(defn laheta-kohde-velhoon [integraatioloki db ssl-engine
                            {:keys [paallystetoteuma-url token-url kayttajatunnus salasana]}
                            urakka-id kohde-id]
  (log/debug (format "Lähetetään urakan (id: %s) kohde: %s Velhoon URL:lla: %s." urakka-id kohde-id paallystetoteuma-url))
  (when (not (str/blank? paallystetoteuma-url))
    (try+
      (let [paivita-paallystekerros (fn [id tila vastaus]
                                      (q-paallystys/merkitse-paallystekerros-lahetystiedot-velhoon!
                                        db
                                        {:aikaleima (pvm/nyt)
                                         :tila tila
                                         :lahetysvastaus vastaus
                                         :id (Integer/parseInt id)}))
            paivita-alusta (fn [id tila vastaus]
                             (q-paallystys/merkitse-alusta-lahetystiedot-velhoon!
                               db
                               {:aikaleima (pvm/nyt)
                                :tila tila
                                :lahetysvastaus vastaus
                                :id (Integer/parseInt id)}))
            paivita-yllapitokohde! (fn [tila vastaus]
                                     (q-yllapitokohteet/merkitse-kohteen-lahetystiedot-velhoon!
                                       db
                                       {:aikaleima (pvm/nyt)
                                        :tila tila
                                        :lahetysvastaus vastaus
                                        :kohdeid kohde-id}))]
        (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "velho" "kohteiden-lahetys" nil
          (fn [konteksti]
            (if-let [urakka (first (q-yha-tiedot/hae-urakan-yhatiedot db {:urakka urakka-id}))]
              (let [token-virhe-fn (partial paivita-yllapitokohde! "tekninen-virhe")
                    token (hae-velho-token token-url kayttajatunnus salasana ssl-engine konteksti token-virhe-fn)]
                (when token
                  (let [urakka (assoc urakka :harjaid urakka-id
                                             :sampoid (yha/yhaan-lahetettava-sampoid urakka))
                        kohde (hae-kohteen-tiedot db kohde-id)
                        kutsudata (kohteen-lahetyssanoma/muodosta urakka kohde (partial koodistot/konversio db))
                        ainakin-yksi-rivi-onnistui? (atom false)
                        kohteen-lahetys-onnistunut? (atom true)
                        laheta-rivi-velhoon (fn [kuorma paivita-fn]
                                              (try+
                                                (let [otsikot {"Content-Type" "application/json; charset=utf-8"
                                                               "Authorization" (str "Bearer " token)}
                                                      http-asetukset {:metodi :POST
                                                                      :url paallystetoteuma-url
                                                                      :otsikot otsikot}
                                                      kuorma-json (cheshire/encode kuorma)
                                                      {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset kuorma-json)
                                                      onnistunut? (kasittele-velhon-vastaus body headers paivita-fn)]
                                                  (reset! kohteen-lahetys-onnistunut? (and @kohteen-lahetys-onnistunut? onnistunut?))
                                                  (reset! ainakin-yksi-rivi-onnistui? (or @ainakin-yksi-rivi-onnistui? onnistunut?)))
                                                (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
                                                  (log/error "Päällystysilmoituksen rivin lähetys Velhoon epäonnistui. Virheet: " virheet)
                                                  (reset! kohteen-lahetys-onnistunut? false)
                                                  (paivita-fn "epaonnistunut" (str virheet)))))]
                    (doseq [paallystekerros (:paallystekerros kutsudata)]
                      (laheta-rivi-velhoon paallystekerros
                                           (partial paivita-paallystekerros (get-in paallystekerros [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
                    (doseq [alusta (:alusta kutsudata)]
                      (laheta-rivi-velhoon alusta
                                           (partial paivita-alusta (get-in alusta [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
                    (if @kohteen-lahetys-onnistunut?
                      (do (q-paallystys/lukitse-paallystysilmoitus! db {:yllapitokohde_id kohde-id})
                          (paivita-yllapitokohde! "valmis" nil))
                      (let [virhe-teksti "katso päälystekerrokset ja alustat"
                            lahetyksen-tila (if @ainakin-yksi-rivi-onnistui? "osittain-onnistunut" "epaonnistunut")]
                        (log/error (format "Kohteen (id: %s) lähetys epäonnistui. Virhe: \"%s\"" kohde-id virhe-teksti))
                        (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})
                        (paivita-yllapitokohde! lahetyksen-tila virhe-teksti))))))
              {:virhekasittelija (fn [konteksti e]
                                   (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})
                                   (paivita-yllapitokohde! "epaonnistunut" (.getMessage e)))}))))
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
        false))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this]
    (let [token-url (:token-url asetukset)]
      (if token-url
        (assoc this :ssl-engine
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
                      ssl-engine))
        (log/warn "Velho komponentti ssl-engine ei toiminnassa"))))
  (stop [this] this)

  PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]
    (laheta-kohde-velhoon (:integraatioloki this) (:db this) (:ssl-engine this) asetukset urakka-id kohde-id)))
