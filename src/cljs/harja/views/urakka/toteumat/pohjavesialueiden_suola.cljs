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
            [harja.fmt :as fmt])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))


(defn vetolaatikko-taso-2 [rajoitusalueet]
  [:div {:style {:display "flex"}}
   [:div {:style {:width "3px"
                  :height "auto"
                  :margin-left "5px" ;; Manuaalinen säätö, jotta sininen viiva menee vatolaatikon avausnapin kanssa samaa linjaa
                  :border-left "3px solid blue"}}]
   [:div {:style {:margin-left "1rem"}}
    [grid/grid {:tunniste (fn [rivi]
                            (str "rajoitusalue_vetolaatikko_taso_2_rivi_" (hash rivi)))
                :tyhjä (if (nil? @tiedot/urakan-rajoitusalueet)
                         [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                         "Ei Rajoitusalueita")}
     [{:tyyppi :avattava-rivi :leveys "2%"}
      {:otsikko "Tie" :tunniste :tie :hae (comp :tie :tr-osoite) :leveys 1}
      {:otsikko "Osoiteväli" :tunniste :tie :hae :tr-osoite
       :fmt (fn [tr-osoite]
              (str
                (str (:aosa tr-osoite) " / " (:aet tr-osoite))
                " - "
                (str (:losa tr-osoite) " / " (:let tr-osoite))))
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
     rajoitusalueet]]])

(defn vetolaatikko-taso-1 [rajoitusalueet]
  [grid/grid {:tunniste :id
              :reunaviiva? true
              :vetolaatikot (into {}
                              (map (juxt :id (fn [rivi] [vetolaatikko-taso-2 rajoitusalueet])))
                              rajoitusalueet)
              :tyhjä (if (nil? @tiedot/urakan-rajoitusalueet)
                       [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                       "Ei Rajoitusalueita")}
   [{:tyyppi :vetolaatikon-tila :leveys 0.5}
    {:otsikko "Tie" :tunniste :tie :hae (comp :tie :tr-osoite) :leveys 0.5}
    {:otsikko "Osoiteväli" :tunniste :tie :hae :tr-osoite
     :fmt (fn [tr-osoite]
            (str
              (str (:aosa tr-osoite) " / " (:aet tr-osoite))
              " - "
              (str (:losa tr-osoite) " / " (:let tr-osoite))))
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
   rajoitusalueet])




(defn taulukko
  "Rajoitusalueiden taulukko. Näyttää pääriveillä rajoitusalueiden tietojen yhteenvedot.
  Määrittelee tason 1 ja 2 vetolaatikot, joista pääsee sukeltamaan rajoitusalueen suolojen käytön yhteenvetoon ja sieltä
  vielä tiettyyn suolariviin liittyviin toteutumiin."
  [rajoitusalueet]
  [grid/grid {:tunniste :id
              :ensimmainen-sarake-sticky? true
              #_#_:esta-tiivis-grid? true
              #_#_:samalle-sheetille? true
              :vetolaatikot (into {}
                              (map (juxt :id (fn [rivi] [vetolaatikko-taso-1 rajoitusalueet])))
                              rajoitusalueet)
              :tyhjä (if (nil? @tiedot/urakan-rajoitusalueet)
                       [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                       "Ei Rajoitusalueita")}
   [{:tyyppi :vetolaatikon-tila :leveys 0.5}
    {:otsikko "Tie" :tunniste :tie :hae (comp :tie :tr-osoite) :leveys 0.5}
    {:otsikko "Osoiteväli" :tunniste :tie :hae :tr-osoite
     :fmt (fn [tr-osoite]
            (str
              (str (:aosa tr-osoite) " / " (:aet tr-osoite))
              " - "
              (str (:losa tr-osoite) " / " (:let tr-osoite))))
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
   rajoitusalueet])

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
        [:div.pohjavesialueiden-suola
         ;; TODO: Yleiskäyttöinen komponentti. Speksissä halutaan erilainen komponentti, jossa on nappula, jolla
         ;;       käynnistetään tietojen hakeminen aikavälin valinnan jälkeen
         ;; Aikavälivalinta
         [urakka-valinnat/aikavali-nykypvm-taakse urakka
          tiedot/valittu-aikavali
          {:aikavalin-rajoitus [12 :kuukausi]}]

         ;; Rajoitiusalueiden taulukko
         [taulukko rajoitusalueet]]))))
