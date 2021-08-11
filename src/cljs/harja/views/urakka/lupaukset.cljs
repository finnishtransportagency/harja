(ns harja.views.urakka.lupaukset
  "Lupausten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.lupaukset :as tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.bootstrap :as bs]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.ui.kentat :as kentat]
            [harja.validointi :as v]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- pallo-ja-kk [e! kk]
  [:div.pallo-ja-kk
   [:div.circle-8]
   [:div.kk-nimi (pvm/kuukauden-lyhyt-nimi kk)]])

(defn- kuukausittainen-tilanne [e! ryhma]
  [:div.kk-tilanne
   (for [kk (concat (range 10 13) (range 1 10))]
     ^{:key (str "kk-tilanne-" kk)}
     [pallo-ja-kk e! kk])])

(defn- pisteet-div [pisteet teksti]
  [:div {:class (str "lupausryhman-" (str/lower-case teksti))}
   [:div.pisteet (or pisteet 0)]
   [:div.teksti teksti]])

(defn- lupausryhman-yhteenveto [e! {:keys [otsikko pisteet] :as ryhma}]
  [:div.lupausryhman-yhteenveto
   [:div.lupausryhman-otsikko-ja-pisteet
    [:h3.lupausryhman-otsikko otsikko]
    ;; fixme ennusteen laskenta backendistä urakoitsijoiden merkinnöistä tauluun lupaus_vastaus
    [:div.lupausryhman-pisteet
     [pisteet-div 0 "ENNUSTE"]
     [pisteet-div pisteet "MAX"]]]
   [:div.seuranta
    [:div.kannanotto-ja-nuoli
     [:div.kannanotto
      "Ota kuukausittain kantaa lupauksiin"]
     [:div.nuoli (ikonit/navigation-right)]]
    [kuukausittainen-tilanne e! ryhma]]])

(defn- pisteympyra
  "Pyöreä nappi, jonka numeroa voi tyypistä riippuen ehkä muokata."
  [tiedot toiminto urakka]
  (assert (#{:ennuste :toteuma :lupaus} (:tyyppi tiedot)) "Tyypin on oltava ennuste, toteuma tai lupaus")
  (let [oikeus-asettaa-luvatut-pisteet?
        (and
          (roolit/tilaajan-kayttaja? @istunto/kayttaja)
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet
                                    (:id urakka)))]
    [:div.inline-block.lupausympyra-container
     [:div {:on-click (when (and toiminto oikeus-asettaa-luvatut-pisteet?)
                        toiminto)
            :style {:cursor (when (and toiminto oikeus-asettaa-luvatut-pisteet?)
                              "pointer")}
            :class ["lupausympyra" (:tyyppi tiedot)]}
      [:h3 (:pisteet tiedot)]
      (when (and toiminto oikeus-asettaa-luvatut-pisteet?)
        (ikonit/action-edit))]
     [:div.lupausympyran-tyyppi (when-let [tyyppi (:tyyppi tiedot)] (name tyyppi))]]))

(defn- yhteenveto [e! {:keys [muokkaa-luvattuja-pisteita? lupaus-sitoutuminen] :as app} urakka]
  [:div.lupausten-yhteenveto
   [:div.otsikko-ja-kuukausi
    [:div "Yhteenveto"]
    [:h2.kuukausi (pvm/kuluva-kuukausi-isolla)]]
   [:div.lupauspisteet
    [pisteympyra {:pisteet 0
                  :tyyppi :ennuste} nil urakka]
    (if muokkaa-luvattuja-pisteita?
      [:div.lupauspisteen-muokkaus-container
       [:div.otsikko "Luvatut pisteet"]
       [kentat/tee-kentta {:tyyppi :positiivinen-numero :kokonaisluku? true
                           :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100 1))}
        (r/wrap (get-in app [:lupaus-sitoutuminen :pisteet])
                (fn [pisteet]
                  (e! (tiedot/->LuvattujaPisteitaMuokattu pisteet))))]
       [napit/yleinen-ensisijainen "Valmis"
        #(e! (tiedot/->TallennaLupausSitoutuminen (:urakka @tila/yleiset)))
        {:luokka "lupauspisteet-valmis"}]]
      [pisteympyra (merge lupaus-sitoutuminen
                          {:tyyppi :lupaus})
       #(e! (tiedot/->VaihdaLuvattujenPisteidenMuokkausTila))
       urakka])]])

(defn- ennuste [e! app]
  [:div.lupausten-ennuste
   [:div "Ennusteen mukaan urakalle on tulossa sanktiota... (ominaisuus tekemättä)"]])

(defn lupaukset-alempi-valilehti*
  [e! app]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
     (komp/sisaan-ulos
       #(do
          (e! (tiedot/->AlustaNakyma urakka))
          (e! (tiedot/->HaeUrakanLupaustiedot urakka)))

       #(e! (tiedot/->NakymastaPoistuttiin)))
     (fn [e! app]
       [:span.lupaukset-sivu
        [:div.otsikko-ja-hoitokausi
         [:h1 "Lupaukset"]
         [valinnat/urakan-hoitokausi-tuck (:valittu-hoitokausi app)
          (:urakan-hoitokaudet app) #(e! (tiedot/->HoitokausiVaihdettu urakka %))]]
        [yhteenveto e! app urakka]
        [ennuste e! app]
        (for [ryhma (:lupausryhmat app)]
          ^{:key (str "lupaustyhma" (:jarjestys ryhma))}
          [lupausryhman-yhteenveto e! ryhma])
        [debug app {:otsikko "TUCK STATE"}]]))))

(defn- valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :lupaukset (#{:teiden-hoito} tyyppi)

    false))

(defn lupaukset-paatason-valilehti [ur]
  (fn [{:keys [tyyppi] :as ur}]
    ;; vain MHU-urakoissa halutaan Lupaukset, jolloin alatabit näkyviin. Muutoin suoraan Välitavoitteet sisältö
    (if (= tyyppi :teiden-hoito)
      [bs/tabs
       {:style :tabs :classes "tabs-taso2"
        ;; huom: avain yhä valitavoitteet, koska Rooli-excel ja oikeudet
        :active (nav/valittu-valilehti-atom :valitavoitteet)}

       "Lupaukset" :lupaukset
       (when (valilehti-mahdollinen? :lupaukset ur)
         [tuck/tuck tila/lupaukset lupaukset-alempi-valilehti*])

       "Välitavoitteet" :valitavoitteet-nakyma
       [valitavoitteet/valitavoitteet ur]]
      [valitavoitteet/valitavoitteet ur])))