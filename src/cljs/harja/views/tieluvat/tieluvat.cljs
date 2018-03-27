(ns harja.views.tieluvat.tieluvat
  (:require
    [tuck.core :refer [tuck]]
    [harja.loki :refer [log]]
    [harja.tiedot.tieluvat.tieluvat :as tiedot]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
    [harja.ui.grid :as grid]
    [harja.views.kartta :as kartta]
    [harja.ui.debug :as debug]
    [harja.ui.valinnat :as valinnat]
    [harja.ui.kentat :as kentat]

    [harja.domain.tielupa :as tielupa]
    [harja.ui.napit :as napit]
    [clojure.string :as str]
    [harja.pvm :as pvm])
  (:require-macros
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn tielupalomake [e! app]
  (let [uusi? false]
    [:div
    [debug/debug app]
    [napit/takaisin "Takaisin lupataulukkoon" #(e! (tiedot/->ValitseTielupa nil))]
    [lomake/lomake
     {:otsikko (if uusi?
                 "Uusi tielupa"
                 "Tieluvan tiedot")
      :footer-fn (fn [lupa]
                   [napit/tallenna
                    "Tallenna tielupa"
                    #(e! (tiedot/->TallennaTielupa (lomake/ilman-lomaketietoja lupa)))
                    {:disabled (or true
                                   (not (tiedot/voi-tallentaa? lupa))
                                   (not (lomake/voi-tallentaa? lupa)))}])}
     [{:otsikko "Pvm"
       :tyyppi :pvm
       :nimi ::tielupa/voimassaolon-alkupvm}]]]))

(defn suodattimet [e! app]
  (let [atomi (partial tiedot/valinta-wrap e! app)]
    [valinnat/urakkavalinnat
     {}
     ^{:key "valinnat"}
     [valinnat/valintaryhmat-3
      [:div
       [kentat/tee-otsikollinen-kentta {:otsikko "Tierekisteriosoiteväli"
                                        :kentta-params {:tyyppi :tierekisteriosoite
                                                        :sijainti (atomi :sijainti)}
                                        :arvo-atom (atomi :tr)}]]
      [:div
       [kentat/tee-otsikollinen-kentta {:otsikko "Luvan numero"
                                        :kentta-params {:tyyppi :string}
                                        :arvo-atom (atomi :luvan-numero)}]
       [kentat/tee-otsikollinen-kentta {:otsikko "Lupatyyppi"
                                        :kentta-params {:tyyppi :valinta
                                                        :valinnat (into [nil] (sort tielupa/lupatyyppi-vaihtoehdot))
                                                        :valinta-nayta #(or (tielupa/tyyppi-fmt %) "- Ei käytössä -")}
                                        :arvo-atom (atomi :lupatyyppi)}]
       [kentat/tee-otsikollinen-kentta {:otsikko "Hakija"
                                        :kentta-params {:tyyppi :haku
                                                        :nayta ::tielupa/hakija-nimi
                                                        :hae-kun-yli-n-merkkia 2
                                                        :lahde tiedot/hakijahaku}
                                        :arvo-atom (atomi :hakija)}]]

      [:div
       [valinnat/aikavali (atomi :myonnetty) {:otsikko "Myönnetty välillä"}]
       [valinnat/aikavali (atomi :voimassaolo) {:otsikko "Voimassaolon aikaväli"}]]]]))

(defn tielupataulukko [e! {:keys [haetut-tieluvat tielupien-haku-kaynnissa?] :as app}]
  [:div
   [kartta/kartan-paikka]
   [debug/debug app]
   [suodattimet e! app]
   [grid/grid
    {:otsikko "Tienpidon luvat"
     :tunniste ::tielupa/id
     :sivuta grid/vakiosivutus
     :rivi-klikattu #(e! (tiedot/->ValitseTielupa %))
     :tyhja (if tielupien-haku-kaynnissa?
              [ajax-loader "Haku käynnissä"]
              "Ei liikennetapahtumia")}
    [{:otsikko "Myönnetty"
      :leveys 1
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/myontamispvm}
     {:otsikko "Voimassaolon alku"
      :leveys 1
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/voimassaolon-alkupvm}
     {:otsikko "Voimassaolon loppu"
      :leveys 1
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/voimassaolon-loppupvm}
     {:otsikko "Lupatyyppi"
      :leveys 1
      :tyyppi :string
      :nimi ::tielupa/tyyppi
      :fmt tielupa/tyyppi-fmt}
     {:otsikko "Hakija"
      :leveys 1
      :tyyppi :string
      :nimi ::tielupa/hakija-nimi}
     {:otsikko "Luvan numero"
      :leveys 1
      :tyyppi :positiivinen-numero
      :nimi ::tielupa/ulkoinen-tunniste}
     {:otsikko "Tie"
      :leveys 1
      :tyyppi :string
      :nimi :tie
      :hae (fn [rivi]
             (let [sijainnit (::tielupa/sijainnit rivi)]
               (str/join ", " (map ::tielupa/tie sijainnit))))}
     {:otsikko "Alku"
      :leveys 1
      :tyyppi :string
      :nimi :alkuosa
      :hae (fn [rivi]
             (let [sijainnit (::tielupa/sijainnit rivi)]
               (str/join ", " (map (comp
                                     (partial str/join "/")
                                     (juxt ::tielupa/aosa ::tielupa/aet))
                                   sijainnit))))}
     {:otsikko "Loppu"
      :leveys 1
      :tyyppi :string
      :nimi :loppuosa
      :hae (fn [rivi]
             (let [sijainnit (::tielupa/sijainnit rivi)]
               (str/join ", " (map (comp
                                     (partial str/join "/")
                                     (juxt ::tielupa/losa ::tielupa/let))
                                   sijainnit))))}]
    haetut-tieluvat]])

(defn tieluvat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeTieluvat)))
                      #(do (e! (tiedot/->Nakymassa? false))))
    (fn [e! app]
      (if-not (:valittu-tielupa app)
        [tielupataulukko e! app]
        [tielupalomake e! app]))))

(defc tieluvat []
  [tuck tiedot/tila tieluvat*])