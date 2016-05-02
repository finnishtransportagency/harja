(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma :as urakan-kohdehakuvastaus]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.kyselyt.yha :as yha-tiedot])
  (:use [slingshot.slingshot :only [throw+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)
(def +virhe-urakan-kohdehaussa+ ::yha-virhe-urakan-kohdehaussa)

;; todo: poista kun saadaan oikea yhteys YHA:n
(def +testiurakka-haun-vastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
   <yha:urakat>
     <yha:urakka>
      <yha:yha-id>1</yha:yha-id>
      <yha:elyt>
        <yha:ely>Pohjois-Pohjanmaa</yha:ely>
      </yha:elyt>
      <yha:vuodet>
        <yha:vuosi>2016</yha:vuosi>
      </yha:vuodet>
      <yha:sampotunnus>SAMPOTUNNUS1</yha:sampotunnus>
      <yha:tunnus>YHATUNNUS1</yha:tunnus>
     </yha:urakka>
     <yha:urakka>
       <yha:yha-id>2</yha:yha-id>
       <yha:elyt>
         <yha:ely>Pohjois-Pohjanmaa</yha:ely>
         <yha:ely>Pohjois-Savo</yha:ely>
       </yha:elyt>
       <yha:vuodet>
         <yha:vuosi>2016</yha:vuosi>
         <yha:vuosi>2017</yha:vuosi>
       </yha:vuodet>
       <yha:sampotunnus>SAMPOTUNNUS2</yha:sampotunnus>
       <yha:tunnus>YHATUNNUS2</yha:tunnus>
     </yha:urakka>
     <yha:urakka>
      <yha:yha-id>3</yha:yha-id>
      <yha:elyt>
        <yha:ely>Pohjois-Pohjanmaa</yha:ely>
        <yha:ely>Pohjois-Savo</yha:ely>
      </yha:elyt>
      <yha:vuodet>
        <yha:vuosi>2016</yha:vuosi>
      </yha:vuodet>
      <yha:sampotunnus>SAMPOTUNNUS3</yha:sampotunnus>
      <yha:tunnus>YHATUNNUS3</yha:tunnus>
    </yha:urakka>
   </yha:urakat>
 </yha:urakoiden-hakuvastaus>")

(def +testi-urakan-kohdehakuvastaus+
  "<yha:urakan-kohdehakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:kohteet>
      <yha:kohde>
        <yha:yha-id>3</yha:yha-id>
        <yha:kohdetyyppi>paikkaus</yha:kohdetyyppi>
        <yha:tunnus>string</yha:tunnus>
        <yha:yllapitoluokka>1</yha:yllapitoluokka>
        <yha:keskimaarainen-vuorokausiilikenne>1000</yha:keskimaarainen-vuorokausiilikenne>
        <yha:nykyinen-paallyste>1</yha:nykyinen-paallyste>
        <yha:tierekisteriosoitevali>
          <yha:karttapaivamaara>2016-01-01</yha:karttapaivamaara>
          <yha:tienumero>3</yha:tienumero>
          <yha:ajorata>0</yha:ajorata>
          <yha:kaista>11</yha:kaista>
          <yha:aosa>3</yha:aosa>
          <yha:aet>3</yha:aet>
          <yha:losa>3</yha:losa>
          <yha:let>3</yha:let>
        </yha:tierekisteriosoitevali>
        <yha:alikohteet>
          <yha:alikohde>
            <yha:yha-id>3</yha:yha-id>
            <yha:tierekisteriosoitevali>
              <yha:karttapaivamaara>2016-01-01</yha:karttapaivamaara>
              <yha:tienumero>3</yha:tienumero>
              <yha:ajorata>0</yha:ajorata>
              <yha:kaista>11</yha:kaista>
              <yha:aosa>3</yha:aosa>
              <yha:aet>3</yha:aet>
              <yha:losa>3</yha:losa>
              <yha:let>3</yha:let>
            </yha:tierekisteriosoitevali>
            <yha:tunnus>A</yha:tunnus>
            <yha:paallystystoimenpide>
              <yha:uusi-paallyste>11</yha:uusi-paallyste>
              <yha:raekoko>12</yha:raekoko>
              <yha:kokonaismassamaara>124</yha:kokonaismassamaara>
              <yha:rc-prosentti>14</yha:rc-prosentti>
              <yha:kuulamylly>4</yha:kuulamylly>
             <yha:paallystetyomenetelma>22</yha:paallystetyomenetelma>
          </yha:paallystystoimenpide>
        </yha:alikohde>
        </yha:alikohteet>
      </yha:kohde>
    </yha:kohteet>
  </yha:urakan-kohdehakuvastaus>")

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this yhatunniste sampotunniste vuosi])
  (hae-kohteet [this urakka-id])
  (laheta-kohde! [this kohde-id]))

(defn kasittele-urakoiden-hakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakoiden haulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakan-kohdehakuvastaus/lue-sanoma sisalto)
        kohteet (:kohteet vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (do
        (log/error (format "Urakan kohteiden haussa YHA:sta tapahtui virhe: %s" virhe))
        (throw+
          {:type +virhe-urakoiden-haussa+
           :virheet {:virhe virhe}}))
      kohteet)))

(defn kasittele-urakan-kohdehakuvastaus [sisalto otsikot]
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

(defn hae-urakat-yhasta [integraatioloki db url yhatunniste sampotunniste vuosi]
  (let [url (str url "/urakkahaku")]
    (log/debug (format "Haetaan YHA:sta urakata (tunniste: %s, sampotunnus: %s & vuosi: %s). URL: "
                       yhatunniste sampotunniste vuosi url))
    ;; todo: poista kun saadaan oikea yhteys YHA:n
    (with-fake-http [url +testiurakka-haun-vastaus+]
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
            (kasittele-urakoiden-hakuvastaus body headers)))))))

(defn hae-urakan-kohteet-yhasta [integraatioloki db url urakka-id]
  (if-let [yha-id (yha-tiedot/hae-urakan-yha-id db {:urakkaid urakka-id})]
    (let [url (str url (format "/urakat/%s/kohteet" yha-id)) ]
      (log/debug (format "Haetaan urakan (id: %s, YHA-id: %s) kohteet YHA:sta. URL: %s" urakka-id yha-id url))
      (with-fake-http [url +testi-urakan-kohdehakuvastaus+]
        (integraatiotapahtuma/suorita-integraatio
         db integraatioloki "yha" "kohteiden-haku"
         (fn [konteksti]
           (let [http-asetukset {:metodi :GET :url url}
                 {body :body headers :headers}
                 (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
             (kasittele-urakan-kohdehakuvastaus body headers))))))
    (do
      (let [virhe (format "Urakan (id: %s) YHA-id:tä ei löydy tietokannasta. Kohteita ei voida hakea." urakka-id)]
        (log/error virhe
                   (throw+
                     {:type +virhe-urakan-kohdehaussa+
                      :virheet {:virhe virhe}}))))))

(defn laheta-kohde-yhan [integraatioloki db url kohde-id])
  ;; todo: toteuta

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YllapidonUrakoidenHallinta

  (hae-urakat [this yhatunniste sampotunniste vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) yhatunniste sampotunniste vuosi))
  (hae-kohteet [this urakka-id]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) urakka-id))
  (laheta-kohde! [this kohde-id]
    (laheta-kohde-yhan (:integraatioloki this) (:db this) (:url (:yha asetukset)) kohde-id)))

