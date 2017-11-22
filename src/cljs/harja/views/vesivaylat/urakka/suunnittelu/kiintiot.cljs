(ns harja.views.vesivaylat.urakka.suunnittelu.kiintiot
  (:require [reagent.core :as r]
            [cljs.core.async :refer [chan <!]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.ui.debug :refer [debug]]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]

            [harja.tiedot.urakka :as u]
            [harja.views.urakka.valinnat :as suodattimet]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.tyokalut.tuck :as tuck-apurit]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def sivu "Vesiväylät/Kiintiöt")

(defn kiintion-toimenpiteet [e! app kiintio]
  (komp/luo
    (komp/kirjaa-kaytto! sivu "Kiintiön toimenpiteet")
    (fn [e! app kiintio]
      [grid/grid
      {:tyhja "Ei liitettyjä toimenpiteitä"
       :tunniste ::to/id}
      [{:otsikko "Työluokka" :nimi ::to/tyoluokka :fmt to/reimari-tyoluokka-fmt :leveys 10}
       {:otsikko "Toimenpide" :nimi ::to/toimenpide :fmt to/reimari-toimenpidetyyppi-fmt :leveys 10}
       {:otsikko "Hintatyyppi" :nimi ::to/hintatyyppi :fmt to/hintatyyppi-fmt :leveys 10}
       {:otsikko "Päivämäärä" :nimi ::to/pvm :fmt pvm/pvm-opt :leveys 10}
       {:otsikko "Turvalaite" :nimi ::to/turvalaite :leveys 10 :hae #(get-in % [::to/turvalaite ::tu/nimi])}
       {:otsikko "Valitse" :nimi :valinta :tyyppi :komponentti :tasaa :keskita
        :solu-klikattu (fn [rivi]
                         (let [valittu? (boolean ((:valitut-toimenpide-idt app) (::to/id rivi)))]
                           (e! (tiedot/->ValitseToimenpide {:id (::to/id rivi)
                                                            :valittu? (not valittu?)}))))
        :komponentti (fn [rivi]
                       (let [valittu? (boolean ((:valitut-toimenpide-idt app) (::to/id rivi)))]
                         [kentat/tee-kentta
                          {:tyyppi :checkbox}
                          (r/wrap valittu?
                                  (fn [uusi]
                                    (e! (tiedot/->ValitseToimenpide {:id (::to/id rivi)
                                                                     :valittu? uusi}))))]))
        :leveys 5}]
      (::kiintio/toimenpiteet kiintio)])))

(defn kiintiot* [e! app]
  (komp/luo
    (komp/kirjaa-kaytto! sivu)
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat
                                 {:urakka-id (:id @nav/valittu-urakka)
                                  :sopimus-id (first @u/valittu-sopimusnumero)}))
                           (e! (tiedot/->HaeKiintiot (:valinnat app))))
                      #(do (e! (tiedot/->Nakymassa? false))))
    (fn [e! {:keys [kiintiot
                    kiintioiden-haku-kaynnissa?
                    kiintioiden-tallennus-kaynnissa?] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.
      [:div
       [debug app]
       [valinnat/urakkavalinnat
        {}
        ^{:key "valinnat"}
        [suodattimet/urakan-sopimus @nav/valittu-urakka]
        ^{:key "urakkatoiminnot"}
        [valinnat/urakkatoiminnot {:sticky? true}
         ^{:key "irrotusnappi"}
         [napit/yleinen-ensisijainen (str "Irrota kiintiöstä"
                                          (when-not (empty? (:valitut-toimenpide-idt app))
                                            (str " (" (count (:valitut-toimenpide-idt app)) ")")))
          #(e! (tiedot/->IrrotaKiintiosta (:valitut-toimenpide-idt app)))
          {:disabled (or (empty? (:valitut-toimenpide-idt app))
                         (not (oikeudet/on-muu-oikeus? "irrota"
                                                       oikeudet/urakat-vesivaylasuunnittelu-kiintiot
                                                       (:id @nav/valittu-urakka))))}]]]
       [grid/grid
        {:otsikko (if (or (and (some? kiintiot) kiintioiden-haku-kaynnissa?)
                          kiintioiden-tallennus-kaynnissa?)
                    [ajax-loader-pieni "Päivitetään listaa"]
                    "Sopimuksessa määritellyt urakan kiintiöt")
         :esta-poistaminen? #(not-empty (::kiintio/toimenpiteet %))
         :esta-poistaminen-tooltip (fn [_] "Vain tyhjiä kiintiöitä voi poistaa")
         :piilota-toiminnot? (not
                               (oikeudet/voi-kirjoittaa? oikeudet/urakat-vesivaylasuunnittelu-kiintiot
                                                         (:id @nav/valittu-urakka)))
         :voi-lisata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-vesivaylasuunnittelu-kiintiot
                                                (:id @nav/valittu-urakka))
         :tallenna (when
                     (oikeudet/voi-kirjoittaa? oikeudet/urakat-vesivaylasuunnittelu-kiintiot
                                               (:id @nav/valittu-urakka))
                     (fn [sisalto]
                       (tuck-apurit/e-kanavalla! e! tiedot/->TallennaKiintiot sisalto)))
         :tyhja (if kiintioiden-haku-kaynnissa? [ajax-loader "Haetaan kiintiöitä"] "Ei määriteltyjä kiintiöitä")
         :tunniste ::kiintio/id
         :uusi-rivi (fn [rivi] rivi)
         :vetolaatikot (into {}
                             (map (juxt ::kiintio/id (fn [rivi] [kiintion-toimenpiteet e! app rivi])))
                             kiintiot)}
        [{:tyyppi :vetolaatikon-tila :leveys 1}
         {:otsikko "Nimi"
          :nimi ::kiintio/nimi
          :tyyppi :string
          :leveys 6
          :validoi [[:ei-tyhja "Anna nimi"]]}
         {:otsikko "Kuvaus"
          :nimi ::kiintio/kuvaus
          :tyyppi :text
          :leveys 12}
         {:otsikko "Toteutunut"
          :nimi :toteutunut
          :hae (comp count ::kiintio/toimenpiteet)
          :tyyppi :positiivinen-numero
          :muokattava? (constantly false)
          :leveys 3}
         {:otsikko "Koko"
          :nimi ::kiintio/koko
          :tyyppi :positiivinen-numero
          :kokonaisosan-maara 7
          :leveys 3
          :validoi [[:ei-tyhja "Anna koko"]]}]
        (sort-by (comp str/upper-case ::kiintio/nimi) kiintiot)]])))

(defn kiintiot []
  [tuck/tuck tiedot/tila kiintiot*])
