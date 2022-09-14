(ns harja.views.hallinta.toteumatyokalu-nakyma
  "Työkalu toteumien lisäämiseksi testiurakoille."
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.hallinta.toteumatyokalu-tiedot :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn toteumalomake [e! {:keys [toteumatiedot] :as app}]
  (let [disable-tallenna? (if (or (nil? (:lahetysaika toteumatiedot))
                                (nil? (:valittu-urakka toteumatiedot))
                                (nil? (:valittu-materiaali toteumatiedot))
                                (nil? (:koordinaatit app)))
                            true
                            false)
        disable-trhaku? (if (or (nil? (:numero (:tierekisteriosoite toteumatiedot)))
                              (nil? (:alkuosa (:tierekisteriosoite toteumatiedot)))
                              (nil? (:alkuetaisyys (:tierekisteriosoite toteumatiedot)))
                              (nil? (:loppuosa (:tierekisteriosoite toteumatiedot)))
                              (nil? (:loppuetaisyys (:tierekisteriosoite toteumatiedot)))
                              )
                          true
                          false)]
    [:div.yhteydenpito
     [:h3 "Reittitoteuman simulointi valitulle urakalle"]
     [:p "Aloita valitsemalla hallintayksikkö ja sitten urakka."]
     [:p "Huomaa, että samalla ulkoisella id:llä tehdään toteumaan päivitys. Eli käytä aina uniikkia ulkoista id:tä."]
     [:p "Käytössäsi on hyvin vähän koko suomen tierekisteristä. Paras tuki on käytössä Raaseporin urakan alueella. Kysy tarvittaessa."]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [toteumatiedot]
                    [:div
                     [:p "Koska tämä viritelmä on kesken, niin tr-osoitteen koordinaatteja ei saada, ennenkuin ne haetaan serveriltä"]
                     [:p (str (:koordinaatit app))]
                     [napit/tallenna "Hae TR osoitteelle koordinaatit"
                      #(e! (tiedot/->HaeTROsoitteelleKoordinaatit toteumatiedot))
                      {:disabled disable-trhaku? :paksu? true}]
                     [napit/tallenna "Lähetä"
                      #(e! (tiedot/->Laheta toteumatiedot))
                      {:disabled disable-tallenna? :paksu? true}]])
       :muokkaa! #(e! (tiedot/->Muokkaa %))}
      [{:nimi :valittu-hallintayksikko
        :otsikko "Valitse hallintayksikko"
        :tyyppi :valinta
        :valinnat @hal/vaylamuodon-hallintayksikot
        :valinta-nayta :nimi
        :pakollinen? true}
       {:id #_(hash tiedot/+mahdolliset-urakat+)
        (hash (:mahdolliset-urakat app))
        :nimi :valittu-urakka
        :otsikko "Valitse urakka"
        :tyyppi :valinta
        :valinnat (:mahdolliset-urakat app)                 ;tiedot/+mahdolliset-urakat+
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
       {:nimi :sopimusid
        :otsikko "Sopimusid"
        :tyyppi :numero
        :pituus-max 40
        :pakollinen? true
        :tarkkaile-ulkopuolisia-muutoksia? true}
       {:nimi :valittu-materiaali
        :otsikko "Valitse materiaali"
        :tyyppi :valinta
        :valinnat tiedot/+mahdolliset-materiaalit+
        :valinta-nayta :nimi
        :pakollinen? true}
       {:nimi :materiaalimaara
        :otsikko "Materiaalimäärä"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :tierekisteriosoite
        :tyyppi :tierekisteriosoite
        :vayla-tyyli? true
        :lataa-piirrettaessa-koordinaatit? true}]
      toteumatiedot]]))

(defn simuloi-toteuma* []
  (komp/luo
    (komp/sisaan-ulos
      #(go (do
             (nav/vaihda-kartan-koko! :S)                   ;; Otetaan kartta paremmin näkyviin, kun reitin tiedot on saatu renderöityä sinne
             (kartta-tasot/taso-paalle! :tr-valitsin)
             (kartta-tasot/taso-paalle! :organisaatio)
             (reset! tiedot/nakymassa? true)))
      #(do
         (nav/vaihda-kartan-koko! :S)
         (kartta-tasot/taso-pois! :tr-valitsin)
         (kartta-tasot/taso-pois! :organisaatio)
         (reset! tiedot/nakymassa? false)))
    (fn [e! app]
      (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
        (when @tiedot/nakymassa?
          [:div
           [kartta/kartan-paikka]
           [debug/debug app]
           (when (:oikeudet-urakoihin app)
             [:div
              [:p [:b "Käyttäjällä on oikeus lisätä toteumia seuraaviin urakoihin:"]]
              (for [urakka (:oikeudet-urakoihin app)]
                ^{:key (str urakka)}
                [:div [:span (str (:urakka-id urakka) " ")] [:span (:urakka-nimi urakka)]])])
           [:div
            ;; Näytetään mahdollisuus lisätä oikeudet urakkaan vain, jos siihen ei vielä ole oikeuksia
            (when (and (get-in app [:toteumatiedot :valittu-urakka])
                    (not (some (fn [u] (when (= (get-in app [:toteumatiedot :valittu-urakka :id]) (:urakka-id u)) true)) (:oikeudet-urakoihin app))))
              [:p "Lisää oikeudet puuttuvaan urakkaan"]
              [napit/tallenna (str "Lisää oikeudet urakkaan: " (get-in app [:toteumatiedot :valittu-urakka :nimi]))
               #(e! (tiedot/->LisaaOikeudetUrakkaan (get-in app [:toteumatiedot :valittu-urakka :id])))
               {:paksu? true}])]
           (toteumalomake e! app)])
        "Puutteelliset käyttöoikeudet"))))

(defn simuloi-toteuma []
  [tuck tiedot/data simuloi-toteuma*])
