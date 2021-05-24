(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma
  (:require [harja.tyokalut.xml :as xml]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/yha/")

(defn laske-hinta-kokonaishinta [kohde]
  (yllapitokohteet-domain/yllapitokohteen-kokonaishinta kohde))

(defn kasittele-kohteen-tyyppi [tyyppi]
  (case tyyppi
    "paallyste" 1
    "kevytliikenne" 2
    "sora" 3
    1))

(defn tee-tierekisteriosoitevali [osoite]
  [:tierekisteriosoitevali
   [:karttapaivamaara (xml/formatoi-paivamaara (if (:karttapvm osoite) (:karttapvm osoite) (pvm/nyt)))]
   [:tienumero (:tr-numero osoite)]
   [:aosa (:tr-alkuosa osoite)]
   [:aet (:tr-alkuetaisyys osoite)]
   [:losa (:tr-loppuosa osoite)]
   [:let (:tr-loppuetaisyys osoite)]
   (when (:tr-ajorata osoite)
     [:ajorata (:tr-ajorata osoite)])
   (when (:tr-kaista osoite)
     [:kaista (:tr-kaista osoite)])])

(defn tee-alikohde [{:keys [yhaid id paallystetyyppi raekoko kokonaismassamaara massamenekki rc% kuulamylly
                            tyomenetelma leveys pinta-ala esiintyma km-arvo muotoarvo sideainetyyppi pitoisuus
                            lisaaineet poistettu] :as alikohde}]
  [:alikohde
   (when yhaid [:yha-id yhaid])
   [:harja-id id]
   [:poistettu (if poistettu 1 0)]
   (tee-tierekisteriosoitevali alikohde)
   (when
     (or paallystetyyppi raekoko massamenekki kokonaismassamaara rc% kuulamylly tyomenetelma leveys pinta-ala)
     [:paallystystoimenpide
      (when paallystetyyppi [:uusi-paallyste paallystetyyppi])
      (when raekoko [:raekoko raekoko])
      (when massamenekki [:massamenekki massamenekki])
      (when kokonaismassamaara [:kokonaismassamaara kokonaismassamaara])
      (when rc% [:rc-prosentti (int rc%)])
      (when kuulamylly [:kuulamylly kuulamylly])
      [:paallystetyomenetelma (or tyomenetelma
                                  ;; 99 = ei tietoa
                                  99)]
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

(defn tee-alustalle-tehty-toimenpide [{:keys [verkkotyyppi verkon-tyyppi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                                              tr-ajorata tr-kaista verkon-tarkoitus kasittelymenetelma paksuus lisatty-paksuus
                                              verkon-sijainti toimenpide kasittelysyvyys]}
                                      kohteen-tienumero karttapvm]
  [:alustalle-tehty-toimenpide
   [:tierekisteriosoitevali
    [:karttapaivamaara (xml/formatoi-paivamaara (if karttapvm karttapvm (pvm/nyt)))]
    ;; Tienumero on joko alustatoimenpiteelle määritelty tienumero, tai sen puuttuessa alustatoimenpiteen
    ;; oletetaan kohdistuvan pääkohteen kanssa samalle tielle eli käytetään pääkohteen tienumeroa.
    ;; Kaudella 2017 alustatoimenpiteelle ei kirjattu tienumeroa, mutta kaudella 2018 se kirjataan, koska
    ;; pääkohteen kanssa voidaan päällystää myös sellaisia alikohteita, jotka ovat eri tiellä kuin pääkohde
    [:tienumero (or tr-numero kohteen-tienumero)]
    [:aosa tr-alkuosa]
    [:aet tr-alkuetaisyys]
    [:losa tr-loppuosa]
    [:let tr-loppuetaisyys]
    [:ajorata tr-ajorata]
    [:kaista tr-kaista]]
    [:kasittelymenetelma (or kasittelymenetelma toimenpide)]
   (when-let [kasittelysyvyys (or paksuus kasittelysyvyys lisatty-paksuus 1)]
     [:kasittelypaksuus kasittelysyvyys])
   (when-let [verkkotyyppi (or verkkotyyppi verkon-tyyppi)]
     [:verkkotyyppi verkkotyyppi])
   (when verkon-tarkoitus
     [:verkon-tarkoitus verkon-tarkoitus])
   (when verkon-sijainti
     [:verkon-sijainti verkon-sijainti])
   (when-not (contains? #{42 41 32 31 4} toimenpide)      ;; LJYR TJYR TAS TASK REM-TAS
     [:tekninen-toimenpide 4])])                            ;; "Kevyt rakenteen parantaminen"

(defn tee-kohde [{:keys [yhaid yha-kohdenumero id yllapitokohdetyyppi yllapitokohdetyotyyppi tr-numero
                         karttapvm nimi tunnus] :as kohde}
                 alikohteet
                 {:keys [aloituspvm valmispvm-paallystys valmispvm-kohde takuupvm ilmoitustiedot] :as paallystysilmoitus}]
  [:kohde
   (when yhaid [:yha-id yhaid])
   [:harja-id id]
   (when yha-kohdenumero [:kohdenumero yha-kohdenumero])
   [:kohdetyyppi (kasittele-kohteen-tyyppi yllapitokohdetyyppi)]
   [:kohdetyotyyppi yllapitokohdetyotyyppi]
   (when nimi [:nimi nimi])
   (when tunnus [:tunnus tunnus])
   (when aloituspvm [:toiden-aloituspaivamaara (xml/formatoi-paivamaara aloituspvm)])
   (when valmispvm-paallystys [:paallystyksen-valmistumispaivamaara (xml/formatoi-paivamaara valmispvm-paallystys)])
   (when valmispvm-kohde [:kohteen-valmistumispaivamaara (xml/formatoi-paivamaara valmispvm-kohde)])
   (when takuupvm [:takuupaivamaara (xml/formatoi-paivamaara takuupvm)])
   [:toteutunuthinta (laske-hinta-kokonaishinta paallystysilmoitus)]
   (tee-tierekisteriosoitevali (dissoc kohde :tr-ajorata :tr-kaista))
   (when (:alustatoimet ilmoitustiedot)
     (reduce conj [:alustalle-tehdyt-toimet]
             (mapv #(tee-alustalle-tehty-toimenpide % tr-numero karttapvm)
                   (:alustatoimet ilmoitustiedot))))
   (when alikohteet
     (reduce conj [:alikohteet]
             (mapv tee-alikohde alikohteet)))])

(defn muodosta-sanoma [{:keys [yhaid harjaid sampoid yhatunnus]} kohteet]
  [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.vayla.fi/xsd/yha"}
   [:urakka
    [:yha-id yhaid]
    [:harja-id harjaid]
    [:sampotunnus sampoid]
    [:tunnus yhatunnus]
    (reduce conj [:kohteet] (mapv #(tee-kohde (:kohde %) (:alikohteet %) (:paallystysilmoitus %)) kohteet))]])

(defn muodosta [urakka kohteet]
  (let [sisalto (muodosta-sanoma urakka kohteet)
        xml (xml/tee-xml-sanoma sisalto)]
    (log/debug "Muodostettu XML sanoma: " (pr-str xml))
    (if-let [virheet (xml/validoi-xml +xsd-polku+ "yha.xsd" xml)]
      (let [virheviesti (format "Kohdetta ei voi lähettää YHAan. XML ei ole validia. Validointivirheet: %s" virheet)]
        (log/error virheviesti)
        (throw+ {:type :invalidi-yha-kohde-xml
                 :error virheviesti}))
      xml)))


