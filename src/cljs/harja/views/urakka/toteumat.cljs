(ns harja.views.urakka.toteumat
  "Urakan 'Toteumat' välilehti:"
  (:require [harja.ui.bootstrap :as bs]
            [harja.views.urakka.toteumat.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.views.urakka.toteumat.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.toteumat.muut-tyot :as muut-tyot]
            [harja.views.urakka.toteumat.erilliskustannukset :as erilliskustannukset]
            [harja.views.urakka.toteumat.maarien-toteumat :as maarien-toteumat-nakyma]
            [harja.views.urakka.toteumat.muut-materiaalit :refer [muut-materiaalit-nakyma]]
            [harja.views.urakka.toteumat.varusteet :as varusteet]
            [harja.views.urakka.toteumat.talvisuola :refer [talvisuolatoteumat]]
            [harja.views.urakka.toteumat.pohjavesialueiden-suola :as pohjavesialueiden-suola]
            [harja.views.urakka.toteumat.velho-varusteet :as velho-varusteet]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.pvm :as pvm]
            [harja.domain.roolit :as roolit]))


(defn toteumat
  "Toteumien pääkomponentti"
  [ur]
  (let [mhu-urakka? (= :teiden-hoito (:tyyppi ur))
        hj-urakka? (and (= :teiden-hoito (:tyyppi ur)) (< (pvm/vuosi (:alkupvm ur)) 2019))]
    (komp/luo
      (komp/sisaan-ulos #(do
                           (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                           (nav/vaihda-kartan-koko! :S))
                        #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
      (fn [{:keys [id] :as ur}]
        [bs/tabs {:style :tabs :classes "tabs-taso2"
                  :active (nav/valittu-valilehti-atom :toteumat)}

         "Kokonaishintaiset työt" :kokonaishintaiset-tyot
         (when (and (oikeudet/urakat-toteumat-kokonaishintaisettyot id)
                    (not mhu-urakka?))
           [kokonaishintaiset-tyot/kokonaishintaiset-toteumat])

         "Yksikköhintaiset työt" :yksikkohintaiset-tyot
         (when (and (oikeudet/urakat-toteumat-yksikkohintaisettyot id)
                    (not mhu-urakka?))
           [yks-hint-tyot/yksikkohintaisten-toteumat])

         "Tehtävät" :maarien-toteumat
         (when (and (oikeudet/urakat-toteumat-kokonaishintaisettyot id)
                    (#{:teiden-hoito} (:tyyppi ur)))
           [maarien-toteumat-nakyma/maarien-toteumat])

         "Muutos- ja lisätyöt" :muut-tyot
         (when (and (oikeudet/urakat-toteumat-muutos-ja-lisatyot id)
                    (not mhu-urakka?))
           [muut-tyot/muut-tyot-toteumat ur])

         "Talvisuola" :talvisuola
         (when (and (oikeudet/urakat-toteumat-suola id)
                    (#{:hoito :teiden-hoito} (:tyyppi ur)))
           [talvisuolatoteumat])

         "Pohjavesialueiden suola" :pohjavesialueiden-suola
         (when (and (oikeudet/urakat-toteumat-suola id)
                    (#{:hoito :teiden-hoito} (:tyyppi ur)))
           [pohjavesialueiden-suola/pohjavesialueiden-suola])

         "Muut materiaalit" :muut-materiaalit
         (when (oikeudet/urakat-toteumat-materiaalit id)
           [muut-materiaalit-nakyma ur])

         "Erilliskustannukset" :erilliskustannukset
         (when (and (oikeudet/urakat-toteumat-erilliskustannukset id)
                 ;; Piilotetaan Erilliskustannukset-tab 'teiden-hoito' (eli mh-urakoilta), paitsi HJ-urakoilta.
                 ;; HJ-urakat ovat 'teiden-hoito'-urakoita, jotka ovat alkaneet ennen vuotta 2019
                 ;; VHAR-6675
                 (or hj-urakka? (not mhu-urakka?)))
           [erilliskustannukset/erilliskustannusten-toteumat ur])

         "Varusteet" :varusteet-ulkoiset
         (when (and (istunto/ominaisuus-kaytossa? :varusteet-ulkoiset)
                 (roolit/jvh? @istunto/kayttaja)
                 (oikeudet/urakat-toteumat-varusteet id)
                 (#{:hoito :teiden-hoito} (:tyyppi ur)))
           [velho-varusteet/velho-varusteet ur])

         "Vanhat varustekirjaukset (Tierekisteri)" :varusteet
         (when (and (istunto/ominaisuus-kaytossa? :tierekisterin-varusteet)
                    (oikeudet/urakat-toteumat-varusteet id)
                    (#{:hoito :teiden-hoito} (:tyyppi ur)))
           [varusteet/varusteet])]))))
