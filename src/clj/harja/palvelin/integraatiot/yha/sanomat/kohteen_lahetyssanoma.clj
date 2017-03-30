(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma
  (:require [harja.tyokalut.xml :as xml]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/yha/")

(defn laske-hinta-kokonaishinta [{:keys [sopimuksen-mukaiset-tyot maaramuutokset muutoshinta bitumi-indeksi
                                         arvonvahennykset kaasuindeksi sakot-ja-bonukset] :as kohde}]
  (reduce + 0 (remove nil? [sopimuksen-mukaiset-tyot maaramuutokset muutoshinta bitumi-indeksi
                            arvonvahennykset kaasuindeksi sakot-ja-bonukset])))

(defn kasittele-kohteen-tyyppi [tyyppi]
  (case tyyppi
    "paallyste" 1
    "sora" 2
    "kevytliikenne" 3
    1))

(defn tee-tierekisteriosoitevali [osoite]
  [:tierekisteriosoitevali
   [:karttapaivamaara (xml/formatoi-paivamaara (if (:karttapvm osoite) (:karttapvm osoite) (pvm/nyt)))]
   [:tienumero (:tr-numero osoite)]
   [:aosa (:tr-alkuosa osoite)]
   [:aet (:tr-alkuetaisyys osoite)]
   [:losa (:tr-loppuosa osoite)]
   [:let (:tr-loppuetaisyys osoite)]
   [:ajorata (:tr-ajorata osoite)]
   [:kaista (:tr-kaista osoite)]])

(defn tee-alikohde [{:keys [yhaid id tunnus paallystetyyppi raekoko kokonaismassamaara massamenekki rc% kuulamylly
                            tyomenetelma leveys pinta-ala esiintyma km-arvo muotoarvo sideainetyyppi pitoisuus
                            lisaaineet] :as alikohde}]
  [:alikohde
   (when yhaid [:yha-id yhaid])
   [:harja-id id]
   (tee-tierekisteriosoitevali alikohde)
   [:tunnus tunnus]
   (when
     (or paallystetyyppi raekoko massamenekki kokonaismassamaara rc% kuulamylly tyomenetelma leveys pinta-ala)
     [:paallystystoimenpide
      (when paallystetyyppi [:uusi-paallyste paallystetyyppi])
      (when raekoko [:raekoko raekoko])
      (when massamenekki [:massamenekki massamenekki])
      (when kokonaismassamaara [:kokonaismassamaara kokonaismassamaara])
      (when rc% [:rc-prosentti rc%])
      (when kuulamylly [:kuulamylly kuulamylly])
      (when tyomenetelma [:paallystetyomenetelma tyomenetelma])
      (when leveys [:leveys leveys])
      (when pinta-ala [:pinta-ala pinta-ala])])
   ;; todo: täytyy varmistaa pitääkö alikohteelle voida kirjata useampia materiaaleja
   [:materiaalit
    [:materiaali
     (when esiintyma [:kiviainesesiintyman-nimi esiintyma])
     (when km-arvo [:kiviaineksen-km-arvo km-arvo])
     (when muotoarvo [:kiviaineksen-muotoarvo muotoarvo])
     (when sideainetyyppi [:sideainetyyppi sideainetyyppi])
     (when pitoisuus [:sideainepitoisuus pitoisuus])
     [:lisa-aineet lisaaineet]]]])

(defn tee-alustalle-tehty-toimenpide [{:keys [verkkotyyppi tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                                              verkon-tarkoitus kasittelymenetelma tekninen-toimenpide paksuus
                                              verkon-sijainti]}
                                      tienumero karttapvm]
  [:alustalle-tehty-toimenpide
   [:tierekisteriosoitevali
    [:karttapaivamaara (xml/formatoi-paivamaara (if karttapvm karttapvm (pvm/nyt)))]
    [:tienumero tienumero]
    [:aosa tr-alkuosa]
    [:aet tr-alkuetaisyys]
    [:losa tr-loppuosa]
    [:let tr-loppuetaisyys]]
   [:kasittelymenetelma kasittelymenetelma]
   [:kasittelypaksuus paksuus]
   (when verkkotyyppi
     [:verkkotyyppi verkkotyyppi])
   (when verkon-tarkoitus
     [:verkon-tarkoitus verkon-tarkoitus])
   (when verkon-sijainti
     [:verkon-sijainti verkon-sijainti])
   (when tekninen-toimenpide
     [:tekninen-toimenpide tekninen-toimenpide])])

(defn tee-kohde [{:keys [yhaid yha-kohdenumero id yllapitokohdetyyppi yllapitokohdetyotyyppi tr-numero
                         karttapvm nimi] :as kohde}
                 alikohteet
                 {:keys [aloituspvm valmispvm-paallystys valmispvm-kohde takuupvm ilmoitustiedot] :as paallystysilmoitus}]
  [:kohde
   (when yhaid [:yha-id yhaid])
   [:harja-id id]
   (when yha-kohdenumero [:kohdenumero yha-kohdenumero])
   [:kohdetyyppi (kasittele-kohteen-tyyppi yllapitokohdetyyppi)]
   [:kohdetyotyyppi yllapitokohdetyotyyppi]
   (when nimi [:nimi nimi])
   (when aloituspvm [:toiden-aloituspaivamaara (xml/formatoi-paivamaara aloituspvm)])
   (when valmispvm-paallystys [:paallystyksen-valmistumispaivamaara (xml/formatoi-paivamaara valmispvm-paallystys)])
   (when valmispvm-kohde [:kohteen-valmistumispaivamaara (xml/formatoi-paivamaara valmispvm-kohde)])
   (when takuupvm [:takuupaivamaara (xml/formatoi-paivamaara takuupvm)])
   [:toteutunuthinta (laske-hinta-kokonaishinta paallystysilmoitus)]
   (tee-tierekisteriosoitevali kohde)
   (when (:alustatoimet ilmoitustiedot)
     (reduce conj [:alustalle-tehdyt-toimet]
             (mapv #(tee-alustalle-tehty-toimenpide % tr-numero karttapvm)
                   (:alustatoimet ilmoitustiedot))))
   (when alikohteet
     (reduce conj [:alikohteet]
             (mapv tee-alikohde alikohteet)))])

(defn muodosta-sanoma [{:keys [yhaid harjaid sampoid yhatunnus]} kohteet]
  [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.liikennevirasto.fi/xsd/yha"}
   [:urakka
    [:yha-id yhaid]
    [:harja-id harjaid]
    [:sampotunnus sampoid]
    [:tunnus yhatunnus]
    (reduce conj [:kohteet] (mapv #(tee-kohde (:kohde %) (:alikohteet %) (:paallystysilmoitus %)) kohteet))]])

(defn muodosta [urakka kohteet]
  (let [sisalto (muodosta-sanoma urakka kohteet)
        xml (xml/tee-xml-sanoma sisalto)]
    (if-let [virheet (xml/validoi-xml +xsd-polku+ "yha.xsd" xml)]
      (let [virheviesti (format "Kohdetta ei voi lähettää YHA:n. XML ei ole validia. Validointivirheet: %s" virheet)]
        (log/error virheviesti)
        (throw+ {:type :invalidi-yha-kohde-xml
                 :error virheviesti}))
      xml)))


