(ns harja.views.urakka.toteumat.pohjavesialueiden-suola
  "Suolankäytön toteumat hoidon alueurakoissa"
  (:require [reagent.core :refer [atom wrap]]
            [tuck.core :as tuck]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.toteumat.suola :as tiedot]
            [harja.tiedot.urakka.toteumat.pohjavesialueiden-suolatoteumat-tiedot :as suolatoteumat-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [cljs.core.async :refer [<! >!]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [clojure.string :as str])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))


(defn vetolaatikko-taso-2
  "Valitun toteumien summarivin toteumarivit."
  [e! app {:keys [materiaali_id pvm rivi-id koneellinen?] :as rivi} rajoitusalue-id]

  (komp/luo
    (komp/sisaan
      (fn []
        (e! (suolatoteumat-tiedot/->HaeRajoitusalueenPaivanToteumat
              {:rajoitusalue-id rajoitusalue-id
               :materiaali-id materiaali_id
               :pvm pvm
               :rivi-id rivi-id
               :koneellinen? koneellinen?}))))
    (fn [e! app rivi]
      (let [rajoitusalueet (:rajoitusalueet app)
            valittu-rajoitusalue (some #(when (= rajoitusalue-id (:rajoitusalue_id %)) %) rajoitusalueet)
            valittu-paivarivi (some #(when (= rivi-id (:rivi-id %)) %) (:suolasummat valittu-rajoitusalue))
            toteumat (:paivatoteumat valittu-paivarivi) #_ (get @tiedot/urakan-rajoitusalueiden-toteumat yhteenveto-id)]
        [grid/grid {:tunniste :id ; Toteuma id
                    :piilota-muokkaus? true
                    ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
                    ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
                    :esta-tiivis-grid? true
                    :reunaviiva? true
                    :tyhja (if (nil? toteumat)
                             [yleiset/ajax-loader "Toteumia haetaan..."]
                             "Ei toteumia")}
         [{:otsikko "Alkoi" :nimi :alkanut :tyyppi :pvm :fmt pvm/pvm-aika-klo-suluissa :leveys 1}
          {:otsikko "Päättyi" :nimi :paattynyt :tyyppi :pvm :fmt pvm/pvm-aika-klo-suluissa :leveys 1}
          {:otsikko "Käytetty määrä (t)" :nimi :maara :fmt fmt/pyorista-ehka-kolmeen :tasaa :oikea :leveys 1}
          {:otsikko "ID / lisätieto" :nimi :lisatieto :leveys 3}]
         toteumat]))))


(defn vetolaatikko-taso-1
  "Rajoitusalueen toteumien summatiedot / yhteenveto per päivämäärä ja käytetty materiaali."
  [e! app {:keys [rajoitusalue_id] :as rivi}]
  (komp/luo
    (komp/sisaan
      (fn []
        (e! (suolatoteumat-tiedot/->HaeRajoitusalueenSummatiedot
              {:rajoitusalue-id rajoitusalue_id
               :hoitokauden-alkuvuosi 2021}))))
    (fn [e! app rivi]
      (let [valittu-rajoitusalue (some #(when (= rajoitusalue_id (:rajoitusalue_id %)) %) (:rajoitusalueet app))
            toteumien-summatiedot (:suolasummat valittu-rajoitusalue)]
        [grid/grid {:tunniste :rivi-id
                    :piilota-muokkaus? true
                    ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
                    ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
                    :esta-tiivis-grid? true
                    :reunaviiva? true
                    :vetolaatikot (into {}
                                    (map (juxt :rivi-id
                                           (fn [rivi] [vetolaatikko-taso-2 e! app rivi rajoitusalue_id])))
                                    toteumien-summatiedot)
                    :tyhja (if (nil? toteumien-summatiedot)
                             [yleiset/ajax-loader "Toteumien yhteenvetoja haetaan..."]
                             "Ei toteumia")}
         [{:tyyppi :vetolaatikon-tila :leveys 0.5}
          {:otsikko "Päivämäärä" :hae :pvm :tyyppi :pvm :fmt pvm/pvm-opt :leveys 1}
          {:otsikko "Materiaali" :nimi :materiaali-nimi :leveys 2}
          {:otsikko "Käytetty määrä (t)" :nimi :maara :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–")
           :tasaa :oikea :leveys 1}
          {:otsikko "Toteumia" :nimi :lukumaara :tasaa :oikea :leveys 1}
          {:otsikko "Lisätieto" :nimi :koneellinen? :fmt #(when % "Koneellisesti raportoitu") :leveys 2}]
         toteumien-summatiedot]))))

(defn taulukko-rajoitusalueet
  "Rajoitusalueiden taulukko. Näyttää pääriveillä rajoitusalueiden tietojen yhteenvedot.
  Määrittelee tason 1 ja 2 vetolaatikot, joista pääsee sukeltamaan rajoitusalueen suolojen käytön yhteenvetoon ja sieltä
  vielä tiettyyn suolariviin liittyviin toteutumiin."
  [e! {:keys [rajoitusalueet] :as app}]
  (let [;; Siivotaan pois ne rajoitusalueet, joilla ei ole toteumia
        vetolaatikon-rajoitusalueet (keep #(when (or (:suolatoteumat %) (:formiaattitoteumat %))
                                             %) rajoitusalueet)
        vetolaatikot (into {}
                       (map (juxt :rajoitusalue_id (fn [rivi] [vetolaatikko-taso-1 e! app rivi])))
                       vetolaatikon-rajoitusalueet)]
    [grid/grid {:tunniste :rajoitusalue_id
                :piilota-muokkaus? true
                ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
                ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
                :esta-tiivis-grid? true
                :vetolaatikot vetolaatikot
                :tyhja (if (or (nil? rajoitusalueet))
                         [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                         "Ei Rajoitusalueita")}
     [{:tyyppi :vetolaatikon-tila :leveys 0.5}
      {:otsikko "Tie" :nimi :tie :tasaa :oikea :leveys 0.6}
      {:otsikko "Osoiteväli" :nimi :osoitevali :leveys 1.5}
      {:otsikko "Pohjavesialue (tunnus)" :nimi :pohjavesialueet
       :luokka "sarake-pohjavesialueet"
       :tyyppi :komponentti
       :komponentti (fn [{:keys [pohjavesialueet]}]
                      (if (seq pohjavesialueet)
                        (into [:div]
                          (mapv (fn [alue]
                                  [:div (str (:nimi alue) " (" (:tunnus alue) ")")])
                            pohjavesialueet))
                        "-"))
       :leveys 1.7}
      {:otsikko "Pituus (m)" :nimi :pituus :tasaa :oikea :leveys 0.7}
      {:otsikko "Pituus ajoradat (m)" :nimi :ajoratojen_pituus :fmt fmt/pyorista-ehka-kolmeen
       :tasaa :oikea :leveys 1}
      {:otsikko "Formiaatit yhteensä (t)" :nimi :formiaattitoteumat
       :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–") :tasaa :oikea :leveys 1}
      {:otsikko "Formiaatit (t/ajoratakm)" :nimi :formiaatit_t_per_ajoratakm
       :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–") :tasaa :oikea :leveys 1}
      {:otsikko "Talvisuola yhteensä (t)" :nimi :suolatoteumat
       :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–") :tasaa :oikea :leveys 1}
      {:otsikko "Talvisuola (t/ajoratakm)" :nimi :talvisuola_t_per_ajoratakm
       :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–")
       :solun-luokka (fn [arvo rivi]
                       (when (> arvo (:suolarajoitus rivi))
                         "rajoitus-ylitetty"))
       :tasaa :oikea :leveys 1}
      {:otsikko "Suolankäyttöraja (t/ajoratakm)" :nimi :suolarajoitus :tasaa :oikea :leveys 1.1
       :fmt #(if % (fmt/desimaaliluku % 1) "–")}
      {:otsikko "" :nimi :formiaatti :fmt #(when % "Käytettävä formiaattia") :leveys 0.9}]
     rajoitusalueet]))

(defn pohjavesialueiden-suola* [e! app]
  (komp/luo
    (komp/sisaan
      (fn []
        (e! (suolatoteumat-tiedot/->HaeRajoitusalueet (or (:valittu-hoitovuosi app)
                                                        (pvm/vuosi (first @tiedot-urakka/valittu-hoitokausi)))))))
    (fn [e! app]
      [:div.pohjavesialueiden-suola
       [:h2 "Pohjavesialueiden suolatoteumat"]

       ;; Aikavälivalinta ja muut kontrollit
       [:div.taulukon-kontrollit
        [urakka-valinnat/aikavali-nykypvm-taakse @nav/valittu-urakka
         suolatoteumat-tiedot/valittu-aikavali
         {:aikavalin-rajoitus [12 :kuukausi]
          :aikavali-valinnat [;; Kuluvan kkn aikaväli alkaen kuluvan kkn ensimmäisestä päivästä kkn viimeiseen päivään
                              ["Kuluva kuukausi" #(pvm/kuukauden-aikavali (pvm/nyt))]
                              ;; Edellisen kkn aikaväli alkaen edellisen kkn ensimmäiestä päivästä edellisen kkn viimeiseen päivään
                              ;; TODO: VAI halutaanko edelliset 4 * 7 päivää?
                              ["Edellinen kuukausi" #(pvm/ed-kk-aikavalina (pvm/nyt))]
                              ;; TODO: Halutaanko tähän kokonainen edellinen viikko alkaen edellisen viikon ensimmäisestä päivästä
                              ;;       VAI edelliset 7 päivää?
                              ["Edellinen viikko" #(pvm/aikavali-nyt-miinus 7)]
                              ["Valittu aikaväli" nil]]}]

        ;; TODO: Toteuta tuck-eventin kutsu
        [napit/yleinen-ensisijainen "Hae toteumat" #(e! (suolatoteumat-tiedot/->HaeRajoitusalueet (pvm/vuosi (first @tiedot-urakka/valittu-hoitokausi))))
         {:luokka "nappi-hae-toteumat"}]]

       ;; TODO: Kartta
       #_[kartta]
       #_ [debug/debug app]
       ;; Rajoitusalueiden taulukko
       [taulukko-rajoitusalueet e! app]])))

(defn pohjavesialueiden-suola []
  (tuck/tuck tila/toteuma-pohjavesialueiden-suola pohjavesialueiden-suola*))
