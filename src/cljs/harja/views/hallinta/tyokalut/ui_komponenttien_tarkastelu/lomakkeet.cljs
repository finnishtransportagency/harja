(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.lomakkeet
  (:require [reagent.core :as r]
            [harja.ui.bootstrap :as bootstrap]
            [harja.ui.kentat :as kentat]
            [harja.ui.perus :as ui]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(def ^:private urakkatyypit
  [{:arvo :hoito :nimi "Hoito"}
   {:arvo :paallyste :nimi "Päällyste"}
   {:arvo :vesivayla :nimi "Vesiväylä"}])

(defn- suodatin [komponentti]
  (r/with-let [haku (r/atom "")
               vain-puutteelliset? (r/atom false)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Suodatin antaa kentälle id:n. Käyttöpaikka omistaa arvon ja muutoksen käsittelyn."]
       [ui/suodatinrivi {:data-cy (str ankkuri "-rivi")}
        [ui/suodatin {:otsikko "Urakka" :id (str ankkuri "-haku")}
         (fn [{:keys [id]}]
           [kentat/tee-kentta
            {:tyyppi :string
             :elementin-id id
             :placeholder "Hae urakan nimellä..."}
            haku])]
        [ui/suodatin {:id (str ankkuri "-puutteelliset")}
         [kentat/tee-kentta
          {:tyyppi :checkbox
           :teksti "Näytä vain puutteelliset"}
          vain-puutteelliset?]]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Haku: \"" @haku "\", vain puutteelliset: " @vain-puutteelliset?)]])))

(defn- valintaryhma [komponentti]
  (r/with-let [valinta (r/atom :hoito)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Arvot ovat keywordeja: vaihda-fn saa valitun arvon sellaisenaan."]
       [ui/valintaryhma
        {:otsikko "Urakkatyyppi"
         :id (str ankkuri "-urakkatyyppi")
         :arvo @valinta
         :vaihda-fn #(reset! valinta %)
         :vaihtoehdot urakkatyypit
         :tyhja-valinta "Valitse..."
         :ohje "Valitse yksi vaihtoehto."}]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Valittu: " (pr-str @valinta))]])))

(defn- lomake [komponentti]
  (r/with-let [nimi (r/atom "")
               tyyppi (r/atom :hoito)
               lataus? (r/atom false)
               tallennettu? (r/atom false)
               virhe (r/atom nil)]
    (let [ankkuri (kehys/ankkuri komponentti)
          tallenna! (fn []
                      (when-not @lataus?
                        (reset! lataus? true)
                        (js/setTimeout
                          (fn []
                            (reset! lataus? false)
                            (if (seq @nimi)
                              (do (reset! virhe nil)
                                  (reset! tallennettu? true))
                              (do (reset! tallennettu? false)
                                  (reset! virhe "Nimi puuttuu"))))
                          300)))
          tyhjenna! (fn []
                      (when-not @lataus?
                        (reset! nimi "")
                        (reset! tallennettu? false)
                        (reset! virhe nil)))]
      [kehys/kortti komponentti
       [:p "Näkymä omistaa arvot ja tallennuksen ehdot. Rakenteet omistavat id-yhteydet ja roolit."]
       [ui/pino {:data-cy (str ankkuri "-pino")}
        [ui/kenttaryhma
         {:otsikko "Nimi"
          :id (str ankkuri "-nimi")
          :ohje "Anna vähintään yksi merkki."
          :virhe @virhe
          :pakollinen? true}
         (fn [{:keys [id disabled?]}]
           [kentat/tee-kentta
            {:tyyppi :string
             :elementin-id id
             :disabled? (or disabled? @lataus?)
             :placeholder "Esimerkiksi Harja"}
            nimi])]
        [ui/valintaryhma
         {:otsikko "Tyyppi"
          :id (str ankkuri "-tyyppi")
          :arvo @tyyppi
          :vaihda-fn #(reset! tyyppi %)
          :vaihtoehdot urakkatyypit
          :ohje "Valitse lomakkeen käyttötarkoitus."
          :disabled? @lataus?}]
        [ui/toimintoryhma {:data-cy (str ankkuri "-toiminnot")}
         [napit/nappi "Tallenna" tallenna!
          {:data-cy (str ankkuri "-tallenna")
           :disabled @lataus?
           :tallennus-kaynnissa? @lataus?}]
         [napit/nappi "Tyhjennä" tyhjenna!
          {:data-cy (str ankkuri "-tyhjenna")
           :disabled @lataus?
           :luokka "nappi-toissijainen"}]]
        [ui/tilaviesti
         {:tila (cond @lataus? :lataus
                      @virhe :virhe
                      :else :onnistui)
          :viesti (cond @lataus? "Tallennetaan..."
                        @virhe "Tallennus epäonnistui, tarkista lomake"
                        @tallennettu? "Nimi tallennettu")
          :data-cy (str ankkuri "-tila")}]]])))

(defn- rivi [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [yleiset/rivi {:koko {:lg 4 :md 6 :xs 12}
                    :luokka "ui-komponenttien-tarkastelu-rivi-sarake"}
      [:div {:data-cy (str ankkuri "-sarake-a")} "Sarake A"]
      [:div {:data-cy (str ankkuri "-sarake-b")} "Sarake B"]
      [:div {:data-cy (str ankkuri "-sarake-c")} "Sarake C"]]]))

(defn- tekstikentta [komponentti]
  (r/with-let [arvo (r/atom "")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Arvo on käyttöpaikan omistama. Kenttä välittää muutokset atomiin."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :string
          :elementin-id (str ankkuri "-input")
          :placeholder "Kirjoita tekstiä..."}
         arvo]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: \"" @arvo "\"")]])))

(defn- numerokentta [komponentti]
  (r/with-let [arvo (r/atom nil)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Kenttä hyväksyy vain numeroita. Arvo on käyttöpaikan omistama."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :numero
          :elementin-id (str ankkuri "-input")
          :desimaalien-maara 2
          :kokonaisosan-maara 6
          :vaadi-ei-negatiivinen? true}
         arvo]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: " (pr-str @arvo))]])))

(defn- sahkopostikentta [komponentti]
  (r/with-let [arvo (r/atom "")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Kenttä käyttää selaimen sähköpostin validointia. Arvo on käyttöpaikan omistama."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :email
          :elementin-id (str ankkuri "-input")
          :placeholder "nimi@esimerkki.fi"}
         arvo]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: \"" @arvo "\"")]])))

(defn- puhelinkentta [komponentti]
  (r/with-let [arvo (r/atom "")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Puhelinnumeron kenttä. Arvo on käyttöpaikan omistama."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :puhelin
          :elementin-id (str ankkuri "-input")
          :placeholder "040 1234567"}
         arvo]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: \"" @arvo "\"")]])))

(defn- radiokentta [komponentti]
  (r/with-let [valittu (r/atom "Keski")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Vaihtoehdoista valitaan yksi. Arvo on käyttöpaikan omistama."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :radio
          :valinnat ["Matala" "Keski" "Korkea"]}
         valittu]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: " (pr-str @valittu))]])))

(defn- checkbox-ryhma [komponentti]
  (r/with-let [valitut (r/atom #{"Foo" "Baz"})]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Monta vaihtoehtoa voi olla valittuna. Arvo on joukko."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :checkbox-group
          :vaihtoehdot ["Foo" "Bar" "Baz" "Quux"]
          :vaihtoehto-nayta str}
         valitut]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: " (pr-str (sort @valitut)))]])))

(defn- radio-group-kentta [komponentti]
  (r/with-let [valittu (r/atom "Keski")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Vaihtoehdoista valitaan yksi. Arvo on käyttöpaikan omistama."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :radio-group
          :vaihtoehdot ["Matala" "Keski" "Korkea"]
          :vaihtoehto-nayta str}
         valittu]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: " (pr-str @valittu))]])))

(defn- valinta-kentta [komponentti]
  (r/with-let [valittu (r/atom "Keski")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Pudotusvalikko tee-kentän kautta. Arvo on käyttöpaikan omistama."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :valinta
          :valinnat ["Matala" "Keski" "Korkea"]
          :valinta-nayta str
          :elementin-id (str ankkuri "-valinta")}
         valittu]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: " (pr-str @valittu))]])))

(defn- dropdown-valikko [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Otsikko ja vaihtoehtolista. Avautuminen ei kuulu komponenttiin, joten tässä näkyy vain otsikko."]
     [:div {:data-cy (str ankkuri "-valikko")}
      [bootstrap/dropdown "Valikko" [[:a {:href "#"} "Ensimmäinen"] [:a {:href "#"} "Toinen"]]]]]))

(defn- valintaruutu [komponentti]
  (r/with-let [valittu? (r/atom false)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :checkbox
          :teksti "Hyväksy ehdot"
          :input-id (str ankkuri "-input")}
         valittu?]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Valittu: " (if @valittu? "kyllä" "ei"))]])))

(defn- raksiboksi [komponentti]
  (r/with-let [valittu? (r/atom false)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Raksiboksi käärii valintaruudun teksti- ja infoalueeseen. Arvon ja muutoksen käsittelee käyttöpaikka."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/raksiboksi
         {:teksti "Näytä vain aktiiviset"
          :toiminto #(reset! valittu? %)}
         valittu?]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Valittu: " (if @valittu? "kyllä" "ei"))]])))

(defn- pino [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Pino asettaa lapset allekkain. Välit omistaa rakenne."]
     [ui/pino {:data-cy (str ankkuri "-pino")}
      [:div {:data-cy (str ankkuri "-lapsi-a")} "Ensimmäinen lapsi"]
      [:div {:data-cy (str ankkuri "-lapsi-b")} "Toinen lapsi"]]]))

(defn- suodatinrivi [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Rivi rivittää suodattimet. Suodattimen kentän sisällön antaa käyttöpaikka."]
     [ui/suodatinrivi {:data-cy (str ankkuri "-rivi")}
      [ui/suodatin {:otsikko "Alku" :id (str ankkuri "-alku")}
       [:span "Alkukenttä"]]
      [ui/suodatin {:otsikko "Loppu" :id (str ankkuri "-loppu")}
       [:span "Loppukenttä"]]]]))

(defn- toimintoryhma [komponentti]
  (r/with-let [painettu (r/atom nil)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Ryhmä pitää toimintopainikkeet yhdessä. Painikkeiden toiminnan antaa käyttöpaikka."]
       [ui/toimintoryhma {:data-cy (str ankkuri "-toiminnot")}
        [napit/nappi "Tallenna" #(reset! painettu "Tallenna")
         {:data-cy (str ankkuri "-tallenna")}]
        [napit/nappi "Tyhjennä" #(reset! painettu "Tyhjennä")
         {:data-cy (str ankkuri "-tyhjenna")
          :luokka "nappi-toissijainen"}]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Viimeksi painettu: " (or @painettu "ei mitään"))]])))

(defn- tilaviesti [komponentti]
  (r/with-let [tila (r/atom :onnistui)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Tila ja viesti tulevat käyttöpaikalta. Viestisäiliö näkyy aina."]
       [ui/toimintoryhma {:data-cy (str ankkuri "-tilat")}
        [napit/nappi "Lataus" #(reset! tila :lataus)
         {:data-cy (str ankkuri "-lataus")
          :luokka "nappi-toissijainen"}]
        [napit/nappi "Onnistui" #(reset! tila :onnistui)
         {:data-cy (str ankkuri "-onnistui")
          :luokka "nappi-toissijainen"}]
        [napit/nappi "Virhe" #(reset! tila :virhe)
         {:data-cy (str ankkuri "-virhe")
          :luokka "nappi-toissijainen"}]]
       [ui/tilaviesti {:tila @tila
                       :viesti (case @tila
                                 :lataus "Tallennetaan..."
                                 :onnistui "Nimi tallennettu"
                                 :virhe "Tallennus epäonnistui")
                       :data-cy (str ankkuri "-tila")}]])))

(defn- kenttaryhma [komponentti]
  (r/with-let [arvo (r/atom "")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Natiivi kenttä saa id:n ja ARIA-attribuutit, jolloin label, ohje ja virhe liittyvät siihen."]
       [ui/kenttaryhma {:otsikko "Natiivi kenttä"
                        :id (str ankkuri "-natiivi")
                        :ohje "Anna vähintään yksi merkki."
                        :virhe (when (empty? @arvo) "Arvo puuttuu")
                        :pakollinen? true
                        :data-cy (str ankkuri "-natiivi-ryhma")}
        (fn [{:keys [id aria-describedby aria-invalid aria-required disabled?]}]
          [:input {:id id
                   :type "text"
                   :value @arvo
                   :on-change #(reset! arvo (.. % -target -value))
                   :aria-describedby aria-describedby
                   :aria-invalid aria-invalid
                   :aria-required aria-required
                   :disabled disabled?}])]
       [:p "Rajoitus: tee-kentta ei vielä välitä ARIA-attribuutteja, joten yhteyttä label-kenttään ja virheeseen ei voi tarkistaa sen kohdalla."]
       [ui/kenttaryhma {:otsikko "tee-kentta"
                        :id (str ankkuri "-tee-kentta")
                        :data-cy (str ankkuri "-tee-kentta-ryhma")}
        (fn [{:keys [id]}]
          [kentat/tee-kentta
           {:tyyppi :string
            :elementin-id id}
           arvo])]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Arvo: \"" @arvo "\"")]])))

(defn- tietoja [komponentti]
  [kehys/kortti komponentti
   [:p "Otsikko-arvo-rivit. Tyhjä arvo jätetään pois."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/tietoja {:class "body-text"}
     "Nimi" "Esimerkki Oy"
     "Tyhjä kenttä" nil
     "Tila" [:span {:data-cy (kehys/ankkuri komponentti "-arvo")} "Hyväksytty"]]]])

(register! :kenttaryhma {:esikatselu kenttaryhma})
(register! :tietoja {:esikatselu tietoja})

(defn- taulukkotietonakyma-esikatselu [komponentti]
  [kehys/kortti komponentti
   [:p "Sama otsikko-arvo-data taulukkona. Tyhjä arvo jätetään pois."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/taulukkotietonakyma {}
     "Nimi" "Esimerkki Oy"
     "Tyhjä kenttä" nil
     "Tila" [:span {:data-cy (kehys/ankkuri komponentti "-arvo")} "Hyväksytty"]]]])

(register! :taulukkotietonakyma {:esikatselu taulukkotietonakyma-esikatselu})
(register! :suodatin {:esikatselu suodatin})
(defn- valintaryhma-simulaattorissa [solmu tila _lapsi-fn]
  [ui/valintaryhma
   (merge {:id "ui-simulaattori-urakkatyyppi"
           :arvo @(:valinta tila)
           :vaihda-fn (fn [arvo] (reset! (:valinta tila) arvo))
           :ohje "Valinta tulee deklaratiivisen skenaarion vaihtoehdoista."}
          (:asetukset solmu))])

(defn- lomake-simulaattorissa [solmu _tila lapsi-fn]
  [ui/pino {:data-cy "ui-simulaattori-lomake"}
   (map-indexed (fn [indeksi lapsi]
                  ^{:key indeksi}
                  [lapsi-fn lapsi])
                (:lapset solmu))])

(register! :valintaryhma {:esikatselu valintaryhma
                          :renderoi valintaryhma-simulaattorissa})
(register! :lomake {:esikatselu lomake
                    :renderoi lomake-simulaattorissa})
(register! :rivi {:esikatselu rivi})
(register! :tekstikentta {:esikatselu tekstikentta})
(register! :numerokentta {:esikatselu numerokentta})
(register! :sahkopostikentta {:esikatselu sahkopostikentta})
(register! :puhelinkentta {:esikatselu puhelinkentta})
(register! :valintaruutu {:esikatselu valintaruutu})
(register! :raksiboksi {:esikatselu raksiboksi})
(register! :radiokentta {:esikatselu radiokentta})
(register! :checkbox-ryhma {:esikatselu checkbox-ryhma})
(register! :radio-group {:esikatselu radio-group-kentta})
(register! :valinta {:esikatselu valinta-kentta})
(register! :dropdown {:esikatselu dropdown-valikko})

(defn- asettelu-simulaattorissa [asettelu-fn]
  (fn [solmu _tila lapsi-fn]
    (apply asettelu-fn (:asetukset solmu)
           (map-indexed (fn [indeksi lapsi]
                          ^{:key indeksi}
                          [lapsi-fn lapsi])
                        (:lapset solmu)))))

(defn- tilaviesti-simulaattorissa [solmu _tila _lapsi-fn]
  [ui/tilaviesti (:asetukset solmu)])

(register! :pino {:esikatselu pino
                  :renderoi (asettelu-simulaattorissa ui/pino)})
(register! :suodatinrivi {:esikatselu suodatinrivi
                          :renderoi (asettelu-simulaattorissa ui/suodatinrivi)})
(register! :toimintoryhma {:esikatselu toimintoryhma
                           :renderoi (asettelu-simulaattorissa ui/toimintoryhma)})
(register! :tilaviesti {:esikatselu tilaviesti
                        :renderoi tilaviesti-simulaattorissa})
