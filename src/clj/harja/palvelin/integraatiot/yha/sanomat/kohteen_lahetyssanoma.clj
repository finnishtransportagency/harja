(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma
  (:require [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "xsd/yha/")

(defn tee-alikohteet []
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

(defn tee-alustalle-tehdyt-toimenpiteet []
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

(defn tee-kohteet []
  [:kohteet
   [:kohde
    [:yha-id "3"]
    [:harja-id "3"]
    [:kohdetyotyyppi "paikkaus"]
    [:nimi "string"]
    [:toiden-aloituspaivamaara "2007-10-26"]
    [:paallystyksen-valmistumispaivamaara "2004-02-14"]
    [:kohteen-valmistumispaivamaara "2018-11-01+02:00"]
    [:takuupaivamaara "2013-05-22+03:00"]
    [:toteutunuthinta
     "1000.00"]
    [:tierekisteriosoitevali
     [:karttapaivamaara "2016-01-01+02:00"]
     [:tienumero "3"]
     [:ajorata "0"]
     [:kaista "11"]
     [:aosa "3"]
     [:aet "3"]
     [:losa "3"]
     [:let "3"]]
    (tee-alustalle-tehdyt-toimenpiteet)
    (tee-alikohteet)]])

(defn muodosta-sanoma []
  [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.liikennevirasto.fi/xsd/yha"}
   [:urakka
    [:yha-id "3"]
    [:harja-id "3"]
    [:sampotunnus "string"]
    [:tunnus "string"]
    (tee-kohteet)]])

(defn muodosta [kohde alikohteet paallystys-ilmoitus]
  (let [sisalto (muodosta-sanoma)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "yha.xsd" xml)
      xml
      (let [virheviesti "Kohdetta ei voi lähettää YHA:n. XML ei ole validia."]
        (log/error virheviesti)
        (throw+ {:type :invalidi-yha-kohde-xml
                 :error virheviesti})))))

