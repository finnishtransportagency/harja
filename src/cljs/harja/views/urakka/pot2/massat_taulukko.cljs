(ns harja.views.urakka.pot2.massat-taulukko
  "POT2 materiaalikirjaston massataulukko"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.validoinnit :as pot2-validoinnit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
            [harja.validointi :as v]
            [harja.views.urakka.pot2.massa-ja-murske-yhteiset :as mm-yhteiset]
            [harja.ui.komponentti :as komp]
            [harja.fmt :as fmt])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn- massan-runkoaineet
  [rivi ainetyypit]
  [:div
   (str/join "; " (map (fn [aine]
                         (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:runkoaine/tyyppi aine))
                              (when (:runkoaine/massaprosentti aine)
                                (str " (" (fmt/piste->pilkku (:runkoaine/massaprosentti aine)) "%)"))))
                       (reverse
                         (sort-by :runkoaine/massaprosentti
                                  (:harja.domain.pot2/runkoaineet rivi)))))])

(defn- massan-side-tai-lisa-aineet [rivi ainetyypit tyyppi]
  (let [aineet-key (if (= tyyppi :lisaaineet)
                     :harja.domain.pot2/lisaaineet
                     :harja.domain.pot2/sideaineet)]

    (str/join "; "
              (map (fn [aine]
                     (let [aine (clojure.set/rename-keys aine {:sideaine/tyyppi :tyyppi
                                                               :lisaaine/tyyppi :tyyppi
                                                               :sideaine/pitoisuus :pitoisuus
                                                               :lisaaine/pitoisuus :pitoisuus
                                                               :sideaine/id :id
                                                               :lisaaine/id :id})

                           {:keys [id tyyppi pitoisuus]}  aine]
                       (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit tyyppi)
                            (when pitoisuus
                              (str " (" (fmt/piste->pilkku pitoisuus) "%)")))))
                   (reverse
                     (sort-by (if (= tyyppi :lisaaineet)
                                :lisaaine/pitoisuus
                                :sideaine/pitoisuus)
                              (aineet-key rivi)))))))

(defn massat-taulukko [e! {:keys [massat materiaalikoodistot] :as app}]
  [grid/grid
   {:otsikko "Massat"
    :tunniste ::pot2-domain/massa-id
    :luokat ["massa-taulukko"]
    :tyhja (if (nil? massat)
             [ajax-loader "Haetaan massatyyppejä..."]
             "Urakalle ei ole vielä lisätty massoja")
    :rivi-klikattu #(e! (mk-tiedot/->MuokkaaMassaa % false))
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true
    :custom-toiminto {:teksti "Lisää massa"
                      :toiminto #(e! (mk-tiedot/->UusiMassa))
                      :opts {:ikoni (ikonit/livicon-plus)
                             :luokka "nappi-ensisijainen"}}}
   [{:otsikko "Nimi" :tyyppi :komponentti :leveys 6
     :komponentti (fn [rivi]
                    [mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit (:massatyypit materiaalikoodistot)
                                                              :materiaali rivi
                                                              :fmt :komponentti}])}
    {:otsikko "KM-lk." :nimi ::pot2-domain/kuulamyllyluokka :leveys 2}
    {:otsikko "RC%" :nimi ::pot2-domain/rc% :leveys 2
     :hae (fn [massa]
            (pot2-domain/massan-rc-pitoisuus massa))}
    {:otsikko "Runkoaineet" :nimi ::pot2-domain/runkoaineet :fmt #(or % "-") :tyyppi :komponentti :leveys 6
     :komponentti (fn [rivi]
                    [massan-runkoaineet rivi (:runkoainetyypit materiaalikoodistot)])}
    {:otsikko "Sideaineet" :nimi ::pot2-domain/sideaineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 5
     :komponentti (fn [rivi]
                    [massan-side-tai-lisa-aineet rivi (:sideainetyypit materiaalikoodistot) :sideaineet])}
    {:otsikko "Lisäaineet" :nimi ::pot2-domain/lisaaineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [massan-side-tai-lisa-aineet rivi (:lisaainetyypit materiaalikoodistot) :lisaaineet])}
    {:otsikko "" :nimi :toiminnot :tyyppi :komponentti :leveys 3
     :komponentti (fn [rivi]
                    [mm-yhteiset/materiaalirivin-toiminnot e! rivi])}]
   (sort-by (fn [massa]
              (pot2-domain/massan-rikastettu-nimi (:massatyypit materiaalikoodistot)
                                                  massa))
            massat)])