(ns harja.views.urakka.yllapitokohteet.paikkaukset.reikapaikkaukset-apurit
  "Reikäpaikkausnäkymän apufunktiot"
  (:require [harja.ui.napit :as napit]
            [reagent.core :as r]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.modal :as modal]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn excel-virhe-modal
  "Näyttää käyttäjälle kaikki reikäpaikkausten Excel- tuonnin virheet"
  [e! nayta-virhe-modal excel-virheet]
  [modal/modal
   {:otsikko "Virheitä reikäpaikkausten tuonnissa Excelillä"
    :nakyvissa? nayta-virhe-modal
    :sulje-fn #(e! (tiedot/->SuljeVirheModal))
    :footer [:div
             [napit/sulje #(e! (tiedot/->SuljeVirheModal))]]}
   [:div
    [:<>
     [:p "Tuotua Exceliä ei voitu lukea. Varmista, että käytät HARJAsta ladattua pohjaa jonka sarakkeita A-K ei ole muokattu, ja paikkaukset alkavat riviltä 4."]
     [:<>
      [:br]
      [:<>
       [:p "Virheet:"]
       [:ul
        (for* [virhe excel-virheet]
          [:li virhe])]]]]
    [:<>
     [:br]
     [:p "Tarkista virheet ja yritä tuontia uudelleen."]]]])


(defn reikapaikkaus-suodattimet [e! valinnat aikavali]
  [:div.row.filtterit
   ;; TR valinta
   [:div
    [:div.alasvedon-otsikko-vayla "Tieosoite"]
    [kentat/tee-kentta {:tyyppi :tierekisteriosoite
                        :alaotsikot? true
                        :vayla-tyyli? true}
     (r/wrap
       (:tr valinnat)
       #(e! (tiedot/->PaivitaValinnat {:tr %})))]]

   ;; Pvm valinta
   [:div {:data-cy "reikapaikkaus-aikavali"}
    [aikavali
     (r/wrap
       (:aikavali valinnat)
       #(e! (tiedot/->PaivitaValinnat {:aikavali %})))
     {:otsikko "Päivämäärä"
      :for-teksti "filtteri-aikavali"
      :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta reikapaikkaus-pvm "}
      :ikoni-sisaan? true
      :vayla-tyyli? true
      :aikavalin-rajoitus [6 :kuukausi]}]]

   ;; Haku
   [:div.haku-nappi
    [napit/yleinen-ensisijainen "Hae" #(e! (tiedot/->HaeTiedot)) {:data-attributes {:data-cy "hae-reikapaikkauskohteita"}}]]])


(defn reikapaikkaus-muokkauspaneeli [e! voi-kirjoittaa? voi-tallentaa?
                                     valittu-rivi alasveto-kuvaukset alkuaika alasveto-valinnat]
  [:div.overlay-oikealla
   ;; Lomake
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
     ;; Header
     :header [:div.col-md-12
              [:h2.header-yhteiset {:data-cy "reikapaikkaus-muokkauspaneeli"} "Muokkaa toteumaa"]
              [:hr]]
     ;; Footer, joka on vakiona col-md-12
     :footer [:<>
              [:hr]
              [:div.muokkaus-modal-napit
               ;; Tallenna
               [napit/tallenna "Tallenna muutokset" #(e! (tiedot/->TallennaReikapaikkaus valittu-rivi)) {:disabled (not voi-tallentaa?)
                                                                                                         :data-attributes {:data-cy "tallena-reikapaikkaus"}}]
               ;; Poista 
               [napit/yleinen-toissijainen "Poista" #(e! (tiedot/->PoistaReikapaikkaus valittu-rivi)) {:ikoni (ikonit/livicon-trash)
                                                                                                       :data-attributes {:data-cy "poista-reikapaikkaus"}
                                                                                                       :paksu? true
                                                                                                       :luokka "lomake-poista"
                                                                                                       :disabled (not voi-kirjoittaa?)}]
               ;; Sulje 
               [napit/yleinen-toissijainen "Sulje" #(e! (tiedot/->SuljeMuokkaus)) {:data-attributes {:data-cy "sulje-muokkauspaneeli"}}]]]}

    [(lomake/rivi
       {:otsikko "Pvm"
        :pakollinen? true
        :tyyppi :komponentti
        :komponentti (fn []
                       [:span {:data-cy "reikapaikkaus-muokkaa-pvm"}
                        [kentat/tee-kentta {:tyyppi :pvm
                                            :ikoni-sisaan? true
                                            :vayla-tyyli? true}
                         (r/wrap
                           alkuaika
                           #(e! (tiedot/->AsetaToteumanPvm %)))]])})
     ;; Sijainti
     (lomake/ryhma
       {:otsikko "Sijainti"
        :ryhman-luokka "lomakeryhman-otsikko-tausta lomake-ryhma-otsikko"}
       ;; TR- valinnat
       (lomake/rivi
         {:nimi :tie
          :otsikko "Tie"
          :pakollinen? true
          :tyyppi :numero
          :input-luokka "lomake-tr-valinta"
          :rivi-luokka "lomakeryhman-rivi-tausta"
          :desimaalien-maara 0
          :validoi [[:ei-tyhja "Syötä tienumero"]]
          ::lomake/col-luokka "col-xs-2 tr-input"}
         {:nimi :aosa
          :otsikko "A-osa"
          :pakollinen? true
          :tyyppi :numero
          :input-luokka "lomake-tr-valinta"
          ::lomake/col-luokka "col-xs-2 tr-input"
          :validoi [[:ei-tyhja "Syötä alkuosa"]]
          :desimaalien-maara 0}
         {:nimi :aet
          :otsikko "A-et"
          :pakollinen? true
          :tyyppi :numero
          :input-luokka "lomake-tr-valinta"
          ::lomake/col-luokka "col-xs-2 tr-input"
          :validoi [[:ei-tyhja "Syötä alkuetäisyys"]]
          :desimaalien-maara 0}
         {:nimi :losa
          :otsikko "L-osa"
          :pakollinen? true
          :tyyppi :numero
          :input-luokka "lomake-tr-valinta"
          ::lomake/col-luokka "col-xs-2 tr-input"
          :validoi [[:ei-tyhja "Syötä loppuosa"]]
          :desimaalien-maara 0}
         {:nimi :let
          :otsikko "L-et"
          :pakollinen? true
          :tyyppi :numero
          :input-luokka "lomake-tr-valinta"
          ::lomake/col-luokka "col-xs-2 tr-input"
          :validoi [[:ei-tyhja "Syötä loppuetäisyys"]]
          :desimaalien-maara 0}))
     
     ;; Menetelmä
     (lomake/ryhma
       {:otsikko "Menetelmä"
        :ryhman-luokka "lomakeryhman-otsikko-tausta lomake-ryhma-otsikko"}
       ;; Alasveto
       (lomake/rivi
         {:otsikko "Menetelmä"
          :pakollinen? true
          :rivi-luokka "lomakeryhman-rivi-tausta"
          :validoi [[:ei-tyhja "Valitse menetelmä"]]
          :nimi :tyomenetelma
          :tyyppi :valinta
          :valinnat (into [nil] alasveto-valinnat)
          :valinta-nayta #(if %
                            (alasveto-kuvaukset %)
                            "- Valitse -")
          ::lomake/col-luokka "leveys-kokonainen"}))

     ;; Määrä
     (lomake/ryhma
       {:otsikko "Määrä"
        :ryhman-luokka "lomakeryhman-otsikko-tausta lomake-ryhma-otsikko"}
       (lomake/rivi
         {:otsikko "Määrä"
          :pakollinen? true
          :rivi-luokka "lomakeryhman-rivi-tausta cy-maara"
          :nimi :maara
          :tyyppi :numero
          :vayla-tyyli? true
          :validoi [[:ei-tyhja "Syötä määrä"]]
          ::lomake/col-luokka "maara-valinnat"}
         {:otsikko "Yksikkö"
          :tyyppi :valinta
          :valinnat (vec tiedot/reikapaikkausten-yksikot)
          :nimi :reikapaikkaus-yksikko
          :pakollinen? true
          :vayla-tyyli? true
          ::lomake/col-luokka "maara-valinnat"}
         {:otsikko "Kustannus"
          :pakollinen? true
          :rivi-luokka "lomakeryhman-rivi-tausta"
          :nimi :kustannus
          :tyyppi :euro
          :teksti-oikealla "EUR"
          :vayla-tyyli? true
          :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
          ::lomake/col-luokka "maara-valinnat"}))]
    valittu-rivi]])
