(ns harja.views.urakka.kohdeluettelo.toteumat
  "Urakan kohdeluettelon toteumat"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.domain.paallystys.pot :as pot]

            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [clojure.string :as str]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :refer [lomake]]
            [harja.tiedot.urakka.paallystys :as paallystys])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn tila-keyword->string [tila]
  (case tila
    :aloitettu "Aloitettu"
    :valmis "Valmis"
    :lukittu "Hyväksytty"
    :palautettu "Palautettu"
    "-"))

(def lomakedata (atom nil))

(def urakkasopimuksen-mukainen-kokonaishinta (reaction (:tarjoushinta @lomakedata)))
(def muutokset-kokonaishintaan                              ; Lasketaan jokaisesta työstä muutos tilattuun hintaan (POT-Excelistä "Muutos hintaan") ja summataan yhteen.
  (reaction (reduce + (mapv
                        (fn [tyo]
                          (* (- (:toteutunut-maara tyo) (:tilattu-maara tyo)) (:yksikkohinta tyo)))
                        (:tyot @lomakedata)))))

(def yhteensa (reaction (+ @urakkasopimuksen-mukainen-kokonaishinta @muutokset-kokonaishintaan)))

(defn kohteen-tiedot []
  (let [kohteen-tiedot (r/wrap {:aloituspvm     (:aloituspvm @lomakedata)
                                :valmistumispvm (:valmistumispvm @lomakedata)
                                :takuupvm       (:takuupvm @lomakedata)
                                :hinta          (fmt/euro-opt (+ @urakkasopimuksen-mukainen-kokonaishinta @muutokset-kokonaishintaan))}
                               (fn [uusi-arvo]
                                 (reset! lomakedata (-> (assoc @lomakedata :aloituspvm (:aloituspvm uusi-arvo))
                                                        (assoc :valmistumispvm (:valmistumispvm uusi-arvo))
                                                        (assoc :takuupvm (:takuupvm uusi-arvo))
                                                        (assoc :hinta (:hinta uusi-arvo))))))]
    [lomake {:luokka   :horizontal                          ; FIXME Luokka inline ei toimi kovin hyvin koska bootstrap
             :muokkaa! (fn [uusi]
                         (log "PÄÄ Muokataan kohteen tietoja: " (pr-str uusi))
                         (reset! kohteen-tiedot uusi))}
     [{:otsikko "Kohde" :nimi :kohde :hae (fn [_] (:kohde @lomakedata) " " (:kohdenimi @lomakedata)) :muokattava? (constantly false)}
      {:otsikko "Aloitettu" :nimi :aloituspvm :tyyppi :pvm}
      {:otsikko "Valmistunut" :nimi :valmistumispvm :tyyppi :pvm}
      {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm}
      {:otsikko "Toteutunut hinta" :nimi :hinta :tyyppi :numero :leveys-col 2 :muokattava? (constantly false)}]
     @kohteen-tiedot]))

(tarkkaile! "PÄÄ Lomakedata: " lomakedata)


(defn yhteenveto []
  (let []
    [:div.pot-yhteenveto
     [:table
      [:tr
       [:td.pot-yhteenveto-nimi [:span "Urakkasopimuksen mukainen kokonaishinta: "]]
       [:td.pot-yhteenveto-summa [:span (fmt/euro-opt (or @urakkasopimuksen-mukainen-kokonaishinta 0))]]]
      [:tr
       [:td.pot-yhteenveto-nimi [:span "Muutokset kokonaishintaan ilman kustannustasomuutoksia: "]]
       [:td.pot-yhteenveto-summa [:span (fmt/euro-opt (or @muutokset-kokonaishintaan 0))]]]
      [:tr
       [:td.pot-yhteenveto-nimi [:span "Yhteensä: "]]
       [:td.pot-yhteenveto-summa [:span (fmt/euro-opt @yhteensa)]]]]]))

(defn toiminnot [valmis-tallennettavaksi? valmis-kasiteltavaksi?]
  (let [huomautusteksti (reaction (let [valmispvm (:valmistumispvm @lomakedata)]
                                    (if (not valmispvm)
                                      "Valmistusmispäivämäärää ei annettu, ilmoitus tallennetaan keskeneräisenä.")))
        urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero]
    [:div.pot-toiminnot
     [:div.pot-huomaus @huomautusteksti]

     (istunto/jos-rooli-urakassa istunto/rooli-urakoitsijan-kayttaja (:id @nav/valittu-urakka)
                                 [harja.ui.napit/palvelinkutsu-nappi
                                  "Tallenna"
                                  #(let [paallystyskohde-id (:paallystyskohde-id @lomakedata)
                                         aloituspvm (:aloituspvm @lomakedata)
                                         valmispvm (:valmistumispvm @lomakedata)
                                         takuupvm (:takuupvm @lomakedata)
                                         lahetettava-data (-> (dissoc @lomakedata :paallystyskohde-id)
                                                              (dissoc @lomakedata :valmistumispvm)
                                                              (dissoc @lomakedata :aloituspvm)
                                                              (dissoc @lomakedata :takuupvmpvm))]
                                    (log "PÄÄ Lähetetään lomake. Valmistumispvm: " valmispvm ", ilmoitustiedot: " (pr-str lahetettava-data))
                                    (paallystys/tallenna-paallystysilmoitus urakka-id sopimus-id paallystyskohde-id lahetettava-data aloituspvm valmispvm takuupvm))
                                  {:luokka       "nappi-ensisijainen"
                                   :disabled     (false? @valmis-tallennettavaksi?)
                                   :kun-onnistuu (fn [vastaus]
                                                   (log "PÄÄ Lomake tallennettu, vastaus: " (pr-str vastaus))
                                                   (reset! paallystys/paallystystoteumat vastaus)
                                                   (reset! lomakedata nil))}])

     (istunto/jos-rooli-urakassa istunto/rooli-tilaajan-kayttaja (:id @nav/valittu-urakka)
                                 [harja.ui.napit/palvelinkutsu-nappi
                                  "Hyväksy"
                                  #(let [paallystyskohde-id (:paallystyskohde-id @lomakedata)]
                                    (paallystys/hyvaksy-paallystysilmoitus urakka-id sopimus-id paallystyskohde-id))
                                  {:luokka       "nappi-ensisijainen"
                                   :disabled     (false? @valmis-kasiteltavaksi?)
                                   :kun-onnistuu (fn [vastaus]
                                                   (log "PÄÄ Lomake hyväksytty, vastaus: " (pr-str vastaus))
                                                   (reset! paallystys/paallystystoteumat vastaus)
                                                   (reset! lomakedata nil))}])

     (istunto/jos-rooli-urakassa istunto/rooli-tilaajan-kayttaja (:id @nav/valittu-urakka)
                                 [harja.ui.napit/palvelinkutsu-nappi
                                  "Palauta urakoitsijalle"
                                  #(let [paallystyskohde-id (:paallystyskohde-id @lomakedata)]
                                    (paallystys/hylkaa-paallystysilmoitus urakka-id sopimus-id paallystyskohde-id))
                                  {:luokka       "nappi-ensisijainen"
                                   :disabled     (false? @valmis-kasiteltavaksi?)
                                   :kun-onnistuu (fn [vastaus]
                                                   (log "PÄÄ Lomake hylätty, vastaus: " (pr-str vastaus))
                                                   (reset! paallystys/paallystystoteumat vastaus)
                                                   (reset! lomakedata nil))}])]))

(defn paallystysilmoituslomake
  []
  (let [toteutuneet-osoitteet (r/wrap (zipmap (iterate inc 1) (:osoitteet @lomakedata))
                                      (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :osoitteet (filter
                                                                                                         #(not (and (true? (:poistettu %))
                                                                                                                    (neg? (:id %)))) (vals uusi-arvo))))))
        paallystystoimenpide (r/wrap (zipmap (iterate inc 1) (:toimenpiteet @lomakedata))
                                     (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :toimenpiteet (filter
                                                                                                           #(not (and (true? (:poistettu %))
                                                                                                                      (neg? (:id %)))) (vals uusi-arvo))))))
        kiviaines (r/wrap (zipmap (iterate inc 1) (:kiviaines @lomakedata))
                          (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :kiviaines (filter
                                                                                             #(not (and (true? (:poistettu %))
                                                                                                        (neg? (:id %)))) (vals uusi-arvo))))))
        alustalle-tehdyt-toimet (r/wrap (zipmap (iterate inc 1) (:alustatoimet @lomakedata))
                                        (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :alustatoimet (filter
                                                                                                              #(not (and (true? (:poistettu %))
                                                                                                                         (neg? (:id %)))) (vals uusi-arvo))))))
        toteutuneet-maarat (r/wrap (zipmap (iterate inc 1) (:tyot @lomakedata))
                                   (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :tyot (filter
                                                                                                 #(not (and (true? (:poistettu %))
                                                                                                            (neg? (:id %))))
                                                                                                 (vals uusi-arvo))))))

        alikohteet-virheet (atom {})
        paallystystoimenpide-virheet (atom {})
        alustalle-tehdyt-toimet-virheet (atom {})
        toteutuneet-maarat-virheet (atom {})
        kiviaines-virheet (atom {})

        valmis-tallennettavaksi? (reaction
                                   (let [alikohteet-virheet @alikohteet-virheet
                                         paallystystoimenpide-virheet @paallystystoimenpide-virheet
                                         alustalle-tehdyt-toimet-virheet @alustalle-tehdyt-toimet-virheet
                                         toteutuneet-maarat-virheet @toteutuneet-maarat-virheet
                                         kiviaines-virheet @kiviaines-virheet
                                         tila (:tila @lomakedata)]
                                     (and
                                       (not (= tila :lukittu))
                                       (not (= tila :valmis))
                                       (empty? alikohteet-virheet)
                                       (empty? paallystystoimenpide-virheet)
                                       (empty? alustalle-tehdyt-toimet-virheet)
                                       (empty? toteutuneet-maarat-virheet)
                                       (empty? kiviaines-virheet))))
        valmis-kasiteltavaksi? (reaction (let [valmispvm (:valmistumispvm @lomakedata)
                                               toteutuneet-osoitteet (:osoitteet @lomakedata)
                                               toteutuneet-maarat (:tyot @lomakedata)
                                               tila (:tila @lomakedata)]
                                           (and (not (= tila :palautettu))
                                                (not (= tila :lukittu))
                                                (not (nil? valmispvm))
                                                (not (empty? toteutuneet-osoitteet))
                                                (not (empty? toteutuneet-maarat)))))]
    (komp/luo
      (fn [ur]
        [:div.paallystysilmoituslomake

         [:button.nappi-toissijainen {:on-click #(reset! lomakedata nil)}
          (ikonit/chevron-left) " Takaisin toteumaluetteloon"]

         (kohteen-tiedot)

         [grid/muokkaus-grid
          {:otsikko      "Toteutuneet alikohteet"
           :tunniste     :tie
           :rivinumerot? true
           :muutos       (fn [g] ; FIXME Kopioi 1. rivin tienro muille riveille, miksei toimi?
                           (let [grid-data (into [] (vals (grid/hae-muokkaustila g)))]
                             (reset! toteutuneet-osoitteet (mapv (fn [rivi] (assoc rivi :tie (:tie (first grid-data)))) grid-data))
                             (reset! alikohteet-virheet (grid/hae-virheet g))))}
          [{:otsikko     "Tie#" :nimi :tie :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]
                                                                                   [:samat-tienumerot "Kaikkien tienumeroiden täytyy olla samat."]]
            :muokattava? (fn [rivi index] (if (> index 0) false true))}
           {:otsikko       "Ajorata"
            :nimi          :ajorata
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse ajorata -")
            :valinnat      pot/+ajoradat+
            :leveys        "20%"
            :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko       "Suunta"
            :nimi          :suunta
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse suunta -")
            :valinnat      pot/+suunnat+
            :leveys        "20%"
            :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko       "Kaista"
            :nimi          :kaista
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse kaista -")
            :valinnat      pot/+kaistat+
            :leveys        "20%"
            :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Alkutieosa" :nimi :aosa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Alkuetäisyys" :nimi :aet :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Lopputieosa" :nimi :losa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Loppuetäisyys" :nimi :let :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (- (:let rivi) (:losa rivi)))}] ; FIXME Onko oikein laskettu?
          toteutuneet-osoitteet]

         [grid/muokkaus-grid
          {:otsikko      "Päällystystoimenpiteen tiedot"
           :voi-lisata?  false
           :voi-poistaa? (constantly false)
           :rivinumerot? true
           :muutos       #(reset! paallystystoimenpide-virheet (grid/hae-virheet %))}
          [{:otsikko       "Päällyste"
            :nimi          :paallystetyyppi
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse päällyste -")
            :valinnat      pot/+paallystetyypit+
            :leveys        "30%"
            :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Raekoko" :nimi :raekoko :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Massa (kg/m2)" :nimi :massa :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "RC-%" :nimi :rc% :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko       "Pääl. työmenetelmä"
            :nimi          :tyomenetelma
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse menetelmä -")
            :valinnat      pot/+tyomenetelmat+
            :leveys        "30%"
            :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Leveys (m)" :nimi :leveys :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Massa (kg/m2)" :nimi :massamaara :leveys "15%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Pinta-ala (m2)" :nimi :pinta-ala :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko       "Edellinen päällyste"
            :nimi          :edellinen-paallystetyyppi
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse päällyste -")
            :valinnat      pot/+paallystetyypit+
            :leveys        "30%"}]
          paallystystoimenpide]

         [grid/muokkaus-grid
          {:otsikko "Kiviaines ja sideaine"
           :muutos  #(reset! kiviaines-virheet (grid/hae-virheet %))}
          [{:otsikko "Kiviaines-esiintymä" :nimi :esiintyma :tyyppi :string :pituus-max 256 :leveys "30%"}
           {:otsikko "KM-arvo" :nimi :km-arvo :tyyppi :string :pituus-max 256 :leveys "20%"}
           {:otsikko "Muotoarvo" :nimi :muotoarvo :tyyppi :string :pituus-max 256 :leveys "20%"}
           {:otsikko "Sideaine-tyyppi" :nimi :sideainetyyppi :leveys "30%" :tyyppi :string :pituus-max 256}
           {:otsikko "Pitoisuus" :nimi :pitoisuus :leveys "20%" :tyyppi :numero}
           {:otsikko "Lisäaineet" :nimi :lisaaineet :leveys "20%" :tyyppi :string :pituus-max 256}]
          kiviaines]

         [grid/muokkaus-grid
          {:otsikko "Alustalle tehdyt toimet"
           :muutos  #(reset! alustalle-tehdyt-toimet-virheet (grid/hae-virheet %))}
          [{:otsikko "Alkutieosa" :nimi :aosa :tyyppi :string :leveys "10%" :pituus-max 256}
           {:otsikko "Alkuetäisyys" :nimi :aet :tyyppi :numero :leveys "10%"}
           {:otsikko "Lopputieosa" :nimi :losa :tyyppi :numero :leveys "10%"}
           {:otsikko "Loppuetäisyys" :nimi :let :leveys "10%" :tyyppi :numero}
           {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (- (:let rivi) (:losa rivi)))} ; FIXME Onko oikein laskettu?
           {:otsikko       "Käsittelymenetelmä"
            :nimi          :kasittelymenetelma
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse menetelmä -")
            :valinnat      pot/+alustamenetelmat+
            :leveys        "30%"}
           {:otsikko "Käsittelypaks. (cm)" :nimi :paksuus :leveys "10%" :tyyppi :numero}
           {:otsikko       "Verkkotyyppi"
            :nimi          :verkkotyyppi
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse verkkotyyppi -")
            :valinnat      pot/+verkkotyypit+
            :leveys        "30%"}
           {:otsikko       "Tekninen toimenpide"
            :nimi          :tekninen-toimenpide
            :tyyppi        :valinta
            :valinta-arvo  :koodi
            :valinta-nayta #(if % (:nimi %) "- Valitse toimenpide -")
            :valinnat      pot/+tekniset-toimenpiteet+
            :leveys        "30%"}]
          alustalle-tehdyt-toimet]

         [grid/muokkaus-grid
          {:otsikko "Toteutuneet määrät"
           :muutos  #(reset! toteutuneet-maarat-virheet (grid/hae-virheet %))}
          [{:otsikko       "Päällystetyön tyyppi"
            :nimi          :tyyppi
            :tyyppi        :valinta
            :valinta-arvo  :avain
            :valinta-nayta #(if % (:nimi %) "- Valitse työ -")
            :valinnat      pot/+paallystystyon-tyypit+
            :leveys        "30%"}
           {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "10%" :pituus-max 256}
           {:otsikko "Tilattu määrä" :nimi :tilattu-maara :tyyppi :numero :leveys "15%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Toteutunut määrä" :nimi :toteutunut-maara :leveys "15%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Ero" :nimi :ero :leveys "15%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (- (:toteutunut-maara rivi) (:tilattu-maara rivi)))}
           {:otsikko "Yks.hinta" :nimi :yksikkohinta :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
           {:otsikko "Muutos hintaan" :nimi :muutos-hintaan :leveys "15%" :muokattava? (constantly false) :tyyppi :numero :hae (fn [rivi] (* (- (:toteutunut-maara rivi) (:tilattu-maara rivi)) (:yksikkohinta rivi)))}]
          toteutuneet-maarat]

         (yhteenveto)
         (toiminnot valmis-tallennettavaksi? valmis-kasiteltavaksi?)]))))

(defn toteumaluettelo
  []
  (let []

    (komp/luo
      (fn []
        [:div
         [grid/grid
          {:otsikko  "Toteumat"
           :tyhja    (if (nil? @paallystys/paallystystoteumat) [ajax-loader "Haetaan toteumia..."] "Ei toteumia")
           :tunniste :kohdenumero}
          [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "50%"}
           {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys "20%" :hae (fn [rivi] (tila-keyword->string (:tila rivi)))}
           {:otsikko     "Päällystysilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly false) :leveys "25%" :tyyppi :komponentti
            :komponentti (fn [rivi] (if (:tila rivi) [:button.nappi-toissijainen.nappi-grid {:on-click #(go
                                                                                                         (let [urakka-id (:id @nav/valittu-urakka)
                                                                                                               [sopimus-id _] @u/valittu-sopimusnumero
                                                                                                               vastaus (<! (paallystys/hae-paallystysilmoitus-paallystyskohteella urakka-id sopimus-id (:paallystyskohde_id rivi)))
                                                                                                               ilmoitustiedot (:ilmoitustiedot vastaus)
                                                                                                               ; Lomakkeessa näytettävä data on ilmoitustiedot (JSON:n clojure-map) assocattuna muutamalla keywordilla (jotka saatiin itse taulusta)
                                                                                                               ; FIXME Miksi tämä vastaavan asian helpommin tekevä toteutus ei toimi (merge aiheuttaa oudon virheen):
                                                                                                               ;data-lomakkeelle (-> (assoc vastaus :paallystyskohde-id (:paallystyskohde_id rivi))
                                                                                                               ;                     (dissoc :ilmoitustiedot)
                                                                                                               ;                     (merge ilmoitustiedot))]
                                                                                                               data-lomakkeelle (-> (assoc ilmoitustiedot :paallystyskohde-id (:paallystyskohde_id rivi))
                                                                                                                                    (assoc :valmistumispvm (:valmistumispvm vastaus))
                                                                                                                                    (assoc :aloituspvm (:aloituspvm vastaus))
                                                                                                                                    (assoc :takuupvm (:takuupvm vastaus))
                                                                                                                                    (assoc :tila (:tila vastaus)))]
                                                                                                           (log "PÄÄ Vastaus: " (pr-str vastaus))
                                                                                                           (log "PÄÄ data lomakkeelle: " (pr-str data-lomakkeelle))
                                                                                                           (reset! lomakedata data-lomakkeelle)))}
                                                      [:span (ikonit/eye-open) " Päällystysilmoitus"]]
                                                     [:button.nappi-toissijainen.nappi-grid {:on-click #(reset! lomakedata {:kohde              (:kohdenumero rivi)
                                                                                                                            :kohdenimi          (:nimi rivi)
                                                                                                                            :paallystyskohde-id (:paallystyskohde_id rivi)
                                                                                                                            :tarjoushinta       (:sopimuksen_mukaiset_tyot rivi)})}
                                                      [:span " Tee päällystysilmoitus"]]))}]
          (sort
            (fn [toteuma] (case (:tila toteuma)
                            :lukittu 0
                            :valmis 1
                            :palautettu 2
                            :aloitettu 3
                            4))
            @paallystys/paallystystoteumat)]]))))

(defn toteumat []
  (if @lomakedata
    [paallystysilmoituslomake]
    [toteumaluettelo]))