(ns harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-lahetyssanoma
  (:require [harja.kyselyt.urakat :as q-urakka]
            [harja.kyselyt.paikkaus :as q-paikkaus]
            [harja.tyokalut.xml :as xml]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.domain.paallystysilmoitus :as paallyste] ;; jos
            [cheshire.core :as cheshire]
            [namespacefy.core :refer [unnamespacefy]]
            [clojure.walk :refer [prewalk]]
            [clojure.set :refer [rename-keys]])
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
      (when rc% [:rc-prosentti rc%])
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

(defn tee-alustalle-tehty-toimenpide [{:keys [verkkotyyppi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                                              tr-ajorata tr-kaista verkon-tarkoitus kasittelymenetelma tekninen-toimenpide paksuus
                                              verkon-sijainti]}
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



(defn siivoa-nimiavaruus [data]
  (prewalk #(if (map? %)
              (unnamespacefy %)
              %) data)
  )

(defn muodosta-sanoma [urakka kohde paikkaukset]


  (let [urakka (prewalk #(if (map? %)
                           (unnamespacefy %)
                           %) urakka)
        paikkaukset (prewalk #(if (map? %)
                                (unnamespacefy %)
                                %) paikkaukset)
        kohde (prewalk #(if (map? %)
                          (unnamespacefy %)
                          %) kohde)
        urakka {:urakka (rename-keys urakka {:id :harja-id})}
        kasittele-materiaali (fn [m]
                               {:kivi-ja-sideaine (-> m
                                                            (dissoc :materiaali-id)
                                                            (rename-keys {:kuulamylly-arvo :km-arvo})
                                                            )})
        kasittele-paikkaus (fn [p] {:paikkaus (-> p
                                                  (dissoc :sijainti :urakka-id :paikkauskohde-id :ulkoinen-id )
                                                  (rename-keys {:tierekisteriosoite :sijainti})
                                                  (assoc-in [:kuulamylly] (:nimi (first (filter
                                                                                          #(= (str (:koodi %)) (:kuulamylly p))
                                                                                          paallyste/+kuulamyllyt+))))
                                                  (conj {:kivi-ja-sideaineet (into [] (map kasittele-materiaali (:materiaalit p)))})
                                                  (dissoc :materiaalit)
                                                  )})
        kohteet {:paikkauskohteet [{:paikkauskohde (-> kohde
                                                       (dissoc :ulkoinen-id)
                                                       (rename-keys {:id :harja-id})
                                                       (conj {:paikkaukset (into [] (map kasittele-paikkaus paikkaukset))}))}]}
        sanomasisalto (merge urakka kohteet)




        _ (println "*** SISÄLTÖ " (cheshire/encode sanomasisalto))


        ;;sanoma (cheshire/encode (conj urakka kohde paikkaukset))

        ]


    ;(keep identity (map #(when (map? %)(conj *k %)) *p))

    )


  ;; VEKTORIIN conj ja sitten update

  ;(let [data (prewalk #(if (map? %)
  ;                       (unnamespacefy %)
  ;                       %) paikkaukset)
  ;      kamat {:urakka urakka
  ;             :paikkauskohteet [(map #(assoc {} :paikkaus %) data)]
  ;
  ;             }
  ;
  ;      sanoma (cheshire/encode data)
  ;
  ;
  ;      (map fn[x](
  ;                 {:paikkauskohde }
  ;                     (map fn[z]() x)
  ;
  ;                      ) data)
  ;
  ;
  ;      ]
  ;
  ;
  ;  (println "DATA count" (count data))
  ;
  ;  (println "DATA " data)
  ;  (println "KAMAT " kamat)
  ;   (println "SANOMA " sanoma)
  ;  )

  )

(defn muodosta [db urakka-id kohde-id]
  (let [urakka (first (q-urakka/hae-urakan-nimi db {:urakka urakka-id}))
        kohde (first (q-paikkaus/hae-paikkauskohteet db {:harja.domain.paikkaus/id               kohde-id ;; hakuparametrin nimestä huolimatta haku tehdään paikkauskohteen id:llä - haetaan siis yksittäisen paikkauskohteen tiedot
                                                         :harja.domain.muokkaustiedot/poistettu? false}))
        paikkaukset (q-paikkaus/hae-paikkaukset-materiaalit db {:harja.domain.paikkaus/paikkauskohde-id kohde-id
                                                                :harja.domain.muokkaustiedot/poistettu? false})
        sisalto (muodosta-sanoma urakka kohde paikkaukset)
        xml (xml/tee-xml-sanoma sisalto)]
    (if-let [virheet (xml/validoi-xml +xsd-polku+ "yha.xsd" xml)]
      (let [virheviesti (format "Kohdetta ei voi lähettää YHAan. XML ei ole validia. Validointivirheet: %s" virheet)]
        (log/error virheviesti)
        (throw+ {:type  :invalidi-yha-kohde-xml
                 :error virheviesti}))
      xml)))



