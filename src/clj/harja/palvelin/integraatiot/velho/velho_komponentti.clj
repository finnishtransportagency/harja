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
            [harja.kyselyt.toteumat :as q-toteumat]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys]
            [harja.pvm :as pvm]
            [clojure.core.memoize :as memoize]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def +virhe-kohteen-lahetyksessa+ ::velho-virhe-kohteen-lahetyksessa)
(def +virhe-varustetoteuma-haussa+ ::velho-virhe-varustetoteuma-haussa)

(def +varuste-lahteet+ [{:tietolaji ["tl501"] :palvelu "varusterekisteri" :api-versio "v1" :kohdeluokka "varusteet/kaiteet"}
                        {:tietolaji ["tl503" "tl504" "tl505"] :palvelu "varusterekisteri" :api-versio "v1" :kohdeluokka "varusteet/tienvarsikalusteet"}
                        {:tietolaji ["tl506"] :palvelu "varusterekisteri" :api-versio "v1" :kohdeluokka "varusteet/liikennemerkit"}
                        ;...
                        {:tietolaji ["tl518"] :palvelu "sijaintipalvelu" :api-versio "v3" :kohdeluokka "tiealueen-poikkileikkaus/erotusalueet"}
                        {:tietolaji ["tl518"] :palvelu "sijaintipalvelu" :api-versio "v3" :kohdeluokka "tiealueen-poikkileikkaus/luiska"}
                        ;...
                        {:tietolaji ["tl524"] :palvelu "tiekohderekisteri" :api-versio "v1" :kohdeluokka "ymparisto/viherkuviot"}])

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

(defn urakka-id-kohteelle [db {:keys [sijainti alkusijainti muokattu] :as kohde}]
  (let [s (or sijainti alkusijainti)
        tr-osoite {:tie (:tie s)
                   :aosa (:osa s)
                   :aet (:etaisyys s)
                   :paivamaara muokattu}
        urakka-id (-> (q-urakat/hae-hoito-urakka-tr-pisteelle db tr-osoite)
                      first
                      :id)]
        (assert (some? s) "`sijainti` tai `alkusijainti` on pakollinen")
        (assert (some? muokattu) "`muokattu` on pakollinen")
        (when (nil? urakka-id)
          (log/error "Kohteelle ei löydy urakkaa: oid: " (:oid kohde) " sijainti: " sijainti " alkusijainti: " alkusijainti))
        urakka-id))

(defn alku-500 [s]
  (subs s 0 (min 499 (count s))))

(defn varuste-raportoi-oid-haku [oidit url]
  (log/info (str "Haku Velhosta onnistui. Saatiin " (count oidit) " oidia. Url: " url)))

(defn kasittele-varusteiden-oidit [url sisalto otsikot]
  (let [{oidit :oidit status :tila} (try (let [oidit (json/read-str sisalto :key-fn keyword)]
                                           {:oidit oidit
                                            :tila {:virheet []
                                                   :sanoman-lukuvirhe? false}})
                                         (catch Throwable e
                                           {:oidit nil
                                            :tila {:virheet [{:selite (.getMessage e) :url url
                                                              :otsikot otsikot :sisalto (alku-500 sisalto)}]
                                                   :sanoman-lukuvirhe? true}}))
        virheet (:virheet status)                           ; todo virhekäsittelyä, ainakin 404, 500, 405?
        onnistunut? (and (some? oidit) (empty? virheet))
        virhe-viesti (str "Jäsennettäessä Velhon vastausta tapahtui seuraavat virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (varuste-raportoi-oid-haku oidit url)
        {:tila true :oidit oidit})
      (do
        (log/error (str "Virheitä haettaessa Velhosta: " virhe-viesti " Url: " url))
        {:tila false :oidit nil}
        ))))

(defn json->kohde-array [json]
  (json/read-str json :key-fn keyword))

(defn varuste-ndjson->kohteet [ndjson]
  "Jäsentää `ndjson` listan kohteita flatten listaksi kohde objekteja
  Jos syötteenä on merkkojonossa kohteiden versioita JSON muodossa,
  palauttaa listan, jossa on kohteiden versioita.
  `ndjson`:
  [<JSON-kohde1-v1>, <JSON-kohde1-v2>, ... ]
  [<JSON-kohde2-v1>, ...]
  ...
  paluuarvo:
  `'(<kohde1-v1> <kohde1-v2> ... <kohde2-v1> <kohde2-v2> ...)`"
  (let [rivit (clojure.string/split-lines ndjson)]
    (flatten (map (fn [rivi] (json->kohde-array rivi)) rivit))))

(defn nayte10 [c]
  (str "(" (min (count c) 10) "/" (count c) "): " (vec (take 10 c))))

(defn varuste-tallenna-kohde [kohteiden-historiat-ndjson haetut-oidit url tallenna-fn]
  "1. Jäsentää kohteet `kohteiden-historiat-ndjson`sta.
   `kohteiden-historiat-ndjson`:
    [{<kohde1-v1>},{kohde2-v2}]
    [{<kohde2-v1>}]
    [{<kohde3-v1>},{kohde3-v2}]
    ...
   2. Vertailee saatujen kohteiden oideja `haetut-oidit` joukkoon ja log/warn eroista.
   3. Päättelee tietolajit.
   4. Etsii urakka-idt.
   5. Tallentaa tietokantaan varustetoteumat `tallenna-fn` funktion avulla."
  (let [haetut-oidit (set haetut-oidit)
        saadut-kohteet (varuste-ndjson->kohteet kohteiden-historiat-ndjson) ;(<kohde1-v1> <kohde1-v2> ... <kohde2-v1> <kohde2-v2> ...)
        saadut-oidit (as-> saadut-kohteet a
                           (set/project a [:oid])
                           (map :oid a)
                           (set a))
        puuttuvat-oidit (set/difference haetut-oidit saadut-oidit)
        ylimaaraiset-oidit (set/difference saadut-oidit haetut-oidit)]
    (when (not-empty puuttuvat-oidit)
      (log/warn "Varustekohdeiden historiahaku palautti vajaan joukon kohteita. Puuttuvat oidit " (nayte10 puuttuvat-oidit) " Url: " url))
    (when (not-empty ylimaaraiset-oidit)
      (log/warn "Varustekohteiden historiahaku palautti ylimääräisiä kohteita. Ylimääräiset oidit " (nayte10 ylimaaraiset-oidit) " Url: " url))
    (log/info "Varustehaku Velhosta palautti " (count saadut-kohteet) " kohdetta. Url: " url)
    (doseq [kohde saadut-kohteet]
      (tallenna-fn kohde))
    true))

(defn varuste-tee-oidit-sisalto [oidit]
  (json/write-str oidit))

(defn varuste-muodosta-kohde-url [varuste-api-juuri-url lahde]
  (format "%s/%s/api/%s/historia/kohteet" varuste-api-juuri-url (:palvelu lahde) (:api-versio lahde)))

(defn hae-kohdetiedot-ja-tallenna-kohde [lahde varuste-api-juuri-url konteksti token oidit tallenna-fn]
  (let [url (varuste-muodosta-kohde-url varuste-api-juuri-url lahde)]
    (try+
      (let [pyynto (varuste-tee-oidit-sisalto oidit)
            otsikot {"Content-Type" "text/json; charset=utf-8"
                     "Authorization" (str "Bearer " token)}
            http-asetukset {:metodi :POST
                            :url url
                            :otsikot otsikot}
            {sisalto :body otsikot :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset pyynto)
            onnistunut? (varuste-tallenna-kohde sisalto oidit url tallenna-fn)]
        onnistunut?)
      (catch Throwable t
        (log/error "Haku Velhosta epäonnistui. url: " url " Throwable: " t)
        false)
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        (log/error "Haku Velhosta epäonnistui. url: " url " virheet: " virheet)
        false))))

(defn varuste-oidit-url [lahde varuste-api-juuri viimeksi-haettu-velhosta]
  (format "%s/%s/api/%s/tunnisteet/%s?jalkeen=%s"           ;`kohdeluokka` sisältää /-merkin. esim. `varusteet/kaiteet`
          varuste-api-juuri (:palvelu lahde) (:api-versio lahde) (:kohdeluokka lahde) viimeksi-haettu-velhosta))

(defn varusteet-hae-ja-tallenna [lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri token tallenna-fn]
  (try+
    (let [url (varuste-oidit-url lahde varuste-api-juuri viimeksi-haettu-velhosta)
          otsikot {"Content-Type" "text/json; charset=utf-8"
                   "Authorization" (str "Bearer " token)}
          http-asetukset {:metodi :GET
                          :url url
                          :otsikot otsikot}
          {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
          {tila :tila oidit :oidit} (kasittele-varusteiden-oidit url body headers)]
      (when (and tila (not-empty oidit))
        (hae-kohdetiedot-ja-tallenna-kohde lahde varuste-api-juuri konteksti token oidit tallenna-fn)))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "Haku Velhosta epäonnistui. Virheet: " virheet))))

(defn sijainti-kohteelle [db {:keys [sijainti alkusijainti loppusijainti] :as kohde}]
  (let [a (or sijainti alkusijainti)
        b loppusijainti]
    (assert (some? a) "`sijainti` tai `alkusijainti` on pakollinen")
    (let [parametrit {:tie (:tie a)
                      :aosa (:osa a)
                      :aet (:etaisyys a)
                      :losa (or (:osa b) (:osa a))
                      :let (or (:etaisyys b) (:etaisyys a))}]
          (:sijainti (first (q-toteumat/varustetoteuman-toimenpiteelle-sijainti db parametrit))))))

(defn hae-varustetoteumat-velhosta
  [integraatioloki
   db
   ssl-engine
   {:keys [token-url
           varuste-api-juuri-url
           varuste-kayttajatunnus
           varuste-salasana]}]
  (log/debug (format "Haetaan uusia varustetoteumia Velhosta."))
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "velho" "varustetoteumien-haku" nil
      (fn [konteksti]
        (let [token-virhe-fn (fn [x] (log/error "Virhe Velho token haussa: " x))
              token (hae-velho-token token-url varuste-kayttajatunnus varuste-salasana ssl-engine konteksti token-virhe-fn)]
          (when token
            (let [viimeksi-haettu-velhosta "2021-09-01T00:00:00Z" ; aikaleima edelliselle hakukerralle
                  tallenna-varustetoteuma2-fn (fn [kohde]
                                                (log/debug "Tallennetaan oid: " (:oid kohde) " muokattu: " (:muokattu kohde))
                                                (let [urakka-id-kohteelle-fn (partial urakka-id-kohteelle db)
                                                      sijainti-kohteelle-fn (partial sijainti-kohteelle db)
                                                      varustetoteuma2 (varuste-vastaanottosanoma/velho->harja
                                                                        urakka-id-kohteelle-fn sijainti-kohteelle-fn kohde)]
                                                  (if varustetoteuma2
                                                    ; TODO Selvitä duplicate key exception (odotettu)
                                                    (q-toteumat/luo-varustetoteuma2<! db varustetoteuma2)
                                                    (log/warn "Varustetoteuma ei ole validi. Oid: "
                                                              (:oid kohde) " muokattu: " (:muokattu kohde)))))]
              (doseq [lahde +varuste-lahteet+]
                (varusteet-hae-ja-tallenna
                  lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri-url
                  token tallenna-varustetoteuma2-fn)))))))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this]
    (let [token-url (:token-url asetukset)
          ssl-engine (try
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
                          nil))]
      (if ssl-engine
        (assoc this :ssl-engine ssl-engine)
        this)))
  (stop [this] this)

  PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]
    (laheta-kohde-velhoon (:integraatioloki this) (:db this) (:ssl-engine this) asetukset urakka-id kohde-id))

  VarustetoteumaHaku
  (hae-varustetoteumat [this]
    (hae-varustetoteumat-velhosta (:integraatioloki this) (:db this) (:ssl-engine this) asetukset)))