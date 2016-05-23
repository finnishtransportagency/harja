(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma :as urakan-kohdehakuvastaus]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.kyselyt.yha :as yha-tiedot]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)
(def +virhe-urakan-kohdehaussa+ ::yha-virhe-urakan-kohdehaussa)

;; todo: poista kun saadaan oikea yhteys YHA:n
(def +testi-urakan-kohdehakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
<urakan-kohdehakuvastaus xmlns=\"http://www.liikennevirasto.fi/xsd/yha\">
<kohteet>
<kohde>
<yha-id>251041528</yha-id>
<kohdetyyppi>paallystys</kohdetyyppi>
<tunnus>kauhajoen suora</tunnus>
<yllapitoluokka>3</yllapitoluokka>
<keskimaarainen-vuorokausiliikenne>2509</keskimaarainen-vuorokausiliikenne>
<nykyinen-paallyste>1</nykyinen-paallyste>
<tierekisteriosoitevali>
<karttapaivamaara>2016-01-01</karttapaivamaara>
<tienumero>66</tienumero>
<ajorata>0</ajorata>
<kaista>1</kaista>
<aosa>36</aosa>
<aet>0</aet>
<losa>41</losa>
<let>2321</let>
</tierekisteriosoitevali>
<alikohteet>
<alikohde>
<yha-id>254915666</yha-id>
<tierekisteriosoitevali>
<karttapaivamaara>2016-01-01</karttapaivamaara>
<tienumero>66</tienumero>
<ajorata>0</ajorata>
<kaista>1</kaista>
<aosa>36</aosa>
<aet>0</aet>
<losa>41</losa>
<let>0</let>
</tierekisteriosoitevali>
<paallystystoimenpide>
<uusi-paallyste>21</uusi-paallyste>
<raekoko>12</raekoko>
<kokonaismassamaara>2</kokonaismassamaara>
<rc-prosentti>80</rc-prosentti>
<kuulamylly>3</kuulamylly>
<paallystetyomenetelma>31</paallystetyomenetelma>
</paallystystoimenpide>
</alikohde>
<alikohde>
<yha-id>254915667</yha-id>
<tierekisteriosoitevali>
<karttapaivamaara>2016-01-01</karttapaivamaara>
<tienumero>66</tienumero>
<ajorata>0</ajorata>
<kaista>1</kaista>
<aosa>41</aosa>
<aet>0</aet>
<losa>41</losa>
<let>2321</let>
</tierekisteriosoitevali>
<paallystystoimenpide>
<uusi-paallyste>21</uusi-paallyste>
<raekoko>10</raekoko>
<kokonaismassamaara>1</kokonaismassamaara>
<rc-prosentti>1</rc-prosentti>
<kuulamylly>1</kuulamylly>
<paallystetyomenetelma>21</paallystetyomenetelma>
</paallystystoimenpide>
</alikohde>
</alikohteet>
</kohde>
<kohde>
<yha-id>251603670</yha-id>
<kohdetyyppi>paallystys</kohdetyyppi>
<tunnus>asdf</tunnus>
<yllapitoluokka>1</yllapitoluokka>
<keskimaarainen-vuorokausiliikenne>3107</keskimaarainen-vuorokausiliikenne>
<nykyinen-paallyste>1</nykyinen-paallyste>
<tierekisteriosoitevali>
<karttapaivamaara>2016-01-01</karttapaivamaara>
<tienumero>3</tienumero>
<ajorata>0</ajorata>
<kaista>1</kaista>
<aosa>230</aosa>
<aet>450</aet>
<losa>230</losa>
<let>460</let>
</tierekisteriosoitevali>
<alikohteet>
<alikohde>
<yha-id>254915669</yha-id>
<tierekisteriosoitevali>
<karttapaivamaara>2016-01-01</karttapaivamaara>
<tienumero>3</tienumero>
<ajorata>0</ajorata>
<kaista>1</kaista>
<aosa>230</aosa>
<aet>450</aet>
<losa>230</losa>
<let>460</let>
</tierekisteriosoitevali>
<paallystystoimenpide>
<uusi-paallyste>21</uusi-paallyste>
<raekoko>10</raekoko>
<kokonaismassamaara>1</kokonaismassamaara>
<rc-prosentti>1</rc-prosentti>
<kuulamylly>1</kuulamylly>
<paallystetyomenetelma>21</paallystetyomenetelma>
</paallystystoimenpide>
</alikohde>
</alikohteet>
</kohde>
</kohteet>
</urakan-kohdehakuvastaus>
")

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this yhatunniste sampotunniste vuosi])
  (hae-kohteet [this urakka-id])
  (laheta-kohde! [this kohde-id]))

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

(defn hae-urakan-kohteet-yhasta [integraatioloki db url urakka-id]
  (if-let [yha-id (yha-tiedot/hae-urakan-yha-id db {:urakkaid urakka-id})]
    (let [url (str url (format "haeUrakanKohteet" yha-id))]
      (log/debug (format "Haetaan urakan (id: %s, YHA-id: %s) kohteet YHA:sta. URL: %s" urakka-id yha-id url))
      ;; todo: ota pois, kun saadaan yhteys toimimaan YHA:n
      (with-fake-http [url +testi-urakan-kohdehakuvastaus+]
        (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "yha" "kohteiden-haku"
          (fn [konteksti]
            (let [parametrit (-> {}
                                 (lisaa-http-parametri "yha-id" yha-id)
                                 (lisaa-http-parametri "vuosi" (pvm/vuosi (pvm/nyt)))
                                 ;; todo: hae käyttäjän livi-tunnus
                                 (lisaa-http-parametri "kayttaja" "kayttaja123"))
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

(defn laheta-kohde-yhan [integraatioloki db url kohde-id])
;; todo: toteuta

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YllapidonUrakoidenHallinta

  (hae-urakat [this yhatunniste sampotunniste vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) (:url asetukset) yhatunniste sampotunniste vuosi))
  (hae-kohteet [this urakka-id]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) (:url asetukset) urakka-id))
  (laheta-kohde! [this kohde-id]
    (laheta-kohde-yhan (:integraatioloki this) (:db this) (:url asetukset) kohde-id)))

