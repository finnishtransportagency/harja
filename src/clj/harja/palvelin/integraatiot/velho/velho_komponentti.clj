(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:import (javax.net.ssl X509TrustManager SNIHostName SNIServerName SSLContext SSLParameters TrustManager)
           (java.net URI)
           (java.security.cert X509Certificate))
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
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

; Varusteiden nimikkeisto
(def +poyta-ja-penkki+ "tienvarsikalustetyyppi/tvkt03")
(def +eko-kierratyspiste+ "tienvarsikalustetyyppi/tvkt06")
(def +kemiallisen-wc_n-tyhjennyspiste+ "tienvarsikalustetyyppi/tvkt07")
(def +leikkialue+ "tienvarsikalustetyyppi/tvkt12")
(def +kuntoiluvaline+ "tienvarsikalustetyyppi/tvkt13")
(def +katos+ "tienvarsikalustetyyppi/tvkt02")
(def +laituri+ "tienvarsikalustetyyppi/tvkt19")
(def +pukukoppi+ "tienvarsikalustetyyppi/tvkt14")
(def +opastuskartta+ "tienvarsikalustetyyppi/tvkt15")
(def +tulentekopaikka+ "tienvarsikalustetyyppi/tvkt16")
(def +polkupyorakatos+ "tienvarsikalustetyyppi/tvkt27")
(def tl503-ominaisuustyyppi-arvot #{
                                    +poyta-ja-penkki+
                                    +eko-kierratyspiste+
                                    +kemiallisen-wc_n-tyhjennyspiste+
                                    +leikkialue+
                                    +kuntoiluvaline+
                                    +katos+
                                    +laituri+
                                    +pukukoppi+
                                    +opastuskartta+
                                    +tulentekopaikka+
                                    +polkupyorakatos+
                                    })

(defprotocol PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]))

(defprotocol VarustetoteumaHaku
  (hae-varustetoteumat [this]))

(defn hae-kohteen-tiedot [db kohde-id]
  (let [paallystysilmoitus (first (q-paallystys/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                    db
                                    {:paallystyskohde kohde-id}))
        _ (assert (= 2 (:versio paallystysilmoitus)) "Päällystysilmoituksen versio täytyy olla 2")
        poista-onnistuneet (fn [rivit]
                             (remove #(= "onnistunut" (:velho-rivi-lahetyksen-tila %)) rivit))
        paallystekerrokset (q-paallystys/hae-pot2-paallystekerrokset db {:pot2_id (:id paallystysilmoitus)})
        paallystekerrokset (poista-onnistuneet paallystekerrokset)
        alustat (let [keep-some (fn [map-jossa-on-nil]
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
                                         :id id}))
            paivita-alusta (fn [id tila vastaus]
                             (q-paallystys/merkitse-alusta-lahetystiedot-velhoon!
                               db
                               {:aikaleima (pvm/nyt)
                                :tila tila
                                :lahetysvastaus vastaus
                                :id id}))
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
                  (println "petar token je nasao " (pr-str token))
                  (let [urakka (assoc urakka :harjaid urakka-id
                                             :sampoid (yha/yhaan-lahetettava-sampoid urakka))
                        kohde (hae-kohteen-tiedot db kohde-id)
                        kutsudata (kohteen-lahetyssanoma/muodosta urakka kohde (partial koodistot/konversio db))
                        ainakin-yksi-rivi-onnistui? (atom false)
                        kohteen-lahetys-onnistunut? (atom true)
                        laheta-rivi-velhoon (fn [kuorma paivita-fn]
                                              (try+
                                                (let [otsikot {"Content-Type" "text/json; charset=utf-8"
                                                               "Authorization" (str "Bearer " token)}
                                                      http-asetukset {:metodi :POST
                                                                      :url paallystetoteuma-url
                                                                      :otsikot otsikot}
                                                      kuorma-json (json/write-str kuorma :value-fn konversio/pvm->json)
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

(defn kasittele-oid-lista [db sisalto otsikot]
  (log/debug (format "Velho palautti: sisältö: %s, otsikot: %s" sisalto otsikot))
  (let [vastaus (try (json/read-str sisalto :key-fn keyword)
                     (catch Throwable e
                       {:virheet [{:selite (.getMessage e)}]
                        :sanoman-lukuvirhe? true}))
        velho-oid (:oid vastaus)
        virheet (:virheet vastaus)                          ; todo virhekäsittelyä, ainakin 404, 500, 405?
        onnistunut? (and (some? velho-oid) (empty? virheet))
        virhe-viesti (str "Velho palautti seuraavat virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (log/info (str "Haku Velhosta onnistui " velho-oid))
        true)
      (do
        (log/error (str "Virheitä haettaessa Velhosta: " virheet))
        ))))

(defn kasittele-varuste-vastaus [db sisalto otsikot paivita-fn]
  (log/debug (format "Velho palautti: sisältö: %s, otsikot: %s" sisalto otsikot))
  (let [vastaus (try (json/read-str sisalto :key-fn keyword)
                     (catch Throwable e
                       {:virheet [{:selite (.getMessage e)}]
                        :sanoman-lukuvirhe? true}))
        velho-oid (:oid vastaus)
        virheet (:virheet vastaus)                          ; todo virhekäsittelyä, ainakin 404, 500, 405?
        onnistunut? (and (some? velho-oid) (empty? virheet))
        virhe-viesti (str "Velho palautti seuraavat virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (log/info (str "Haku Velhosta onnistui " velho-oid))
        (paivita-fn "" "onnistunut" velho-oid)
        vastaus)
      (do
        (log/error (str "Virheitä haettaessa Velhosta: " virheet))
        (paivita-fn "" "epaonnistunut" virhe-viesti)
        false))))

(defn tee-varuste-oid-body [oid-lista]
  (json/write-str oid-lista))


(defn paattele-tietolaji [tietokokonaisuus kohdeluokka kohde]
  (let [rakenteelliset-ominaisuudet (get-in kohde [:ominaisuudet :rakenteelliset-ominaisuudet])
        tl501? (and (= tietokokonaisuus :varusteet)
                   (= kohdeluokka :kaiteet))
        tl503? (and (= tietokokonaisuus :varusteet)
                   (= kohdeluokka :tienvarsikalusteet)
                   (contains? tl503-ominaisuustyyppi-arvot (:tyyppi rakenteelliset-ominaisuudet)))
        tl504? (and (= kohdeluokka "varusteet")
                   (= kohdeluokka :tienvarsikalusteet)
                   (or (:inva-wc rakenteelliset-ominaisuudet)
                       (:wc-lammitys rakenteelliset-ominaisuudet)
                       (:valaistus rakenteelliset-ominaisuudet)
                       (:wc-talousvesi rakenteelliset-ominaisuudet)
                       (:pesutilat rakenteelliset-ominaisuudet)
                       (not-empty (:wc-viemarointi rakenteelliset-ominaisuudet))))
        ]
    (when-not (= 1 (count (filter true? [tl501? tl503? tl504?])))
      (#())) ;ERROR, monta TL:aa kohteella
    (cond tl501? :tl501
          tl503? :tl503
          tl504? :tl504)))

(defn hae-varustetoteumat-velhosta
  [integraatioloki
   db
   ssl-engine
   {:keys [token-url
           varuste-muuttuneet-url
           varuste-hae-kohde-lista-url
           varuste-kayttajatunnus
           varuste-salasana]}]
  (log/debug (format "Haetaan uusia varustetoteumia Velhosta."))
  (when (not (str/blank? "DUMMY"))
    (try+
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "velho" "varusteiden-haku" nil
        (fn [konteksti]
          (let [token (hae-velho-token token-url varuste-kayttajatunnus varuste-salasana ssl-engine konteksti #())
                ; Todo: Tulee hakea jokaisen Varustetyypin (VHAR-5109) muuttuneet kohteet (OID-list)
                hae-muuttuneet-kaiteet-oid (fn [url]
                                     (try+
                                       (let [otsikot {"Content-Type"  "text/json; charset=utf-8"
                                                      "Authorization" (str "Bearer " token)}
                                             http-asetukset {:metodi  :GET
                                                             :url     url
                                                             :otsikot otsikot}
                                             {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
                                             oid-lista (kasittele-oid-lista db body headers)]
                                         ;Todo: Jäsennä body ja palauta oid joukko
                                         oid-lista "kaiteet")
                                       (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
                                         (log/error "Haku Velhosta epäonnistui. Virheet: " virheet))))
                hae-varustetoteumat-kaiteet-fn (fn [oid-lista paivita-fn]
                                         (try+
                                           (let [req-body (tee-varuste-oid-body oid-lista)
                                                 otsikot {"Content-Type"  "text/json; charset=utf-8"
                                                          "Authorization" (str "Bearer " token)}
                                                 http-asetukset {:metodi  :POST
                                                                 :url     varuste-hae-kohde-lista-url
                                                                 :otsikot otsikot
                                                                 :body req-body}
                                                 {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
                                                 onnistunut? (kasittele-varuste-vastaus db body headers paivita-fn)]
                                             onnistunut?)
                                           (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
                                             (log/error "Haku Velhosta epäonnistui. Virheet: " virheet))))
                ;paivita-varustetoteuma (fn [id tila vastaus]
                ;                 (q-paallystys/tallenna-varustetoteuma2!
                ;                   db
                ;                   {:aikaleima (pvm/nyt)
                ;                    :tila tila
                ;                    :lahetysvastaus vastaus
                ;                    :id id}))
                debug-tuloste (fn [id tila vastaus]
                                (println id tila vastaus))
                ] (println "Koodia puuttuu vielä")
                  (let [kaiteet-oid-lista (hae-muuttuneet-kaiteet-oid varuste-muuttuneet-url)
                        onnistunut? (hae-varustetoteumat-kaiteet-fn kaiteet-oid-lista debug-tuloste)]
                    (when onnistunut?          #()             ;paivita-edellinen-varustehaku-aika
                      )
                        ;(->> (hae-muuttuneet-oid debug-tuloste) (hae-varustetoteumat-fn debug-tuloste))
                    )
                  ;(doseq [paallystekerros (:paallystekerros kutsudata)]
                  ;  (laheta-rivi-velhoon paallystekerros
                  ;                       (partial paivita-paallystekerros (get-in paallystekerros [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
                  ;(doseq [alusta (:alusta kutsudata)]
                  ;  (hae-velhosta alusta
                  ;                       (partial paivita-alusta (get-in alusta [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
                  ;(if @kohteen-lahetys-onnistunut?
                  ;  (do (q-paallystys/lukitse-paallystysilmoitus! db {:yllapitokohde_id kohde-id})
                  ;      (paivita-yllapitokohde "valmis" nil))
                  ;  (do (log/error (format "Kohteen (id: %s) lähetys epäonnistui. Virhe: \"%s\"" kohde-id "virhe viesti"))
                  ;      (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})
                  ;      (paivita-yllapitokohde (if @ainakin-yksi-rivi-onnistui? "osittain-onnistunut" "epaonnistunut") nil)))))))
                  ;(catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
                  ;  (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
                  ;  false)
                  ))))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this :ssl-engine
                (let [tm (reify javax.net.ssl.X509TrustManager
                           (getAcceptedIssuers [this] (make-array X509Certificate 0))
                           (checkClientTrusted [this chain auth-type])
                           (checkServerTrusted [this chain auth-type]))
                      client-context (SSLContext/getInstance "TLSv1.2")
                      token-uri (URI. (:token-url asetukset))
                      paallystetoteuma-uri (URI. (:paallystetoteuma-url asetukset))
                      _ (.init client-context nil
                               (-> (make-array TrustManager 1)
                                   (doto (aset 0 tm)))
                               nil)
                      ssl-engine (.createSSLEngine client-context)
                      ^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
                  (.setServerNames ssl-params [(SNIHostName. (.getHost token-uri))])
                  (.setSSLParameters ssl-engine ssl-params)
                  (.setUseClientMode ssl-engine true)
                  ssl-engine)))
  (stop [this] this)

  PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]
    (laheta-kohde-velhoon (:integraatioloki this) (:db this) (:ssl-engine this) asetukset urakka-id kohde-id))

  VarustetoteumaHaku
  (hae-varustetoteumat [this]
    (hae-varustetoteumat-velhosta (:integraatioloki this) (:db this) (:ssl-engine this) asetukset)))

