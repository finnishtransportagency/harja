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
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defn pohjavesialueiden-suola []
  (komp/luo
   (komp/sisaan
    (fn []
      (let [urakkaid @nav/valittu-urakka-id]
        (go
          (reset! tiedot/pohjavesialueen-toteuma nil)
          (reset! tiedot/urakan-rajoitusalueet (<! (tiedot/hae-urakan-rajoitusalueet urakkaid)))))))
   (fn []
     (let [rajoitusalueet @tiedot/urakan-rajoitusalueet
           urakka @nav/valittu-urakka]
       [:div
        [urakka-valinnat/aikavali-nykypvm-taakse urakka
         tiedot/valittu-aikavali
         {:aikavalin-rajoitus [12 :kuukausi]}]
        [grid/grid {:tunniste :tunnus
                    :mahdollista-rivin-valinta? true
                    :rivi-valinta-peruttu (fn [rivi]
                                            (reset! tiedot/pohjavesialueen-toteuma nil))
                    :rivi-klikattu
                    (fn [rivi]
                      (go
                        (reset! tiedot/pohjavesialueen-toteuma
                                (<! (tiedot/hae-pohjavesialueen-suolatoteuma (:tunnus rivi) @tiedot/valittu-aikavali)))))
                         
                    :tyhjä (if (nil? @tiedot/urakan-pohjavesialueet)
                             [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                             "Ei Rajoitusalueita")}
         [{:otsikko "Tie" :tunniste :tie :hae (comp :tie :tr-osoite) :leveys 1}
          {:otsikko "Osoiteväli" :tunniste :tie :hae :tr-osoite
           :fmt (fn [tr-osoite]
                  (tierekisteri/tierekisteriosoite-tekstina
                    {:tr-numero (:tie tr-osoite)
                     :tr-alkuosa (:aosa tr-osoite)
                     :tr-alkuetaisyys (:aet tr-osoite)
                     :tr-loppuosa (:losa tr-osoite)
                     :tr-loppuetaisyys (:let tr-osoite)}
                    {:teksti-tie? false}))
           :leveys 1}
          {:otsikko "Pohjavesialue (tunnus)" :tunniste :pohjavesialueet :hae :pohjavesialueet :leveys 1}
          {:otsikko "Pituus (m)" :tunniste :pituus :hae :pituus :leveys 1}
          {:otsikko "Pituus ajoradat (m)" :tunniste :pituus_ajoradat :hae :pituus_ajoradat :leveys 1}
          {:otsikko "Formiaatit (t/ajoratakm)" :tunniste :formiaatit_t_per_ajoratakm
           :hae :formiaatit_t_per_ajoratakm :leveys 1}
          {:otsikko "Talvisuola (t/ajoratakm)" :tunniste :formiaatit_t_per_ajoratakm
           :hae :formiaatit_t_per_ajoratakm :leveys 1}
          {:otsikko "Suolankäyttöraja (t/ajoratakm)" :tunniste :suolankayttoraja :hae :suolankayttoraja :leveys 1}
          {:otsikko "" :tunniste :pituus :hae :kaytettava-formaattia? :fmt #(when % "Käytettävä formaattia") :leveys 1}]
         rajoitusalueet]
        (let [toteuma @tiedot/pohjavesialueen-toteuma]
          (when toteuma
            [grid/grid
             {:otsikko "Pohjavesialueen suolatoteuma"
              :tunniste :maara_t_per_km
              :piilota-toiminnot? true
              :tyhja (if (empty? toteuma)
                       "Ei tietoja")
              }
             [{:otsikko "Pohjavesialueen pituus (km)"
               :nimi :pituus
               :fmt #(fmt/desimaaliluku-opt % 1)
               :leveys 10}
              {:otsikko "Määrä t/km"
               :nimi :maara_t_per_km
               :fmt #(fmt/desimaaliluku-opt % 1)
               :leveys 10}
              {:otsikko "Määrä yhteensä"
               :leveys 10
               :fmt #(fmt/desimaaliluku-opt % 1)
               :nimi :yhteensa}
              {:otsikko "Käyttöraja"
               :leveys 10
               :nimi :kayttoraja}]
             toteuma]))]))))
