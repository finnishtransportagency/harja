(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma
  (:require [harja.tyokalut.xml :as xml]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/yha/")


(defn tee-tierekisteriosoitevali [osoite]
  [:tierekisteriosoitevali
   [:karttapaivamaara (xml/formatoi-paivamaara (if (:karttapvm osoite) (:karttapvm osoite) (pvm/nyt)))]
   [:tienumero (:tr_numero osoite)]
   [:ajorata (:tr_ajorata osoite)]
   [:kaista (:tr_kaista osoite)]
   [:aosa (:tr_alkuosa osoite)]
   [:aet (:tr_alkuetaisyys osoite)]
   [:losa (:tr_loppuosa osoite)]
   [:let (:tr_loppuetaisyys osoite)]])

(defn tee-alikohteet [alikohteet]
  [:alikohteet
   [:alikohde
    [:yha-id "3"]
    [:harja-id "3"]
    [:tierekisteriosoitevali
     [:karttapaivamaara "2016-01-01"]
     [:tienumero "3"]
     [:ajorata "0"]
     [:kaista "22"]
     [:aosa "3"]
     [:aet "3"]
     [:losa "3"]
     [:let "3"]]
    [:nimi "A"]
    [:paallystystoimenpide
     [:uusi-paallyste "1"]
     [:raekoko "87"]
     [:kokonaismassamaara "100"]
     [:rc-prosentti "12"]
     [:kuulamylly "4"]
     [:paallystetyomenetelma "21"]
     [:leveys "100"]
     [:pinta-ala "100"]]
    [:materiaalit
     [:materiaali
      [:kiviainesesiintyman-nimi "string"]
      [:kiviaineksen-km-arvo "string"]
      [:kiviaineksen-muotoarvo "string"]
      [:sideainetyyppi "21"]
      [:sideainepitoisuus "1000"]
      [:lisa-aineet "string"]]]]]
  )

(defn tee-alustalle-tehdyt-toimenpiteet [paallystys-ilmoitus]
  [:alustalle-tehdyt-toimet
   [:alustalle-tehty-toimenpide
    [:tierekisteriosoitevali
     [:karttapaivamaara "2016-01-01+02:00"]
     [:tienumero "3"]
     [:ajorata "0"]
     [:kaista "11"]
     [:aosa "3"]
     [:aet "3"]
     [:losa "3"]
     [:let "3"]]
    [:kasittelymenetelma "12"]
    [:kasittelypaksuus "100"]
    [:verkkotyyppi "4"]
    [:verkon-tarkoitus "1"]
    [:verkon-sijainti "1"]
    [:tekninen-toimenpide "4"]]])

(defn tee-kohteet [kohde alikohteet paallystys-ilmoitus]
  [:kohteet
   [:kohde
    [:yha-id (:yhaid kohde)]
    [:harja-id (:id kohde)]
    [:kohdetyotyyppi (:tyyppi kohde)]
    [:nimi (:yhatunnus kohde)]
    ;; todo: selvitä mistä löytyy
    [:toiden-aloituspaivamaara "2007-10-26"]
    ;; todo: selvitä mistä löytyy
    [:paallystyksen-valmistumispaivamaara "2004-02-14"]
    ;; todo: selvitä mistä löytyy
    [:kohteen-valmistumispaivamaara "2018-11-01+02:00"]
    ;; todo: selvitä mistä löytyy
    [:takuupaivamaara "2013-05-22+03:00"]
    ;; todo: selvitä mistä löytyy
    [:toteutunuthinta "1000.00"]
    (tee-tierekisteriosoitevali kohde)
    (tee-alustalle-tehdyt-toimenpiteet paallystys-ilmoitus)
    (tee-alikohteet alikohteet)]])

(defn muodosta-sanoma [kohde alikohteet paallystys-ilmoitus]
  [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.liikennevirasto.fi/xsd/yha"}
   [:urakka
    [:yha-id (:yha_urakka_id kohde)]
    [:harja-id (:harja_urakka_id kohde)]
    [:sampotunnus (:sampo_urakka_id kohde)]
    [:tunnus (:yha_urakka_tunnus kohde)]
    (tee-kohteet kohde alikohteet paallystys-ilmoitus)]])

(defn muodosta [kohde alikohteet paallystys-ilmoitus]
  (let [sisalto (muodosta-sanoma kohde alikohteet paallystys-ilmoitus)
        xml (xml/tee-xml-sanoma sisalto)]
    ;; todo: poista
    (println xml)
    (if (xml/validoi +xsd-polku+ "yha.xsd" xml)
      xml
      (let [virheviesti "Kohdetta ei voi lähettää YHA:n. XML ei ole validia."]
        (log/error virheviesti)
        (throw+ {:type :invalidi-yha-kohde-xml
                 :error virheviesti})))))

