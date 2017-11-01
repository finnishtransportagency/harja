(ns harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]

            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]

            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.valinnat :as valinnat])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn henkilon-nimi [henkilo]
  (str (::kayttaja/etunimi henkilo) " " (::kayttaja/sukunimi henkilo)))

(defn kokonaishintaiset* [e! app]
  (let [urakka (get-in app [:valinnat :urakka-id])]
    (komp/luo
      (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                      (log "--->>> uusi" uusi)
                                      (e! (tiedot/->PaivitaValinnat uusi))))
      (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                        #(e! (tiedot/->Nakymassa? false)))

      (fn [e! {:keys [toimenpiteet haku-kaynnissa?] :as app}]
        @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
        (let [toimenpiteet [{::kanavan-toimenpide/kohde {::kanavan-kohde/id 3,
                                                         ::kanavan-kohde/nimi "Tikkalansaaren avattava ratasilta",
                                                         ::kanavan-kohde/tyyppi :silta},
                             ::kanavan-toimenpide/kuittaaja {:harja.domain.kayttaja/kayttajanimi "jvh",
                                                             :harja.domain.kayttaja/etunimi "Mikko",
                                                             :harja.domain.kayttaja/sukunimi "Rönkkömäki",
                                                             :harja.domain.kayttaja/puhelin "041-5442083",
                                                             :harja.domain.kayttaja/sahkoposti "mikko.ronkkomaki@solita.fi",
                                                             :harja.domain.kayttaja/id 2},
                             ::kanavan-toimenpide/suorittaja {:harja.domain.kayttaja/etunimi "Mikko",
                                                              :harja.domain.kayttaja/id 2,
                                                              :harja.domain.kayttaja/kayttajanimi "jvh",
                                                              :harja.domain.kayttaja/sahkoposti "mikko.ronkkomaki@solita.fi",
                                                              :harja.domain.kayttaja/sukunimi "Rönkkömäki",
                                                              :harja.domain.kayttaja/puhelin "041-5442083"},
                             ::kanavan-toimenpide/lisatieto "Testitoimenpide",
                             ::kanavan-toimenpide/toimenpidekoodi {:harja.domain.toimenpidekoodi/id 2997,
                                                                   :harja.domain.toimenpidekoodi/nimi "Ei yksilöity"},
                             ::kanavan-toimenpide/tyyppi :kokonaishintainen,
                             ::kanavan-toimenpide/huoltokohde {::kanavan-huoltokohde/nimi "ASENNONMITTAUSLAITTEET",
                                                               ::kanavan-huoltokohde/id 5},
                             ::kanavan-toimenpide/pvm (pvm/nyt)
                             ::kanavan-toimenpide/id 1}]]
          [:div
           [valinnat/urakkavalinnat {:urakka urakka}
            ^{:key "valinnat"}
            [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka]]
           [harja.ui.napit/uusi
            "Uusi toimenpide"
            (fn [_] ;;todo
              )]
           [grid/grid
            {:otsikko "Urakan toimenpiteet"
             :voi-lisata? false
             :voi-muokata? false
             :voi-poistaa? false
             :voi-kumota? false
             :piilota-toiminnot? true
             :tyhja "Ei kokonaishitaisia toimenpiteita"
             :jarjesta ::kanavan-toimenpide/pvm
             :tunniste ::kanavan-toimenpide/id}
            [{:otsikko "Päivämäärä"
              :nimi ::kanavan-toimenpide/pvm
              :tyyppi :pvm
              :fmt pvm/pvm-opt}
             {:otsikko "Kohde"
              :nimi :kohde
              :tyyppi :string
              :hae #(get-in % [::kanavan-toimenpide/kohde ::kanavan-kohde/nimi])}
             {:otsikko "Huoltokohde"
              :nimi :huoltokohde
              :tyyppi :string
              :hae #(get-in % [::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/nimi])}
             {:otsikko "Toimenpide"
              :nimi :huoltokohde
              :tyyppi :string
              :hae #(get-in % [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi])}
             {:otsikko "Suorittaja"
              :nimi :huoltokohde
              :tyyppi :string
              :hae #(henkilon-nimi (::kanavan-toimenpide/suorittaja %))}
             {:otsikko "Kuittaaja"
              :nimi :huoltokohde
              :tyyppi :string
              :hae #(henkilon-nimi (::kanavan-toimenpide/suorittaja %))}]
            toimenpiteet]])))))

(defc kokonaishintaiset []
      [tuck tiedot/tila kokonaishintaiset*])


