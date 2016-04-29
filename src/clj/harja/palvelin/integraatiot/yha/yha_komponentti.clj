(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus]
            [org.httpkit.fake :refer [with-fake-http]])
  (:use [slingshot.slingshot :only [throw+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)


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

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this yhatunniste sampotunniste vuosi])
  (hae-kohteet [this urakka-id])
  (laheta-kohde [this kohde-id]))

(defn kasittele-urakoiden-hakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakoiden haulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakoiden-hakuvastaus/lue-sanoma sisalto)
        urakat (:urakat vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (throw+
        {:type +virhe-urakoiden-haussa+
         :virheet {:virhe virhe}})
      urakat)))

(defn hae-urakat-yhasta [integraatioloki db url yhatunniste sampotunniste vuosi]
  ;; todo: hae vain, jos URL on annettu
  (let [url (str url "/urakkahaku")]
    (log/debug (format "Haetaan YHA:sta urakata (tunniste: %s, sampotunnus: %s & vuosi: %s)" yhatunniste sampotunniste vuosi))
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
  ;; todo: toteuta
  )

(defn laheta-kohde-yhan [integraatioloki db url kohde-id]
  ;; todo: toteuta
  )

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YllapidonUrakoidenHallinta

  (hae-urakat [this yhatunniste sampotunniste vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) yhatunniste sampotunniste vuosi))
  (hae-kohteet [this urakka-id]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) urakka-id))
  (laheta-kohde [this kohde-id]
    (laheta-kohde-yhan (:integraatioloki this) (:db this) (:url (:yha asetukset)) kohde-id)))

