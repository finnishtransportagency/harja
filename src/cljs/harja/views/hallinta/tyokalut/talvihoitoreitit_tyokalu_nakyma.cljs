(ns harja.views.hallinta.tyokalut.talvihoitoreitit-tyokalu-nakyma
  "Työkalu talvihoitoreittien lisäämiseksi testiurakoille."
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.hallinta.tyokalut.talvihoitoreitti-tyokalu :as tiedot])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn talvihoitoreittilomake [e! {:keys [talvihoitoreitti] :as app}]
  (let [disable-tallenna? (if (nil? (:valittu-urakka talvihoitoreitti))
                            true
                            false)]
    [:div.yhteydenpito
     [:h2 "Talvihoitoreitin lähetys valitulle urakalle"]
     [:p "Aloita valitsemalla hallintayksikkö ja sitten urakka."]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [talvihoitoreitti]
                    [:div
                     [napit/tallenna "Lähetä"
                      #(e! (tiedot/->Laheta talvihoitoreitti))
                      {:disabled disable-tallenna? :paksu? true}]])
       :muokkaa! #(e! (tiedot/->Muokkaa %))}
      [{:nimi :valittu-hallintayksikko
        :otsikko "Valitse hallintayksikko"
        :tyyppi :valinta
        :valinnat @hal/vaylamuodon-hallintayksikot
        :valinta-nayta :nimi
        :pakollinen? true}
       {:id (hash (:mahdolliset-urakat app))
        :nimi :valittu-urakka
        :otsikko "Valitse urakka"
        :tyyppi :valinta
        :valinnat (:mahdolliset-urakat app)
        :valinta-nayta :nimi
        :pakollinen? true}
       {:nimi :valittu-jarjestelma
        :otsikko "Järjestelma"
        :tyyppi :string
        :pituus-max 40
        :pakollinen? true}
       {:nimi :suorittaja-nimi
        :otsikko "Suorittaja"
        :tyyppi :string
        :pituus-max 40
        :pakollinen? true}
       {:nimi :lahetysaika
        :otsikko "Lähetysaika"
        :tyyppi :string
        :pituus-max 40
        :pakollinen? true}
       {:nimi :reittinimi
        :otsikko "Reitin nimi"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :kalusto-lkm
        :otsikko "Kaluston määrä"
        :tyyppi :numero
        :pakollinen? true}
       {:nimi :kalustotyyppi
        :otsikko "Kaluston tyyppi"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :tierekisteriosoite
        :tyyppi :tierekisteriosoite
        :vayla-tyyli? true
        :lataa-piirrettaessa-koordinaatit? true}
      ]
      talvihoitoreitti]]))

(defn simuloi-talvihoitoreitti* [e! app]
  (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
    [:div
     [debug/debug app]
     (when (not (empty? (:oikeudet-urakoihin app)))
       [:div
        [:p [:b "Käyttäjällä on oikeus lisätä talvihoitoreitti seuraaviin urakoihin:"]]
        (for [urakka (:oikeudet-urakoihin app)]
          ^{:key (str urakka)}
          [:div [:span (str (:urakka-id urakka) " ")] [:span (:urakka-nimi urakka)]])])
     [:div
      ;; Näytetään mahdollisuus lisätä oikeudet urakkaan vain, jos siihen ei vielä ole oikeuksia
      (if (and (get-in app [:talvihoitoreitti :valittu-urakka])
            (not (some (fn [u] (when (= (get-in app [:talvihoitoreitti :valittu-urakka :id]) (:urakka-id u)) true)) (:oikeudet-urakoihin app))))
        [:div
         [:p [:b "Lisää oikeudet puuttuvaan urakkaan"]]
         [napit/tallenna (str "Lisää oikeudet urakkaan: " (get-in app [:talvihoitoreitti :valittu-urakka :nimi]))
          #(e! (tiedot/->LisaaOikeudetUrakkaan (get-in app [:talvihoitoreitti :valittu-urakka :id])))
          {:paksu? true}]]
        (talvihoitoreittilomake e! app))]]
    "Puutteelliset käyttöoikeudet"))

(defn simuloi-talvihoitoreitti []
  [tuck tiedot/data simuloi-talvihoitoreitti*])
