(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma
  (:require [harja.tyokalut.xml :as xml]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
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
   [:aosa (:tr_alkuosa osoite)]
   [:aet (:tr_alkuetaisyys osoite)]
   [:losa (:tr_loppuosa osoite)]
   [:let (:tr_loppuetaisyys osoite)]
   ;; todo: pitää laittaa pakolliseksi frontille
   [:ajorata (:tr_ajorata osoite)]
   ;; todo: pitää laittaa pakolliseksi frontille
   [:kaista (:tr_kaista osoite)]])

(defn tee-alikohde [{:keys [yhaid id tunnus paallystetyyppi raekoko massa rc% kuulamylly tyomenetelma leveys pinta-ala
                            esiintyma km-arvo muotoarvo sideainetyyppi pitoisuus lisaaineet] :as alikohde}]
  [:alikohde
   (when yhaid [:yha-id yhaid])
   [:harja-id id]
   (tee-tierekisteriosoitevali alikohde)
   [:tunnus tunnus]
   [:paallystystoimenpide
    (when paallystetyyppi [:uusi-paallyste paallystetyyppi])
    (when raekoko [:raekoko raekoko])
    (when massa [:kokonaismassamaara massa])
    (when rc% [:rc-prosentti rc%])
    (when kuulamylly [:kuulamylly kuulamylly])
    (when tyomenetelma [:paallystetyomenetelma tyomenetelma])
    (when leveys [:leveys leveys])
    (when pinta-ala [:pinta-ala pinta-ala])]
   ;; todo: täytyy varmistaa pitääkö alikohteelle voida kirjata useampia materiaaleja
   [:materiaalit
    [:materiaali
     (when esiintyma [:kiviainesesiintyman-nimi esiintyma])
     (when km-arvo [:kiviaineksen-km-arvo km-arvo])
     (when muotoarvo [:kiviaineksen-muotoarvo muotoarvo])
     (when sideainetyyppi [:sideainetyyppi sideainetyyppi])
     (when pitoisuus [:sideainepitoisuus pitoisuus])
     [:lisa-aineet lisaaineet]]]])

(defn tee-alustalle-tehty-toimenpide [{:keys [verkkotyyppi aosa let verkon-tarkoitus kasittelymenetelma losa aet
                                              tekninen-toimenpide paksuus verkon-sijainti]}
                                      tienumero karttapvm]
  [:alustalle-tehty-toimenpide
   [:tierekisteriosoitevali
    [:karttapaivamaara (xml/formatoi-paivamaara (if karttapvm karttapvm (pvm/nyt)))]
    [:tienumero tienumero]
    [:aosa aosa]
    [:aet aet]
    [:losa losa]
    [:let let]]
   [:kasittelymenetelma kasittelymenetelma]
   [:kasittelypaksuus paksuus]
   [:verkkotyyppi verkkotyyppi]
   [:verkon-tarkoitus verkon-tarkoitus]
   [:verkon-sijainti verkon-sijainti]
   [:tekninen-toimenpide tekninen-toimenpide]])

(defn tee-kohde [{:keys [yhaid id tyyppi yhatunnus tr_numero karttapvm] :as kohde}
                 alikohteet
                 {:keys [aloituspvm valmispvm-paallystys valmispvm-kohde takuupvm ilmoitustiedot] :as paallystys-ilmoitus}]
  [:kohde
   (when [:yha-id yhaid])
   [:harja-id id]
   [:kohdetyotyyppi tyyppi]
   (when yhatunnus [:nimi yhatunnus])
   (when aloituspvm [:toiden-aloituspaivamaara (xml/formatoi-paivamaara aloituspvm)])
   (when valmispvm-paallystys [:paallystyksen-valmistumispaivamaara (xml/formatoi-paivamaara valmispvm-paallystys)])
   (when valmispvm-kohde [:kohteen-valmistumispaivamaara (xml/formatoi-paivamaara valmispvm-kohde)])
   (when takuupvm [:takuupaivamaara (xml/formatoi-paivamaara takuupvm)])
   [:toteutunuthinta (laske-hinta-kokonaishinta paallystys-ilmoitus)]
   (tee-tierekisteriosoitevali kohde)
   (when (:alustatoimet ilmoitustiedot)
     (reduce conj [:alustalle-tehdyt-toimet]
             (mapv #(tee-alustalle-tehty-toimenpide % tr_numero karttapvm)
                   (:alustatoimet ilmoitustiedot))))
   (when alikohteet
     (reduce conj [:alikohteet]
             (mapv tee-alikohde alikohteet)))])

(defn muodosta-sanoma [urakka kohteet]
  [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.liikennevirasto.fi/xsd/yha"}
   [:urakka
    [:yha-id (:yhaid urakka)]
    [:harja-id (:harjaid urakka)]
    [:sampotunnus (:sampoid urakka)]
    [:tunnus (:yhatunnus urakka)]
    (reduce conj [:kohteet] (mapv #(tee-kohde (:kohde %) (:alikohteet %) (:paallystys-ilmoitus %)) kohteet))]])

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

