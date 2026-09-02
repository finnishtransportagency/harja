(ns harja.views.hallinta.tyokalut.toteumatyokalu-nakyma
  "Työkalu toteumien lisäämiseksi testiurakoille."
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
            [harja.tiedot.hallinta.tyokalut.toteumatyokalu-tiedot :as tiedot])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn toteumalomake [e! {:keys [toteumatiedot] :as app}]
  (let [disable-tallenna? (if (or (nil? (:lahetysaika toteumatiedot))
                                (nil? (:valittu-urakka toteumatiedot))
                                (nil? (:valittu-materiaali toteumatiedot))
                                (nil? (:koordinaatit app)))
                            true
                            false)
        disable-laheta2? (if (or (nil? (:lahetysaika toteumatiedot))
                                (nil? (:valittu-urakka toteumatiedot))
                                (nil? (:valittu-materiaali toteumatiedot)))
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
     [:span (str "1. Aloita valitsemalla hallintayksikkö ja sitten urakka.") [:br]
      (str "2. Huomaa, että samalla ulkoisella id:llä tehdään toteumaan päivitys. Eli käytä aina uniikkia ulkoista id:tä, jos et halua päivittää mitään.") [:br]
      (str "3. Voit kopioida JSON tuotannosta kohtaan json tiedoston tuotannosta valitsemalla api/lisaa-reittitoteuma ja Viestin sisällöksi: \"yksikko\":\"t\". Näin saat materiaaleja sisältäviä toteuma jsoneita.") [:br]
      (str "4. Jos haluat hoitoluokkatietoja raportille ja et käytä tuotannon jsonia, niin valitse tiereikisteriosoite huolella. Käytössäsi on hyvin vähän koko suomen tierekisteristä. Paras tuki on käytössä Raaseporin urakan alueella. Kysy tarvittaessa.")]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [toteumatiedot]
                    [:div
                     [:p "Koska tämä viritelmä on kesken, niin tr-osoitteen koordinaatteja ei saada, ennenkuin ne haetaan serveriltä"]
                     [:p (str (:koordinaatit app))]
                     (when (get-in app [:toteumatiedot :valittu-urakka :id])
                       [napit/tallenna "Hae tr-osoitteet ja ulkoinen-id"
                        #(do
                           (e! (tiedot/->HaeSeuraavaVapaaUlkoinenId))
                           (e! (tiedot/->HaeUrakanTierekisteriosoitteita (get-in app [:toteumatiedot :valittu-urakka :id]))))])
                     (when (get-in app [:toteumatiedot :valittu-urakka :id])
                       [napit/tallenna "Päivitä raportit"
                        #(e! (tiedot/->PaivitaRaportit))])
                     [napit/tallenna "Hae TR osoitteelle koordinaatit"
                      #(e! (tiedot/->HaeTROsoitteelleKoordinaatit toteumatiedot))
                      {:disabled disable-trhaku? :paksu? true}]
                     (if (seq (:koordinaatit app))
                       [napit/tallenna "Lähetä"
                        #(e! (tiedot/->Laheta toteumatiedot))
                        {:disabled disable-tallenna? :paksu? true}]
                       [napit/tallenna "Lähetä ilman koordinaatteja"
                        #(e! (tiedot/->HaeKoordinaatitJaLaheta toteumatiedot))
                        {:disabled disable-laheta2? :paksu? true}])])
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
        :valinnat (:mahdolliset-urakat app) ;tiedot/+mahdolliset-urakat+
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
       {:nimi :json-lahetysaika
        :otsikko "Lähetysaika JSONista"
        :tyyppi :radio-group
        :vaihtoehdot [:json :lomake]
        :vaihtoehto-nayta (fn [arvo]
                            ({:json "Ota lähetysajat JSONista"
                              :lomake "Ota lähetysaika lomakkeesta"}
                             arvo))}
       {:nimi :ulkoinen-id
        :otsikko "Ulkoinen id"
        :tyyppi :numero
        :pituus-max 40
        :pakollinen? true}
       {:nimi :lahde
        :otsikko "Lähde"
        :tyyppi :valinta
        :valinnat ["koneellinen", "kasin"]
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
        {:nimi :valittu-tehtava
         :otsikko "Valitse tehtävä (valinnainen)"
         :tyyppi :valinta
         :valinnat (concat [nil] tiedot/+mahdolliset-tehtavat+)
         :valinta-nayta #(if % (:nimi %) "- Ei tehtävää -")
         :pakollinen? false}
        {:nimi :tehtavamaara
         :otsikko "Tehtävämäärä"
         :tyyppi :string
         :pakollinen? false
         :napiton? (nil? (:valittu-tehtava toteumatiedot))}
        {:nimi :tehtavayksikko
         :otsikko "Tehtävän yksikkö"
         :tyyppi :valinta
         :valinnat ["tiekm" "jkm" "kaistakm" "km" "kpl" "m2" "m3"]
         :pakollinen? false
         :napiton? (nil? (:valittu-tehtava toteumatiedot))}
        {:nimi :tierekisteriosoite
        :tyyppi :tierekisteriosoite
        :vayla-tyyli? true
        :lataa-piirrettaessa-koordinaatit? true}
       {:nimi :json-tuotannosta
        :otsikko "JSON tuotannosta"
        :tyyppi :text
        :koko [50 20] :pituus-max 500000
        :vayla-tyyli? true}]

      toteumatiedot]

     #_[:div [:b "Urakan tierekisteriosoitteita, joita voi käyttää toteuman lisäämisessä"]
        [grid/grid
         {:otsikko "Tierekisteriosoitteet"
          :tunniste :id
          :piilota-toiminnot? true}
         [{:otsikko "Tie" :nimi :tie :tyyppi :string :leveys 1}
          {:otsikko "Osa" :nimi :osa :tyyppi :string :leveys 1}
          {:otsikko "Aet" :nimi :aet :tyyppi :string :leveys 1}
          {:otsikko "Let" :nimi :let :tyyppi :string :leveys 1}]
         (:tierekisteriosoitteita app)]]]))

(defn simuloi-toteuma* []
  (komp/luo
    (komp/sisaan-ulos
      #(go (do
             (nav/vaihda-kartan-koko! :S) ;; Otetaan kartta paremmin näkyviin, kun reitin tiedot on saatu renderöityä sinne
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
           #_ [kartta/kartan-paikka]
           (when (seq (:oikeudet-urakoihin app))
             [:div
              [:p [:b "Käyttäjällä on oikeus lisätä toteumia seuraaviin urakoihin:"]]
              (for [urakka (:oikeudet-urakoihin app)]
                ^{:key (str urakka)}
                [:div [:span (str (:urakka-id urakka) " ")] [:span (:urakka-nimi urakka)]])])
           [:div
            ;; Näytetään mahdollisuus lisätä oikeudet urakkaan vain, jos siihen ei vielä ole oikeuksia
            (if (and (get-in app [:toteumatiedot :valittu-urakka])
                  (not (some (fn [u] (when (= (get-in app [:toteumatiedot :valittu-urakka :id]) (:urakka-id u)) true)) (:oikeudet-urakoihin app))))
              [:div
               [:p [:b "Lisää oikeudet puuttuvaan urakkaan"]]
               [napit/tallenna (str "Lisää oikeudet urakkaan: " (get-in app [:toteumatiedot :valittu-urakka :nimi]))
                #(e! (tiedot/->LisaaOikeudetUrakkaan (get-in app [:toteumatiedot :valittu-urakka :id])))
                {:paksu? true}]]
              (toteumalomake e! app))]
           [debug/debug app]])
        "Puutteelliset käyttöoikeudet"))))

(defn simuloi-toteuma []
  [tuck tiedot/data simuloi-toteuma*])
