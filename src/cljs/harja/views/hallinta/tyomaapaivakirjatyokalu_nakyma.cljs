(ns harja.views.hallinta.tyomaapaivakirjatyokalu-nakyma
  "Työkalu työmaapäiväkirjojen lisäämiseksi testiurakoille."
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
            [harja.tiedot.hallinta.tyomaapaivakirjatyokalu-tiedot :as tiedot])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn paivakirjalomake [e! {:keys [paivakirja] :as app}]
  (let [disable-tallenna? (if (or (nil? (:lahetysaika paivakirja))
                                (nil? (:valittu-urakka paivakirja)))
                            true
                            false)]
    [:div.yhteydenpito
     [:h3 "Työmaapäiväkirjan simulointi valitulle urakalle"]
     [:p "Aloita valitsemalla hallintayksikkö ja sitten urakka."]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [paivakirja]
                    [:div
                     [napit/tallenna "Lähetä"
                      #(e! (tiedot/->Laheta paivakirja))
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
       {:nimi :ulkoinen-id
        :otsikko "Ulkoinen id"
        :tyyppi :numero
        :pituus-max 40
        :pakollinen? true}

       {:nimi :tyokoneiden-lkm
        :otsikko "Työkoneiden lukumaara"
        :tyyppi :numero
        :pakollinen? true}
       {:nimi :lisakaluston-lkm
        :otsikko "Lisäkaluston lukumaara"
        :tyyppi :numero
        :pakollinen? true}
       {:nimi :saa-asematietojen-lkm
        :otsikko "Sääasematietojen lukumaara"
        :tyyppi :numero
        :pakollinen? true}
       {:nimi :paivystaja
        :otsikko "Päivystäjä"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :tyonjohtaja
        :otsikko "Työnjohtaja"
        :tyyppi :string
        :pakollinen? true}]
      paivakirja]]))

(defn simuloi-tyomaapaivakirja* [e! app]
  (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
    [:div
     [debug/debug app]
     (when (not (empty? (:oikeudet-urakoihin app)))
       [:div
        [:p [:b "Käyttäjällä on oikeus lisätä työmaapäiväkirjoja seuraaviin urakoihin:"]]
        (for [urakka (:oikeudet-urakoihin app)]
          ^{:key (str urakka)}
          [:div [:span (str (:urakka-id urakka) " ")] [:span (:urakka-nimi urakka)]])])
     [:div
      ;; Näytetään mahdollisuus lisätä oikeudet urakkaan vain, jos siihen ei vielä ole oikeuksia
      (if (and (get-in app [:paivakirja :valittu-urakka])
            (not (some (fn [u] (when (= (get-in app [:paivakirja :valittu-urakka :id]) (:urakka-id u)) true)) (:oikeudet-urakoihin app))))
        [:div
         [:p [:b "Lisää oikeudet puuttuvaan urakkaan"]]
         [napit/tallenna (str "Lisää oikeudet urakkaan: " (get-in app [:paivakirja :valittu-urakka :nimi]))
          #(e! (tiedot/->LisaaOikeudetUrakkaan (get-in app [:paivakirja :valittu-urakka :id])))
          {:paksu? true}]]
        (paivakirjalomake e! app))]]
    "Puutteelliset käyttöoikeudet"))

(defn simuloi-tyomaapaivakirja []
  (do
    (println "jees")
    [tuck tiedot/data simuloi-tyomaapaivakirja*]))
