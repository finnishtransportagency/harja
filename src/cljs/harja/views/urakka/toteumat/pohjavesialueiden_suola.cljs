(ns harja.views.urakka.toteumat.pohjavesialueiden-suola
  "Suolankäytön toteumat hoidon alueurakoissa"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.toteumat.suola :as tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
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
  [{yhteenveto-id :id
    toteuma-idt :toteuma-idt :as rivi}]

  (komp/luo
    (komp/sisaan
      (fn []
        (go
          (swap! tiedot/urakan-rajoitusalueiden-toteumat
            assoc yhteenveto-id (<! (tiedot/hae-rajoitusalueen-suolatoteumat toteuma-idt))))))
    (fn []
      (let [toteumat (get @tiedot/urakan-rajoitusalueiden-toteumat yhteenveto-id)]
        [grid/grid {:tunniste (fn [rivi]
                                (str "vetolaatikko_taso_2_rivi_" (hash rivi)))
                    :piilota-muokkaus? true
                    ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
                    ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
                    :esta-tiivis-grid? true
                    :reunaviiva? true
                    :tyhjä (if (nil? @tiedot/urakan-rajoitusalueiden-toteumat)
                             [yleiset/ajax-loader "Toteumia haetaan..."]
                             "Ei toteumia")}
         [{:tyyppi :vetolaatikon-tila :leveys 0.05}
          {:otsikko "Alkoi" :nimi :alkanut :tyyppi :pvm :fmt pvm/pvm-aika-klo-suluissa :leveys 1}
          {:otsikko "Päättyi" :nimi :paattynyt :tyyppi :pvm :fmt pvm/pvm-aika-klo-suluissa :leveys 1}
          {:otsikko "Käytetty määrä (t)" :nimi :maara-t :fmt fmt/pyorista-ehka-kolmeen :tasaa :oikea :leveys 1}
          {:otsikko "ID / lisätieto" :nimi :lisatieto :leveys 2}]
         toteumat]))))


(defn vetolaatikko-taso-1
  "Rajoitusalueen toteumien summatiedot / yhteenveto per päivämäärä ja käytetty materiaali."
  [{rajoitusalue-id :id :as rivi}]
  (komp/luo
    (komp/sisaan
      (fn []
        (go
          (swap! tiedot/urakan-rajoitusalueiden-summatiedot
            assoc rajoitusalue-id (<! (tiedot/hae-rajoitusalueen-toteumien-summatiedot rajoitusalue-id))))))
    (fn []
      (let [toteumien-summatiedot (get @tiedot/urakan-rajoitusalueiden-summatiedot rajoitusalue-id)]
        [grid/grid {:tunniste :id
                    :piilota-muokkaus? true
                    ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
                    ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
                    :esta-tiivis-grid? true
                    :reunaviiva? true
                    :vetolaatikot (into {}
                                    (map (juxt :id
                                           (fn [rivi] [vetolaatikko-taso-2 rivi])))
                                    toteumien-summatiedot)
                    :tyhjä (if (nil? @tiedot/urakan-rajoitusalueiden-summatiedot)
                             [yleiset/ajax-loader "Toteumien yhteenvetoja haetaan..."]
                             "Ei toteumia")}
         [{:tyyppi :vetolaatikon-tila :leveys 0.18}
          {:otsikko "Päivämäärä" :hae :pvm :tyyppi :pvm :fmt pvm/pvm-opt :leveys 0.8}
          {:otsikko "Materiaali" :nimi :materiaali-nimi :leveys 1}
          {:otsikko "Käytetty määrä (t)" :nimi :maara-t :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–")
           :tasaa :oikea :leveys 1}
          {:otsikko "Toteumia" :nimi :toteuma-lkm :tasaa :oikea :leveys 1}
          {:otsikko "Lisätieto" :nimi :koneellinen? :fmt #(when % "Koneellisesti raportoitu") :leveys 1}]
         toteumien-summatiedot]))))

(defn taulukko-rajoitusalueet
  "Rajoitusalueiden taulukko. Näyttää pääriveillä rajoitusalueiden tietojen yhteenvedot.
  Määrittelee tason 1 ja 2 vetolaatikot, joista pääsee sukeltamaan rajoitusalueen suolojen käytön yhteenvetoon ja sieltä
  vielä tiettyyn suolariviin liittyviin toteutumiin."
  [rajoitusalueet]
  [grid/grid {:tunniste :id
              :piilota-muokkaus? true
              ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
              ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
              :esta-tiivis-grid? true
              :vetolaatikot (into {}
                              (map (juxt :id (fn [rivi] [vetolaatikko-taso-1 rivi])))
                              rajoitusalueet)
              :tyhjä (if (nil? @tiedot/urakan-rajoitusalueet)
                       [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                       "Ei Rajoitusalueita")}
   [{:tyyppi :vetolaatikon-tila :leveys 0.3}
    {:otsikko "Tie" :tunniste :tie :hae (comp :tie :tr-osoite) :tasaa :oikea :leveys 0.5}
    {:otsikko "Osoiteväli" :tunniste :tie :hae :tr-osoite
     :fmt (fn [tr-osoite]
            (str
              (str (:aosa tr-osoite) " / " (:aet tr-osoite))
              " – "
              (str (:losa tr-osoite) " / " (:let tr-osoite))))
     :leveys 1}
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
     :leveys 1}
    {:otsikko "Pituus (m)" :nimi :pituus :fmt fmt/pyorista-ehka-kolmeen :tasaa :oikea :leveys 1}
    {:otsikko "Pituus ajoradat (m)" :nimi :pituus_ajoradat :fmt fmt/pyorista-ehka-kolmeen
     :tasaa :oikea :leveys 1}
    {:otsikko "Formiaatit (t/ajoratakm)" :nimi :formiaatit_t_per_ajoratakm
     :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–") :tasaa :oikea :leveys 1}
    {:otsikko "Talvisuola (t/ajoratakm)" :nimi :talvisuola_t_per_ajoratakm
     :fmt #(if % (fmt/pyorista-ehka-kolmeen %) "–")
     :solun-luokka (fn [arvo rivi]
                     (when (> arvo (:suolankayttoraja rivi))
                       "rajoitus-ylitetty"))
     :tasaa :oikea :leveys 1}
    {:otsikko "Suolankäyttöraja (t/ajoratakm)" :nimi :suolankayttoraja :tasaa :oikea
     :fmt #(if % (fmt/desimaaliluku % 1) "–") :leveys 1}
    {:otsikko "" :nimi :kaytettava-formaattia? :fmt #(when % "Käytettävä formaattia") :leveys 1}]
   rajoitusalueet])

(defn pohjavesialueiden-suola []
  (komp/luo
    (komp/sisaan
      (fn []
        (let [urakkaid @nav/valittu-urakka-id]
          (go
            (reset! tiedot/urakan-rajoitusalueet (<! (tiedot/hae-urakan-rajoitusalueet urakkaid)))))))
    (fn []
      (let [rajoitusalueet @tiedot/urakan-rajoitusalueet
            urakka @nav/valittu-urakka]
        [:div.pohjavesialueiden-suola
         [:h2 "Pohjavesialueiden suolatoteumat"]

         ;; Aikavälivalinta ja muut kontrollit
         [:div.taulukon-kontrollit
          ;; TODO: Tämä on yleiskäyttöinen komponentti. Speksissä halutaan erilainen komponentti, jossa on nappula, jolla
          ;;       käynnistetään tietojen hakeminen aikavälin valinnan jälkeen
          [urakka-valinnat/aikavali-nykypvm-taakse urakka
           tiedot/valittu-aikavali
           {:aikavalin-rajoitus [12 :kuukausi]}]]

         ;; TODO: Kartta
         #_[kartta]

         ;; Rajoitusalueiden taulukko
         [taulukko-rajoitusalueet rajoitusalueet]]))))
