(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma :as urakan-kohdehakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)
(def +virhe-urakan-kohdehaussa+ ::yha-virhe-urakan-kohdehaussa)
(def +virhe-kohteen-lahetyksessa+ ::yha-virhe-kohteen-lahetyksessa)

;; todo: poista kun saadaan oikea yhteys YHA:n
(def +testi-urakan-kohdehakuvastaus+
  "<urakan-kohdehakuvastaus xmlns=\"http://www.liikennevirasto.fi/xsd/yha\">\n    <kohteet>\n        <kohde>\n            <yha-id>251041528</yha-id>\n            <kohdetyyppi>paallystys</kohdetyyppi>\n            <nimi>Testikohde 2</nimi>\n            <yllapitoluokka>3</yllapitoluokka>\n            <keskimaarainen-vuorokausiliikenne>2509</keskimaarainen-vuorokausiliikenne>\n            <nykyinen-paallyste>1</nykyinen-paallyste>\n            <tierekisteriosoitevali>\n                <karttapaivamaara>2016-01-01</karttapaivamaara>\n                <tienumero>66</tienumero>\n                <ajorata>0</ajorata>\n                <kaista>1</kaista>\n                <aosa>36</aosa>\n                <aet>0</aet>\n                <losa>41</losa>\n                <let>2321</let>\n            </tierekisteriosoitevali>\n            <alikohteet>\n                <alikohde>\n                    <yha-id>254915666</yha-id>\n                    <tierekisteriosoitevali>\n                        <karttapaivamaara>2016-01-01</karttapaivamaara>\n                        <tienumero>66</tienumero>\n                        <ajorata>0</ajorata>\n                        <kaista>1</kaista>\n                        <aosa>36</aosa>\n                        <aet>0</aet>\n                        <losa>41</losa>\n                        <let>0</let>\n                    </tierekisteriosoitevali>\n                    <tunnus>A</tunnus>\n                    <paallystystoimenpide>\n                        <uusi-paallyste>21</uusi-paallyste>\n                        <raekoko>12</raekoko>\n                        <kokonaismassamaara>2</kokonaismassamaara>\n                        <rc-prosentti>80</rc-prosentti>\n                        <kuulamylly>3</kuulamylly>\n                        <paallystetyomenetelma>31</paallystetyomenetelma>\n                    </paallystystoimenpide>\n                </alikohde>\n                <alikohde>\n                    <yha-id>254915667</yha-id>\n                    <tierekisteriosoitevali>\n                        <karttapaivamaara>2016-01-01</karttapaivamaara>\n                        <tienumero>66</tienumero>\n                        <ajorata>0</ajorata>\n                        <kaista>1</kaista>\n                        <aosa>41</aosa>\n                        <aet>0</aet>\n                        <losa>41</losa>\n                        <let>2321</let>\n                    </tierekisteriosoitevali>\n                    <tunnus>B</tunnus>\n                    <paallystystoimenpide>\n                        <uusi-paallyste>21</uusi-paallyste>\n                        <raekoko>10</raekoko>\n                        <kokonaismassamaara>1</kokonaismassamaara>\n                        <rc-prosentti>1</rc-prosentti>\n                        <kuulamylly>1</kuulamylly>\n                        <paallystetyomenetelma>21</paallystetyomenetelma>\n                    </paallystystoimenpide>\n                </alikohde>\n            </alikohteet>\n        </kohde>\n        <kohde>\n            <yha-id>251603670</yha-id>\n            <kohdetyyppi>paallystys</kohdetyyppi>\n            <nimi>Testikohde 1</nimi>\n            <yllapitoluokka>1</yllapitoluokka>\n            <keskimaarainen-vuorokausiliikenne>3107</keskimaarainen-vuorokausiliikenne>\n            <nykyinen-paallyste>1</nykyinen-paallyste>\n            <tierekisteriosoitevali>\n                <karttapaivamaara>2016-01-01</karttapaivamaara>\n                <tienumero>3</tienumero>\n                <ajorata>0</ajorata>\n                <kaista>1</kaista>\n                <aosa>230</aosa>\n                <aet>450</aet>\n                <losa>230</losa>\n                <let>460</let>\n            </tierekisteriosoitevali>\n            <alikohteet>\n                <alikohde>\n                    <yha-id>254915669</yha-id>\n                    <tierekisteriosoitevali>\n                        <karttapaivamaara>2016-01-01</karttapaivamaara>\n                        <tienumero>3</tienumero>\n                        <ajorata>0</ajorata>\n                        <kaista>1</kaista>\n                        <aosa>230</aosa>\n                        <aet>450</aet>\n                        <losa>230</losa>\n                        <let>460</let>\n                    </tierekisteriosoitevali>\n                    <tunnus>C</tunnus>\n                    <paallystystoimenpide>\n                        <uusi-paallyste>21</uusi-paallyste>\n                        <raekoko>10</raekoko>\n                        <kokonaismassamaara>1</kokonaismassamaara>\n                        <rc-prosentti>1</rc-prosentti>\n                        <kuulamylly>1</kuulamylly>\n                        <paallystetyomenetelma>21</paallystetyomenetelma>\n                    </paallystystoimenpide>\n                </alikohde>\n            </alikohteet>\n        </kohde>\n    </kohteet>\n</urakan-kohdehakuvastaus>")

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this yhatunniste sampotunniste vuosi])
  (hae-kohteet [this urakka-id kayttajatunnus])
  (laheta-kohteet [this urakka-id kohde-idt]))

(defn kasittele-urakoiden-hakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakan kohdehaulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakoiden-hakuvastaus/lue-sanoma sisalto)
        urakat (:urakat vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (do
        (log/error (format "Urakoiden haussa YHA:sta tapahtui virhe: %s" virhe))
        (throw+
          {:type +virhe-urakoiden-haussa+
           :virheet {:virhe virhe}}))
      urakat)))

(defn kasittele-urakan-kohdehakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakoiden haulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakan-kohdehakuvastaus/lue-sanoma sisalto)
        kohteet (:kohteet vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (do
        (log/error (format "Urakan kohteiden haussa YHA:sta tapahtui virhe: %s" virhe))
        (throw+
          {:type +virhe-urakan-kohdehaussa+
           :virheet {:virhe virhe}}))
      kohteet)))

(defn kasittele-urakan-kohdelahetysvastaus [body headers]
  ;; todo: jos onnistunut, merkitse kohde lähetetyksi
  )

(defn lisaa-http-parametri [parametrit avain arvo]
  (if arvo
    (assoc parametrit avain arvo)
    parametrit))

(defn hae-urakat-yhasta [integraatioloki db url yhatunniste sampotunniste vuosi]
  (let [url (str url "urakkahaku")]
    (log/debug (format "Haetaan YHA:sta urakata (tunniste: %s, sampotunnus: %s & vuosi: %s). URL: "
                       yhatunniste sampotunniste vuosi url))
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "urakoiden-haku"
      (fn [konteksti]
        (let [parametrit {"tunniste" yhatunniste
                          "sampotunnus" sampotunniste
                          "vuosi" vuosi}
              http-asetukset {:metodi :GET
                              :url url
                              :parametrit parametrit}
              {body :body headers :headers}
              (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-urakoiden-hakuvastaus body headers))))))

(defn hae-urakan-kohteet-yhasta [integraatioloki db url urakka-id kayttajatunnus]
  (if-let [yha-id (q-yha-tiedot/hae-urakan-yha-id db {:urakkaid urakka-id})]
    (let [url (str url (format "haeUrakanKohteet" yha-id))
          vuosi (pvm/vuosi (pvm/nyt))
          ;; todo: poista pultattu urakan yha-id, kun CGI saa korjattua rajapinnan
          yha-id 251604402]
      (log/debug (format "Haetaan urakan (id: %s, YHA-id: %s) kohteet YHA:sta. URL: %s" urakka-id yha-id url))

      ;; todo: ota pois, kun saadaan yhteys toimimaan YHA:n
      (with-fake-http [url +testi-urakan-kohdehakuvastaus+]
        (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "yha" "kohteiden-haku"
          (fn [konteksti]
            (let [parametrit (-> {}
                                 (lisaa-http-parametri "yha-id" yha-id)
                                 (lisaa-http-parametri "vuosi" vuosi)
                                 (lisaa-http-parametri "kayttaja" kayttajatunnus))
                  http-asetukset {:metodi :GET :url url :parametrit parametrit}
                  {body :body headers :headers}
                  (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
              (kasittele-urakan-kohdehakuvastaus body headers))))))
    (do
      (let [virhe (format "Urakan (id: %s) YHA-id:tä ei löydy tietokannasta. Kohteita ei voida hakea." urakka-id)]
        (log/error virhe)
        (throw+
          {:type +virhe-urakan-kohdehaussa+
           :virheet {:virhe virhe}})))))

(defn hae-kohteen-tiedot [db kohde-id]
  (if-let [kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))]
    (let [alikohteet (q-yllapitokohteet/hae-yllapitokohteen-kohdeosat db {:yllapitokohde kohde-id})
          paallystys-ilmoitus (first (q-paallystys/hae-urakan-paallystysilmoitus-paallystyskohteella db kohde-id))]
      {:kohde kohde
       :alikohteet alikohteet
       :paallystys-ilmoitus paallystys-ilmoitus})
    (let [virhe (format "Tuntematon kohde (id: %s)." kohde-id)]
      (log/error virhe)
      (throw+
        {:type +virhe-kohteen-lahetyksessa+
         :virheet {:virhe virhe}}))))

(defn laheta-kohteet-yhan [integraatioloki db url urakka-id kohde-idt]
  (log/debug (format "Lähetetään urakan (id: %s) kohteet: %s YHA:n URL:lla: %s." urakka-id kohde-idt url))
  (if-let [urakka (first (q-yha-tiedot/hae-urakan-yhatiedot db urakka-id))]
    (let [urakka (assoc urakka :harjaid urakka-id :sampoid (q-urakat/hae-urakan-sampo-id db urakka-id))
          kohteet (mapv #(hae-kohteen-tiedot db %) kohde-idt)
          url (str url "toteumatiedot")
          kutsudata (kohteen-lahetyssanoma/muodosta urakka kohteet)]
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "yha" "kohteiden-lahetys"
        (fn [konteksti]
          (let [http-asetukset {:metodi :POST :url url}
                {body :body headers :headers}
                (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
            (kasittele-urakan-kohdelahetysvastaus body headers)))))
    (let [virhe (format "Urakan (id: %s) YHA-tietoja ei löydy." urakka-id)]
      (log/error virhe)
      (throw+
        {:type +virhe-kohteen-lahetyksessa+
         :virheet {:virhe virhe}}))))

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YllapidonUrakoidenHallinta

  (hae-urakat [this yhatunniste sampotunniste vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) (:url asetukset) yhatunniste sampotunniste vuosi))
  (hae-kohteet [this urakka-id kayttajatunnus]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) (:url asetukset) urakka-id kayttajatunnus))
  (laheta-kohteet [this urakka-id kohde-idt]
    (laheta-kohteet-yhan (:integraatioloki this) (:db this) (:url asetukset) urakka-id kohde-idt)))