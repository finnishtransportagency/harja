(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-kohdeluettelo
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.ui.bootstrap :as bs]
            [harja.domain.roolit :as roolit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumat :as toteumat]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as paikkauskohteet]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset :as paallystysilmoitukset]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]))


(defn- nayta-paallystysilmoitukset? [urakka]
  (let [kayttaja @istunto/kayttaja
        urakoitsija? (t-paikkauskohteet/kayttaja-on-urakoitsija? (roolit/urakkaroolit kayttaja urakka))
        tilaaja? (roolit/kayttaja-on-laajasti-ottaen-tilaaja? (roolit/urakkaroolit kayttaja urakka) kayttaja)]
    (or (roolit/jvh? kayttaja)
        urakoitsija?
        tilaaja?)))

(defn paikkaukset* [e! app-state]
  (komp/luo
    (komp/ulos
      #(do
         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? false)
         (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [e! {ur :urakka :as app-state}]
      (let [hoitourakka? (or (= :hoito (:tyyppi ur)) (= :teiden-hoito (:tyyppi ur)))
            tilaaja? (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
                       (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                       @istunto/kayttaja)
            nayta-paallystysilmoitukset? (nayta-paallystysilmoitukset? (:id ur))]
        [:span.kohdeluettelo
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :kohdeluettelo-paikkaukset)}

          "Paikkauskohteet"
          :paikkauskohteet
          ;; Jos urakan tyyppi on päällystys, niin aina näytetään päällystyskohteet tabi
          ;; Hoitourakoiden urakanvalvojille (aluevastaava) näytetään tällä hetkellä vain Päällystysurakoiden paikkauskohteet,
          ;; koska tarkempi määrittely hoitourakoiden paikkauskohteiden tekemisestä vielä odottaa.
          ;; Eli piilotetaan tässä vaiheessa vielä välilehti muilta, kuin päällystys -urakoilta
          (when (and
                  (= :paallystys (:tyyppi ur))
                  (oikeudet/urakat-paikkaukset-paikkauskohteet (:id ur)))
            [paikkauskohteet/paikkauskohteet e! app-state])

          "Toteumat"
          :toteumat
          (when (and (oikeudet/urakat-paikkaukset-toteumat (:id ur))
                     (or (= :paallystys (:tyyppi ur))
                         (and hoitourakka? tilaaja?)))
            [toteumat/toteumat ur])

          "Päällystysurakoiden paikkaukset"
          :paallystysurakoiden-paikkauskohteet
          ;; Tiemerkkareille ja aluevastaaville, jotka katsovat :hoito tyyppistä urakkaa, näytetään muiden paikkauksia.
          ;; Tarkistetaan siis, että
          ;; urakka on joko hoitourakka tai tiemerkintäurakka
          ;; Ja että käyttäjällä on oikeudet katsoa urakat-paikkaukset-paikkauskohteet asioita
          (when (and
                  ;; Oikeudet paikkauskohteisiin on oltava
                  (oikeudet/urakat-paikkaukset-paikkauskohteet (:id ur))
                  (or
                    ;; Voi olla joko hoitourakka
                    hoitourakka?
                    ;; Tai tiemerkintäurakka
                    (= :tiemerkinta (:tyyppi ur))))
            (if hoitourakka?
              [paikkauskohteet/aluekohtaiset-paikkauskohteet e! app-state]
              [paikkauskohteet/paikkauskohteet e! app-state]))

          "Päällystysilmoitukset"
          :paikkausten-paallystysilmoitukset
          (when (and
                  (= :paallystys (:tyyppi ur))
                  nayta-paallystysilmoitukset?)
            [paallystysilmoitukset/paallystysilmoitukset e! app-state])]]))))

(defn paikkaukset [ur]
  (swap! tila/paikkauskohteet assoc :urakka ur)
  [tuck/tuck tila/paikkauskohteet paikkaukset*])
