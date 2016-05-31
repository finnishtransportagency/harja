(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma
  (:require [harja.tyokalut.xml :as xml]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.tyokalut.merkkijono :as merkkijono])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/yha/")

(defn laske-hinta-kokonaishinta [{:keys [sopimuksen-mukaiset-tyot muutoshinta bitumi-indeksi arvonvahennykset kaasuindeksi]}]
  (reduce (fn [a b] (+ a (or b 0)))
          0
          [sopimuksen-mukaiset-tyot
           muutoshinta
           bitumi-indeksi
           arvonvahennykset
           kaasuindeksi]))

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

(defn tee-alikohde [{:keys [yhaid id nimi] :as alikohde}]
  [:alikohde
   (when yhaid [:yha-id yhaid])
   [:harja-id id]
   (tee-tierekisteriosoitevali alikohde)
   ;; todo: pitää tarkistaa pitäisikö tunnus tallentaa eri paikkaan.
   [:tunnus (merkkijono/leikkaa 1 nimi)]
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
     [:lisa-aineet "string"]]]])

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

(defn tee-kohde [{:keys [yhaid id tyyppi yhatunnus] :as kohde}
                 alikohteet
                 {:keys [aloituspvm valmispvm-paallystys valmispvm-kohde takuupvm] :as paallystys-ilmoitus}]
  [:kohde
   (when [:yha-id yhaid])
   [:harja-id id]
   [:kohdetyotyyppi tyyppi]
   (when yhatunnus [:nimi yhatunnus])
   (when aloituspvm [:toiden-aloituspaivamaara (xml/parsi-paivamaara aloituspvm)])
   (when valmispvm-paallystys [:paallystyksen-valmistumispaivamaara (xml/parsi-paivamaara valmispvm-paallystys)])
   (when valmispvm-kohde [:kohteen-valmistumispaivamaara (xml/parsi-paivamaara valmispvm-kohde)])
   (when takuupvm [:takuupaivamaara (xml/parsi-paivamaara takuupvm)])
   [:toteutunuthinta (laske-hinta-kokonaishinta paallystys-ilmoitus)]
   (tee-tierekisteriosoitevali kohde)
   (tee-alustalle-tehdyt-toimenpiteet paallystys-ilmoitus)
   (when alikohteet
     (reduce conj [:alikohteet] (mapv tee-alikohde alikohteet)))])

(defn tee-kohteet [kohteet]
  (reduce conj [:kohteet] (mapv #(tee-kohde (:kohde %) (:alikohteet %) (:paallystys-ilmoitus %)) kohteet)))

(defn muodosta-sanoma [urakka kohteet]
  [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.liikennevirasto.fi/xsd/yha"}
   [:urakka
    [:yha-id (:yhaid urakka)]
    [:harja-id (:harjaid urakka)]
    [:sampotunnus (:sampoid urakka)]
    [:tunnus (:yhatunnus urakka)]
    (tee-kohteet kohteet)]])

(defn muodosta [urakka kohteet]
  (let [sisalto (muodosta-sanoma urakka kohteet)
        xml (xml/tee-xml-sanoma sisalto)]
    ;; todo: poista
    (println xml)
    (if (xml/validoi +xsd-polku+ "yha.xsd" xml)
      xml
      (let [virheviesti "Kohdetta ei voi lähettää YHA:n. XML ei ole validia."]
        (log/error virheviesti)
        (throw+ {:type :invalidi-yha-kohde-xml
                 :error virheviesti})))))

