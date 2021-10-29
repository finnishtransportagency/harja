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
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as kohteen-lahetyssanoma]
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

(def +varuste-lahteet+ [{:palvelu "varusterekisteri" :api-versio "v1" :kohdeluokka "varusteet/kaiteet"}
                        {:palvelu "varusterekisteri" :api-versio "v1" :kohdeluokka "varusteet/tienvarsikalusteet"}
                        ;...
                        {:palvelu "sijaintipalvelu" :api-versio "v3" :kohdeluokka "tiealueen-poikkileikkaus/erotusalueet"}
                        ;...
                        {:palvelu "tiekohderekisteri" :api-versio "v1" :kohdeluokka "ymparisto/viherkuviot"}])

; Varusteiden nimikkeistö
; TL 501 Kaiteet
; TODO Mikä erottaa melurakenteiden kaiteet tavallisista kaiteista.
; TL 503 Levähdysalueiden varusteet
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
; #ext-urpo
; Petri Sirkkala  7 days ago
;@Erkki Mattila En löydä Latauspalvelun tienvarsikalusteet.(nd)json tiedostosta yhtään tvkt27:aa.
; Sensijaan tvkt17 ilmenee 152 kertaa.
;
;Erkki Mattila  6 days ago
;[...] Tuossa tosiaan oli joku semmonen juttu muistaakseni, että tierekkarista ei pystynyt päättelemään,
; että onko kyseessä teline vai katos, niin ne kaikki on telineitä nyt
;
; Myöhemmin todettu, että kaikki (def +polkupyorateline+ "tienvarsikalustetyyppi/tvkt17") ovat TL 507 bussipysäkin varusteita

(def +tl503-ominaisuustyyppi-arvot+ #{+poyta-ja-penkki+
                                      +eko-kierratyspiste+
                                      +kemiallisen-wc_n-tyhjennyspiste+
                                      +leikkialue+
                                      +kuntoiluvaline+
                                      +katos+
                                      +laituri+
                                      +pukukoppi+
                                      +opastuskartta+
                                      +tulentekopaikka+
                                      +polkupyorakatos+})
; TL 504 WC
(def +wc+ "tienvarsikalustetyyppi/tvkt11")
; TL 505 Jätehuolto
(def +maanpaallinen-jateastia-alle-240-l+ "tienvarsikalustetyyppi/tvkt08")
(def +maanpaallinen-jatesailio-yli-240-l+ "tienvarsikalustetyyppi/tvkt09")
(def +upotettu-jatesailio+ "tienvarsikalustetyyppi/tvkt10")
(def +tl505-ominaisuustyyppi-arvot+ #{+maanpaallinen-jateastia-alle-240-l+
                                      +maanpaallinen-jatesailio-yli-240-l+
                                      +upotettu-jatesailio+})
; TL 507 Bussipysäkin varusteet
(def +roska-astia+ "tienvarsikalustetyyppi/tvkt05")
(def +polkupyorateline+ "tienvarsikalustetyyppi/tvkt17")
(def +aikataulukehikko+ "tienvarsikalustetyyppi/tvkt20")
(def +penkki+ "tienvarsikalustetyyppi/tvkt04")
(def +tl507-ominaisuustyyppi-arvot+ #{+roska-astia+
                                      +polkupyorateline+
                                      +aikataulukehikko+
                                      +penkki+})
; TL 508 Bussipysäkin katos
(def +bussipysakin-katos+ "tienvarsikalustetyyppi/tvkt01")
; TL 516 Hiekkalaatikot
(def +hiekkalaatikko+ "tienvarsikalustetyyppi/tvkt18")
; TL 518 Kivetyt alueet
(def +liikennesaareke+ "erotusalue-tyyppi/erty05")
(def +korotettu-erotusalue+ "erotusalue-tyyppi/erty02")
(def +bussipysakin-odotusalue+ "erotusalue-tyyppi/erty07")
(def +tl518_ominaisuustyyppi-arvot+ #{+liikennesaareke+
                                      +korotettu-erotusalue+
                                      +bussipysakin-odotusalue+})

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
        tunniste (:oid vastaus)
        virheet (:virheet vastaus)
        onnistunut? (and (some? tunniste) (empty? virheet))
        virhe-viesti (str "Velho palautti seuraavat virheet: " (str/join ", " virheet))]
    (if onnistunut?
      (do
        (log/info (str "Rivin lähetys velhoon onnistui " tunniste))
        (paivita-fn "onnistunut" tunniste)
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

(defn filter-by-vals [pred m] (into {} (filter (fn [[k v]] (pred v)) m)))

(defn paattele-varusteen-tietolaji [kohde]
  (let [kohdeluokka (:kohdeluokka kohde)
        rakenteelliset-ominaisuudet (get-in kohde [:ominaisuudet :rakenteelliset-ominaisuudet])
        rakenteelliset-jarjestelmakokonaisuudet (get-in kohde [:ominaisuudet :infranimikkeisto :rakenteellinen-jarjestelmakokonaisuus])
        melurakenne? (and false rakenteelliset-jarjestelmakokonaisuudet)
        tl-map {:tl501 (and (= kohdeluokka "varusteet/kaiteet")
                            (not melurakenne?))
                :tl503 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (contains? +tl503-ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl504 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (= +wc+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl505 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (contains? +tl505-ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl506 (= kohdeluokka "varusteet/liikennemerkit")
                :tl507 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (contains? +tl507-ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl508 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (= +bussipysakin-katos+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl509 (= kohdeluokka "varusteet/rumpuputket")
                :tl512 (= kohdeluokka "varusteet/kaivot")
                :tl513 (= kohdeluokka "varusteet/reunapaalut")
                :tl515 (= kohdeluokka "varusteet/aidat")
                :tl516 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (= +hiekkalaatikko+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl517 (= kohdeluokka "varusteet/portaat")
                :tl518 (or (and (= kohdeluokka "tiealueen-poikkileikkaus/erotusalueet")
                                (contains? +tl518_ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                           (and (= kohdeluokka "tiealueen-poikkileikkaus/luiskat")
                                (= "luiska-tyyppi/luity01" (:tyyppi rakenteelliset-ominaisuudet))))
                :tl520 (= kohdeluokka "varusteet/puomit-sulkulaitteet-pollarit")
                :tl522 (= kohdeluokka "varusteet/reunatuet")
                :tl524 (= kohdeluokka "ymparisto/viherkuviot")}
        tl-keys (keys (filter-by-vals identity tl-map))]
    (cond
      (> 1 (count tl-keys)) (do (log/error
                                  (format "Varustekohteen tietolaji ole yksikäsitteinen. tunniste: %s tietolajit: %s"
                                          (:oid kohde)
                                          tl-keys))
                                nil)
      (= 0 (count tl-keys)) nil
      :else (first tl-keys))))

(defn paattele-urakka-id-kohteelle [db {:keys [sijainti alkusijainti muokattu] :as kohde}]
  (let [s (or sijainti alkusijainti)]
    (-> (q-urakat/hae-hoito-urakka-tr-pisteelle
          db
          {:tie (:tie s)
           :aosa (:aosa s)
           :aet (:aet s)
           :paivamaara muokattu})
        first
        :id)))

(defn alku-500 [s]
  (subs s 0 (min 499 (count s))))

(defn kasittele-varusteiden-tunnisteet [url sisalto otsikot]
  (log/debug (format "Velho palautti: url: %s otsikot: %s sisalto: %s" url otsikot (alku-500 sisalto)))
  (let [{tunnisteet :tunnisteet status :tila} (try (let [tunnisteet (json/read-str sisalto :key-fn keyword)]
                                                     {:tunnisteet tunnisteet
                                                      :tila {:virheet []
                                                             :sanoman-lukuvirhe? false}})
                                                   (catch Throwable e
                                                     {:tunnisteet nil
                                                      :tila {:virheet [{:selite (.getMessage e) :url url
                                                                        :otsikot otsikot :sisalto (alku-500 sisalto)}]
                                                             :sanoman-lukuvirhe? true}}))
        virheet (:virheet status)                           ; todo virhekäsittelyä, ainakin 404, 500, 405?
        onnistunut? (and (some? tunnisteet) (empty? virheet))
        virhe-viesti (str "Jäsennettäessä Velhon vastausta tapahtui seuraavat virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (log/info (format "Haku Velhosta onnistui. Saatiin %s kohdetta." (count tunnisteet)))
        {:tila true :tunnisteet tunnisteet})
      (do
        (log/error (str "Virheitä haettaessa Velhosta: " virhe-viesti))
        {:tila false :tunnisteet nil}
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

(defn varuste-kasittele-kohde [kohteiden-historiat-ndjson haetut-tunnisteet paattele-urakka-id-kohteelle-fn paivita-fn]
  "1. Jäsentää kohteet `kohteiden-historiat-ndjson`sta.
   `kohteiden-historiat-ndjson`:
    [{<kohde1-v1>},{kohde2-v2}]
    [{<kohde2-v1>}]
    [{<kohde3-v1>},{kohde3-v2}]
    ...
   2. Vertailee saatujen kohteiden tunnisteita `haetut-tunnisteet` joukkoon ja log/warn eroista.
   3. Päättelee tietolajit.
   4. Etsii urakka-idt.
   5. Tallentaa tietokantaan varustetoteumat `paivita-fn` funktion avulla."
  (let [haetut-tunnisteet (set haetut-tunnisteet)
        saadut-kohteet (varuste-ndjson->kohteet kohteiden-historiat-ndjson) ;(<kohde1-v1> <kohde1-v2> ... <kohde2-v1> <kohde2-v2> ...)
        saadut-tunnisteet (as-> saadut-kohteet a
                                (set/project a [:oid])
                                (map :oid a)
                                (set a))
        puuttuvat-tunnisteet (set/difference haetut-tunnisteet saadut-tunnisteet)
        ylimaaraiset-tunnisteet (set/difference saadut-tunnisteet haetut-tunnisteet)]
    (when (not-empty puuttuvat-tunnisteet)
      (log/warn "Varustekohdeiden historiahaku palautti vajaan joukon kohteita. Puuttuvat tunnisteet: " puuttuvat-tunnisteet))
    (when (not-empty ylimaaraiset-tunnisteet)
      (log/warn "Varustekohteiden historiahaku palautti ylimääräisiä kohteita. Ylimääräiset tunnisteet: " ylimaaraiset-tunnisteet))
    (log/info "Varustehaku Velhosta palautti " (count saadut-kohteet) " kohdetta.")
    (doseq [kohde saadut-kohteet]
      (let [urakka-id (paattele-urakka-id-kohteelle-fn kohde)
            tietolaji (paattele-varusteen-tietolaji kohde)
            tallennettava-kohde (assoc kohde :urakka-id urakka-id :tietolaji tietolaji)]
        (paivita-fn "ööh" "onnistunut" tallennettava-kohde)))
    true))

(defn varuste-tee-tunnisteet-sisalto [tunnisteet]
  (json/write-str tunnisteet))

(defn varuste-muodosta-kohde-url [varuste-api-juuri-url lahde]
  (format "%s/%s/api/%s/historia/kohteet" varuste-api-juuri-url (:palvelu lahde) (:api-versio lahde)))

(defn hae-kohdetiedot-ja-tallenna-kohde [lahde varuste-api-juuri-url konteksti token tunnisteet paattele-urakka-id-kohteelle-fn paivita-fn]
  (let [url (varuste-muodosta-kohde-url varuste-api-juuri-url lahde)]
    (try+
      (let [pyynto (varuste-tee-tunnisteet-sisalto tunnisteet)
            otsikot {"Content-Type" "text/json; charset=utf-8"
                     "Authorization" (str "Bearer " token)}
            http-asetukset {:metodi :POST
                            :url url
                            :otsikot otsikot}
            {sisalto :body otsikot :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset pyynto)
            _ (log/debug (format "Velho palautti: sisalto: %s otsikot: %s" (alku-500 sisalto) otsikot))
            onnistunut? (varuste-kasittele-kohde sisalto tunnisteet paattele-urakka-id-kohteelle-fn paivita-fn)]
        onnistunut?)
      (catch Throwable t
        (log/error "Haku Velhosta epäonnistui. url: " url " Throwable: " t)
        false)
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        (log/error "Haku Velhosta epäonnistui. url: " url " virheet: " virheet)
        false))))

(defn varuste-tunnisteet-url [lahde varuste-api-juuri viimeksi-haettu-velhosta]
  (format "%s/%s/api/%s/tunnisteet/%s?jalkeen=%s"           ;`kohdeluokka` sisältää /-merkin. esim. `varusteet/kaiteet`
          varuste-api-juuri (:palvelu lahde) (:api-versio lahde) (:kohdeluokka lahde) viimeksi-haettu-velhosta))

(defn varusteet-hae-ja-tallenna [lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri token paattele-urakka-id-kohteelle-fn paivita-fn]
  (try+
    (let [url (varuste-tunnisteet-url lahde varuste-api-juuri viimeksi-haettu-velhosta)
          otsikot {"Content-Type" "text/json; charset=utf-8"
                   "Authorization" (str "Bearer " token)}
          http-asetukset {:metodi :GET
                          :url url
                          :otsikot otsikot}
          {body :body headers :headers} (do
                                          (println "petrisi1004 " url)
                                          (integraatiotapahtuma/laheta konteksti :http http-asetukset))
          {tila :tila tunnisteet :tunnisteet} (kasittele-varusteiden-tunnisteet url body headers)]
      (when (and tila (not-empty tunnisteet))
        (hae-kohdetiedot-ja-tallenna-kohde lahde varuste-api-juuri konteksti token tunnisteet paattele-urakka-id-kohteelle-fn paivita-fn)))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "Haku Velhosta epäonnistui. Virheet: " virheet))))

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
                  paattele-urakka-id-kohteelle-fn (partial paattele-urakka-id-kohteelle db)
                  paivita-fn (fn [id tila vastaus]
                               (println id tila vastaus))]
              (doseq [lahde +varuste-lahteet+]
                (varusteet-hae-ja-tallenna
                  lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri-url
                  token paattele-urakka-id-kohteelle-fn paivita-fn)))))))))

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