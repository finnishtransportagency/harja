(ns harja.views.urakka.turvallisuus.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat :as tiedot]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn turvallisuuspoikkeaman-tiedot
  []

  (let [muokattu (atom @tiedot/valittu-turvallisuuspoikkeama)
        lomakkeen-virheet (atom {})
        voi-tallentaa? (atom true)]

    (fn []
      [:div
       [:button.nappi-ensisijainen
        {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama nil)}
        "Palaa"]

       [lomake/lomake
        {:luokka   :horizontal
         :muokkaa! #(reset! muokattu %)
         :virheet  lomakkeen-virheet
         :footer   [napit/palvelinkutsu-nappi
                    "Tallenna turvallisuuspoikkeama"
                    #(tiedot/tallenna-turvallisuuspoikkeama @muokattu)
                    {:luokka       "nappi-ensisijainen"
                     :ikoni        (ikonit/envelope)
                     :kun-onnistuu #(do
                                     #_(tiedot/turvallisuuspoikkeaman-tallennus-onnistui % @muokattu)
                                     (reset! tiedot/valittu-turvallisuuspoikkeama nil))
                     :disabled     (not @voi-tallentaa?)}]}
        [{:otsikko "Kuvaus" :nimi :kuvaus :leveys 1 :tyyppi :string}
         {:otsikko  "Tierekisteriosoite" :nimi :tr
          :tyyppi   :tierekisteriosoite
          :sijainti (r/wrap (:sijainti muokattu)
                            #(swap! muokattu assoc :sijainti %))}]
        @muokattu]])))

(defn turvallisuuspoikkeamalistaus
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama tiedot/+uusi-turvallisuuspoikkeama+)}
    (ikonit/plus-sign) " Lisää turvallisuuspoikkeama"]
   [grid/grid
    {:otsikko       "Turvallisuuspoikkeamat"
     :tyhja         (if @tiedot/haetut-turvallisuuspoikkeamat "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
     :rivi-klikattu #(reset! tiedot/valittu-turvallisuuspoikkeama %)}
    [{:otsikko "Kuvaus" :nimi :kuvaus :tyyppi :string :leveys "100%"}]
    @tiedot/haetut-turvallisuuspoikkeamat
    ]])

(defn turvallisuuspoikkeamat []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/lippu tiedot/taso-turvallisuuspoikkeamat)

    (fn []
      (if @tiedot/valittu-turvallisuuspoikkeama
        [turvallisuuspoikkeaman-tiedot]
        [turvallisuuspoikkeamalistaus]))))