(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma
  (:require [harja.tyokalut.xml :as xml]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/yha/")

(defn laske-hinta-kokonaishinta [kohde]
  (let [vuosi (:vuodet kohde)]
    (yllapitokohteet-domain/yllapitokohteen-kokonaishinta kohde vuosi)))

(defn kasittele-kohteen-tyyppi [tyyppi]
  (case tyyppi
    "paallyste" 1
    "kevytliikenne" 2
    "sora" 3
    1))

(defn tee-tierekisteriosoitevali [osoite]
  [:tierekisteriosoitevali
   [:karttapaivamaara (xml/formatoi-paivamaara (if (:karttapaivamaara osoite) (:karttapaivamaara osoite) (pvm/nyt)))]
   [:tienumero (:tr-numero osoite)]
   [:aosa (:tr-alkuosa osoite)]
   [:aet (:tr-alkuetaisyys osoite)]
   [:losa (:tr-loppuosa osoite)]
   [:let (:tr-loppuetaisyys osoite)]
   (when (:tr-ajorata osoite)
     [:ajorata (:tr-ajorata osoite)])
   (when (:tr-kaista osoite)
     [:kaista (:tr-kaista osoite)])])

(def tyomenetelmasta-ei-tietoa 99)

(defn tee-alikohde [{:keys [yhaid id paallystetyyppi raekoko kokonaismassamaara massamenekki rc% kuulamylly
                            pot2-tyomenetelma tyomenetelma leveys pinta-ala esiintyma km-arvo litteyslukuluokka muotoarvo sideainetyyppi pitoisuus
                            lisaaineet poistettu] :as alikohde}]
  ;; Huom! pot2:ssa on lähetettävä litteyslukuluokka YHA:an, mutta muotoarvo Velhoon https://knowledge.solita.fi/display/HAR/Materiaalikirjasto
  ;; POT1:ssä muotoarvo tulee kentässä muotoarvo, mutta pot2:ssa käytetään litteyslukuluokkaa
  (let [muotoarvo (or litteyslukuluokka muotoarvo)]
    [:alikohde
     ;; tämän oltava paikkauskohteiden potissa aina nil (ja onkin)
     (when yhaid [:yha-id yhaid])
     [:harja-id id]
     [:poistettu (if poistettu 1 0)]
     (tee-tierekisteriosoitevali alikohde)
     (when
       (or paallystetyyppi raekoko massamenekki kokonaismassamaara rc% kuulamylly pot2-tyomenetelma tyomenetelma leveys pinta-ala)
       [:paallystystoimenpide
        (when paallystetyyppi [:uusi-paallyste paallystetyyppi])
        (when raekoko [:raekoko raekoko])
        (when massamenekki [:massamenekki massamenekki])
        (when kokonaismassamaara [:kokonaismassamaara kokonaismassamaara])
        (when rc% [:rc-prosentti (int rc%)])
        (when kuulamylly [:kuulamylly kuulamylly])
        [:paallystetyomenetelma (or pot2-tyomenetelma
                                    tyomenetelma
                                    tyomenetelmasta-ei-tietoa)]
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
       [:lisa-aineet lisaaineet]]]]))

(defn tee-alustalle-tehty-toimenpide [{:keys [verkkotyyppi verkon-tyyppi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                                              tr-ajorata tr-kaista verkon-tarkoitus kasittelymenetelma paksuus lisatty-paksuus
                                              verkon-sijainti toimenpide kasittelysyvyys massamenekki]}
                                      kohteen-tienumero karttapvm]
  (let [tekninen-toimenpide (if-not (#{42 41 32 31 4} toimenpide) ;; LJYR TJYR TAS TASK REM-TAS
                              4 ;; "Kevyt rakenteen parantaminen"
                              9)] ;; "ei tiedossa"
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
     (when-let [kasittelysyvyys (or paksuus kasittelysyvyys lisatty-paksuus)]
       [:kasittelypaksuus kasittelysyvyys])
     (when-let [verkkotyyppi (or verkkotyyppi verkon-tyyppi)]
       [:verkkotyyppi verkkotyyppi])
     (when verkon-tarkoitus
       [:verkon-tarkoitus verkon-tarkoitus])
     (when verkon-sijainti
       [:verkon-sijainti verkon-sijainti])
     [:tekninen-toimenpide tekninen-toimenpide]
     (when massamenekki [:massamenekki massamenekki])]))

;; YHA ohjaa paikkauskohteiden pot-lomakkeet poikkeuskäsittelyllä yhteisesti sovitun kohde id:n avulla
(def paikkauskohteiden-yha-id 99)

(defn tee-kohde [{:keys [yhaid yha-kohdenumero id yllapitokohdetyyppi yllapitokohdetyotyyppi tr-numero
                         karttapaivamaara nimi tunnus paikkauskohde-id] :as kohde}
                 alikohteet
                 {:keys [aloituspvm valmispvm-paallystys valmispvm-kohde takuupvm ilmoitustiedot paikkauskohde-toteutunut-hinta] :as paallystysilmoitus}]
  [:kohde
   ;; Tämän yhaid:n oltava paikkauskohteen POT:eilla aina 99
   [:yha-id (if paikkauskohde-id
              paikkauskohteiden-yha-id
              yhaid)]
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
   [:toteutunuthinta (if paikkauskohde-id
                       paikkauskohde-toteutunut-hinta
                       (laske-hinta-kokonaishinta paallystysilmoitus))]
   (tee-tierekisteriosoitevali (dissoc kohde :tr-ajorata :tr-kaista))
   (when (:alustatoimet ilmoitustiedot)
     (reduce conj [:alustalle-tehdyt-toimet]
             (mapv #(tee-alustalle-tehty-toimenpide % tr-numero karttapaivamaara)
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


