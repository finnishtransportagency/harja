(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.lomakkeet
  (:require [reagent.core :as r]
            [harja.ui.bootstrap :as bootstrap]
            [harja.ui.kentat :as kentat]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.perus :as ui]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(def ^:private urakkatyypit
  [:hoito :paallyste :vesivayla])

(def ^:private urakkatyypin-nimi
  {:hoito "Hoito"
   :paallyste "Päällyste"
   :vesivayla "Vesiväylä"})

;; Simulaattorin asetukset ovat EDN-dataa, joten arvo voi olla minkä tahansa tyyppinen.
(defn- vektori [arvo]
  (if (coll? arvo) (vec arvo) []))

(defn- joukko [arvo]
  (if (coll? arvo) (set arvo) #{}))

(defn- numero-tai [arvo oletus]
  (if (number? arvo) arvo oletus))

(defn- positiivinen-numero [arvo]
  (when (and (number? arvo) (pos? arvo)) arvo))

;; Kentät kutsuvat valinta-nayta-funktiota myös toisella argumentilla, siksi muuttuva aritia.
(defn- vaihtoehdon-teksti [vaihtoehto & _]
  (if (keyword? vaihtoehto) (name vaihtoehto) (str vaihtoehto)))

(defn- vaihtoehto-arvo [vaihtoehto]
  (if (or (keyword? vaihtoehto) (string? vaihtoehto)) vaihtoehto (str vaihtoehto)))

(defn- sisalto-renderoijana [sisalto]
  (fn [solmu _tila _lapsi-fn]
    [sisalto solmu]))

(defn- rivit-argumentit
  "Muuntaa `[otsikko arvo]`-parit yleiset-komponentin muotoon. Tyhjä arvo jää nil:ksi."
  [rivit]
  (mapcat (fn [[otsikko arvo]] [(str otsikko) (some-> arvo str)])
    (filter sequential? (vektori rivit))))

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
        [:div
         [yleiset/pudotusvalikko
          "Tyyppi"
          {:data-cy (str ankkuri "-tyyppi")
           :elementin-id (str ankkuri "-tyyppi")
           :valinta @tyyppi
           :valitse-fn #(reset! tyyppi %)
           :format-fn urakkatyypin-nimi
           :vayla-tyyli? true
           :disabled @lataus?}
          urakkatyypit]
         [:p.ui-ohje "Valitse lomakkeen käyttötarkoitus."]]
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

(defn- paivamakentta [komponentti]
  (r/with-let [arvo (r/atom nil)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Päivämäärä ja validointi ovat käyttöpaikan vastuulla."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :pvm
          :elementin-id (str ankkuri "-input")
          :placeholder "pp.kk.vvvv"}
         arvo]]])))

(defn- tekstialue [komponentti]
  (r/with-let [arvo (r/atom "")]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Monirivisen tekstin arvo ja merkkirajoitus ovat käyttöpaikan vastuulla."]
       [:div {:data-cy (str ankkuri "-kentta")}
        [kentat/tee-kentta
         {:tyyppi :text
          :elementin-id (str ankkuri "-input")
          :koko [80 4]
          :placeholder "Kirjoita lisätiedot..."}
         arvo]]])))

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
          :vayla-tyyli? true
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

(defn- vaaka [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Vaaka asettaa lapset vierekkäin ja omistaa välit, tasaukset sekä rivityksen."]
     [ui/pino {:data-cy (str ankkuri "-esimerkit")}
      [ui/vaaka {:data-cy (str ankkuri "-normaali")
                 :vali :pieni
                 :ristitasaus :keskelle}
       [napit/nappi "Ensimmäinen" (constantly nil)]
       [napit/nappi "Toinen" (constantly nil)
        {:luokka "nappi-toissijainen"}]]
      [ui/vaaka {:data-cy (str ankkuri "-rivitetty")
                 :vali :suuri
                 :rivita? true
                 :tasaus :valiin
                 :ristitasaus :loppu}
       [:span "Pitkä, rivittyvä sisältö vasemmalla"]
       [:span "Toinen sisältö oikealla"]]
      [ui/vaaka {:data-cy (str ankkuri "-tyhja")}]]]))

(defn- sailio [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Säiliö rajaa sisällön leveyden ja sivun vaakasuuntaiset sisäreunat ilman ulkomarginaaleja."]
     [ui/sailio {:data-cy (str ankkuri "-sailio")
                 :leveys :keski
                 :sisareuna :pieni}
      [ui/pino {:data-cy (str ankkuri "-sisalto")}
       [:span "Säiliön sisältö käyttää nimettyjä leveys- ja sisäreunatokeneita."]
       [:span "Käyttöpaikka omistaa ulkopuolisen sijoittelun."]]]
     [ui/sailio {:data-cy (str ankkuri "-tyhja")
                 :leveys :pieni
                 :sisareuna :pieni}]
     [ui/sailio {:data-cy (str ankkuri "-keskitetty")
                 :leveys :pieni
                 :sisareuna :pieni
                 :keskita? true}]]))

(defn- osio [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Osio yhdistää otsikon ja sisältöalueen saavutettavasti käyttöpaikan antamalla id:llä."]
     [ui/osio {:id (str ankkuri "-osio")
               :otsikko "Urakan yhteenveto"
               :otsikkotaso 3
               :vali :pieni}
      [:p "Osio ei rakenna korttipintaa tai aseta ulkomarginaaleja."]
      [:p "Osion sisältö kuuluu käyttöpaikalle."]]
     [ui/osio {:id (str ankkuri "-tyhja")
               :otsikko "Tyhjä osio"
               :otsikkotaso 3}]]))

(defn- ruudukko [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Ruudukko tukee 1–4 palstaa. Alle 48 rem:n levyinen näkymä käyttää aina yhtä palstaa."]
     [ui/ruudukko {:data-cy (str ankkuri "-ruudukko")
                   :palstoja 3
                   :vali :keski}
      [:div {:data-cy (str ankkuri "-solu-a")} "Ensimmäinen yhteenveto"]
      [:div {:data-cy (str ankkuri "-solu-b")} "Toinen yhteenveto"]
      [:div {:data-cy (str ankkuri "-solu-c")} "Pitkä teksti ei laajenna palstan ulkopuolelle"]]
     [ui/ruudukko {:data-cy (str ankkuri "-tyhja")
                   :palstoja 2}]]))

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
       [ui/kenttaryhma {:otsikko "Natiivi kenttä (ei Harja-komponentti)"
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
       [:p "Sama ARIA-sopimus välitetään myös Harjan tee-kentta-kentälle."]
       [ui/kenttaryhma {:otsikko "tee-kentta"
                        :id (str ankkuri "-tee-kentta")
                        :data-cy (str ankkuri "-tee-kentta-ryhma")}
        (fn [{:keys [id] :as kentta-attribuutit}]
          [kentat/tee-kentta
           (merge {:tyyppi :string
                   :elementin-id id}
             (dissoc kentta-attribuutit :id))
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

(defn- tietolista [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Semanttinen otsikko-arvo-lista ilman Bootstrap-luokkia. Nil-arvoiset rivit jätetään pois."]
     [ui/kortti {:id (str ankkuri "-esimerkki")
                 :otsikko "Urakan tiedot"
                 :kuvaus "Responsiivinen kortin sisältö normaalilla ja pitkällä arvolla."
                 :toiminto [napit/nappi "Muokkaa" #(identity nil) {:luokka "nappi-toissijainen"}]
                 :alatunniste [:span "Päivitetty 26.11.2026"]
                 :data-cy (str ankkuri "-kortti")}
      [ui/tietolista
       {:data-cy (str ankkuri "-juuri")
        :otsikkoleveys :suuri
        :rivit [{:otsikko "Nimi"
                 :arvo [:span {:data-cy (str ankkuri "-arvo")} "Pohjois-Suomen hoitourakka 2026–2030"]}
                {:otsikko "Tila"
                 :arvo "Hyväksytty"}
                {:otsikko "Kuvaus"
                 :arvo "Pitkä kuvaselitys rivityy tietolistan arvopalstassa myös kapealla näytöllä."}
                {:otsikko "Tyhjä lisätieto"
                 :arvo nil}]}]]]))

(defn- tietolista-simulaattorissa [solmu _tila _lapsi-fn]
  [ui/tietolista
   (merge {:data-cy "ui-simulaattori-tietolista"}
     (select-keys (:asetukset solmu)
       [:rivit :otsikkoleveys :tiivis? :viivat? :data-cy :luokka]))])

(register! :tietolista {:esikatselu tietolista
                         :renderoi tietolista-simulaattorissa})

(defn- kortti [komponentti]
  (let [ankkuri (kehys/ankkuri komponentti)]
    [kehys/kortti komponentti
     [:p "Yhteinen korttipohja: otsake, sisältö, valinnainen toiminto ja alatunniste."]
     [ui/kortti {:id (str ankkuri "-juuri")
                 :otsikko "Kortin otsikko"
                 :kuvaus "Sisältö säilyy käyttöpaikan vastuulla."
                 :toiminto [napit/nappi "Toiminto" #(identity nil) {:luokka "nappi-toissijainen"}]
                 :alatunniste [:span {:data-cy (str ankkuri "-alatunniste")} "Valinnainen alatunniste"]}
      [:p {:data-cy (str ankkuri "-sisalto")} "Kortin sisältö"]]]))

(defn- kortti-simulaattorissa [solmu _tila lapsi-fn]
  (let [asetukset (:asetukset solmu)]
    (into [ui/kortti
           (merge {:id (:data-cy asetukset)
                   :data-cy "ui-simulaattori-kortti"}
             (select-keys asetukset
               [:otsikko :otsikkotaso :kuvaus :toiminto :alatunniste :koko :data-cy :luokka]))]
      (map-indexed (fn [indeksi lapsi]
                     ^{:key indeksi}
                     [lapsi-fn lapsi])
        (:lapset solmu)))))

(register! :kortti {:esikatselu kortti
                    :renderoi kortti-simulaattorissa})

(defn- kenttaryhma-simulaattorissa [solmu _tila lapsi-fn]
  (let [{:keys [data-cy] :as asetukset} (:asetukset solmu)]
    [ui/kenttaryhma
     (merge {:data-cy "ui-simulaattori-kenttaryhma"
             :id data-cy}
       (select-keys asetukset [:otsikko :ohje :virhe :pakollinen? :disabled? :data-cy]))
     (fn [kentta-attribuutit]
       [lapsi-fn (assoc (first (:lapset solmu))
                  :kentta-attribuutit kentta-attribuutit)])]))

(register! :kenttaryhma {:esikatselu kenttaryhma
                         :renderoi kenttaryhma-simulaattorissa})

(def ^:private staattinen-liitetiedosto
  {:id 1 :nimi "tarkastus.pdf" :tyyppi "application/pdf" :koko 12345 :virustarkastettu? true})

(defn- liitteet-esikatselu [komponentti]
  [kehys/kortti komponentti
   [:p "Pelkkä liitetiedosto ilman urakkaa. Lisäys ja lista vaativat urakka-id:n ja oikeudet, joten niitä ei esikatselussa renderöidä."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [liitteet/liitetiedosto staattinen-liitetiedosto]]])

(defn- liitteet-simulaattorissa [_solmu _tila _lapsi-fn]
  [:div {:data-cy "ui-simulaattori-liitteet"}
   [liitteet/liitetiedosto staattinen-liitetiedosto]])

(defn- tietoja-simulaattorissa [solmu _tila _lapsi-fn]
  (let [asetukset (:asetukset solmu)]
    [:div {:data-cy "ui-simulaattori-tietoja"}
     (apply yleiset/tietoja
       {:kavenna? (:kavenna? asetukset)
        :jata-kaventamatta (joukko (:jata-kaventamatta asetukset))
        :otsikot-omalla-rivilla? (:otsikot-omalla-rivilla? asetukset)
        :otsikot-samalla-rivilla (joukko (:otsikot-samalla-rivilla asetukset))
        :tyhja-rivi-otsikon-jalkeen (joukko (:tyhja-rivi-otsikon-jalkeen asetukset))
        :piirra-viivat? (get asetukset :piirra-viivat? true)}
       (rivit-argumentit (:rivit asetukset)))]))

(register! :tietoja {:esikatselu tietoja
                     :renderoi tietoja-simulaattorissa})
(register! :liitteet {:esikatselu liitteet-esikatselu
                      :renderoi liitteet-simulaattorissa})

(defn- taulukkotietonakyma-esikatselu [komponentti]
  [kehys/kortti komponentti
   [:p "Sama otsikko-arvo-data taulukkona. Tyhjä arvo jätetään pois."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/taulukkotietonakyma {}
     "Nimi" "Esimerkki Oy"
     "Tyhjä kenttä" nil
     "Tila" [:span {:data-cy (kehys/ankkuri komponentti "-arvo")} "Hyväksytty"]]]])

(defn- taulukkotietonakyma-simulaattorissa [solmu _tila _lapsi-fn]
  [:div {:data-cy "ui-simulaattori-taulukkotietonakyma"}
   (apply yleiset/taulukkotietonakyma {}
     (rivit-argumentit (get-in solmu [:asetukset :rivit])))])

(register! :taulukkotietonakyma {:esikatselu taulukkotietonakyma-esikatselu
                                 :renderoi taulukkotietonakyma-simulaattorissa})

(defn- kaksi-palstaa-otsikkoja-ja-arvoja [komponentti]
  [kehys/kortti komponentti
   [:p "Vanha grid-pohjainen otsikko-arvo-näkymä. Tyhjä arvo jätetään pois."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/kaksi-palstaa-otsikkoja-ja-arvoja {}
     "Nimi" "Esimerkki Oy"
     "Tyhjä kenttä" nil
     "Tila" [:span {:data-cy (kehys/ankkuri komponentti "-arvo")} "Hyväksytty"]]]])

(defn- kaksi-palstaa-simulaattorissa [solmu _tila _lapsi-fn]
  [:div {:data-cy "ui-simulaattori-kaksi-palstaa-otsikkoja-ja-arvoja"}
   (apply yleiset/kaksi-palstaa-otsikkoja-ja-arvoja {}
     (rivit-argumentit (get-in solmu [:asetukset :rivit])))])

(register! :kaksi-palstaa-otsikkoja-ja-arvoja {:esikatselu kaksi-palstaa-otsikkoja-ja-arvoja
                                               :renderoi kaksi-palstaa-simulaattorissa})

(defn- suodatin-simulaattorissa-sisalto [solmu]
  (r/with-let [tunniste (str (gensym "ui-simulaattori-suodatin-"))
               haku (r/atom "")]
    [ui/suodatin
     (merge {:data-cy "ui-simulaattori-suodatin"}
            (select-keys (:asetukset solmu) [:otsikko :data-cy])
            {:id tunniste})
     (fn [{:keys [id]}]
       [kentat/tee-kentta {:tyyppi :string :elementin-id id} haku])]))

(register! :suodatin {:esikatselu suodatin
                      :renderoi (sisalto-renderoijana suodatin-simulaattorissa-sisalto)})

(defn- lomake-simulaattorissa [solmu _tila lapsi-fn]
  [ui/pino {:data-cy "ui-simulaattori-lomake"}
   (map-indexed (fn [indeksi lapsi]
                  ^{:key indeksi}
                  [lapsi-fn lapsi])
                (:lapset solmu))])

(register! :lomake {:esikatselu lomake
                    :renderoi lomake-simulaattorissa})
(register! :rivi {:esikatselu rivi})
(defn- tekstikentta-simulaattorissa-sisalto [solmu]
  (r/with-let [arvo (r/atom "")]
    [:div {:data-cy "ui-simulaattori-tekstikentta"}
     [kentat/tee-kentta
      (merge {:tyyppi :string
              :vayla-tyyli? true}
        (select-keys (:asetukset solmu) [:placeholder :disabled?])
        (:kentta-attribuutit solmu)
        (when-let [id (get-in solmu [:kentta-attribuutit :id])]
          {:elementin-id id}))
      arvo]]))

(defn- numerokentta-simulaattorissa-sisalto [solmu]
  (r/with-let [arvo (r/atom nil)]
    (let [asetukset (:asetukset solmu)]
      [:div {:data-cy "ui-simulaattori-numerokentta"}
       [kentat/tee-kentta
        (merge {:tyyppi :numero
                :vayla-tyyli? true
                :desimaalien-maara (numero-tai (:desimaalien-maara asetukset) 2)
                :kokonaisosan-maara (numero-tai (:kokonaisosan-maara asetukset) 6)
                :kokonaisluku? (:kokonaisluku? asetukset)
                :vaadi-ei-negatiivinen? (get asetukset :vaadi-ei-negatiivinen? true)}
          (:kentta-attribuutit solmu)
          (when-let [id (get-in solmu [:kentta-attribuutit :id])]
            {:elementin-id id}))
        arvo]])))

(defn- paivamakentta-simulaattorissa-sisalto [solmu]
  (r/with-let [arvo (r/atom nil)]
    [:div {:data-cy "ui-simulaattori-paivamakentta"}
     [kentat/tee-kentta
      (merge {:tyyppi :pvm
              :vayla-tyyli? true}
        (select-keys (:asetukset solmu) [:placeholder :disabled?])
        (:kentta-attribuutit solmu)
        (when-let [id (get-in solmu [:kentta-attribuutit :id])]
          {:elementin-id id}))
      arvo]]))

(defn- tekstialue-simulaattorissa-sisalto [solmu]
  (r/with-let [arvo (r/atom "")]
    [:div {:data-cy "ui-simulaattori-tekstialue"}
     [kentat/tee-kentta
      (merge {:tyyppi :text
              :vayla-tyyli? true
              :koko [80 4]}
        (select-keys (:asetukset solmu) [:placeholder :disabled?])
        (:kentta-attribuutit solmu)
        (when-let [id (get-in solmu [:kentta-attribuutit :id])]
          {:elementin-id id}))
      arvo]]))

(defn- sahkopostikentta-simulaattorissa-sisalto [solmu]
  (r/with-let [arvo (r/atom "")]
    [:div {:data-cy "ui-simulaattori-sahkopostikentta"}
     [kentat/tee-kentta
      (merge {:tyyppi :email}
             (select-keys (:asetukset solmu) [:placeholder :disabled?]))
      arvo]]))

(defn- puhelinkentta-simulaattorissa-sisalto [solmu]
  (r/with-let [arvo (r/atom "")]
    [:div {:data-cy "ui-simulaattori-puhelinkentta"}
     [kentat/tee-kentta
      (merge {:tyyppi :puhelin :vayla-tyyli? true}
             (select-keys (:asetukset solmu) [:placeholder :pituus :vayla-tyyli? :disabled?]))
      arvo]]))

(defn- valintaruutu-simulaattorissa-sisalto [solmu]
  (r/with-let [valittu? (r/atom false)
               input-id (str (gensym "ui-simulaattori-valintaruutu-"))]
    [:div {:data-cy (or (get-in solmu [:asetukset :data-cy]) "ui-simulaattori-valintaruutu")}
     [kentat/tee-kentta
      (merge {:tyyppi :checkbox
              :input-id input-id}
             (select-keys (:asetukset solmu) [:teksti :disabled? :iso-clickalue?]))
      valittu?]]))

(defn- raksiboksi-simulaattorissa-sisalto [solmu]
  (r/with-let [valittu? (r/atom false)]
    [:div {:data-cy "ui-simulaattori-raksiboksi"}
     [kentat/raksiboksi
      (merge (select-keys (:asetukset solmu) [:teksti :tiivis? :disabled?])
             ;; Toiminto saa tapahtuman, ei uutta arvoa, joten arvo käännetään.
             {:toiminto (fn [_tapahtuma] (swap! valittu? not))})
      valittu?]]))

(defn- radiokentta-simulaattorissa-sisalto [solmu]
  (r/with-let [valittu (r/atom nil)]
    (let [{:keys [valinnat disabled?]} (:asetukset solmu)]
      [:div {:data-cy "ui-simulaattori-radiokentta"}
       [kentat/tee-kentta
        {:tyyppi :radio
         :valinnat (vektori valinnat)
         :valinta-nayta vaihtoehdon-teksti
         :disabled? disabled?}
        valittu]])))

(defn- checkbox-ryhma-simulaattorissa-sisalto [solmu]
  (r/with-let [valitut (r/atom #{})]
    (let [{:keys [vaihtoehdot palstoja disabloi]} (:asetukset solmu)]
      [:div {:data-cy "ui-simulaattori-checkbox-ryhma"}
       [kentat/tee-kentta
        {:tyyppi :checkbox-group
         :vaihtoehdot (mapv vaihtoehto-arvo (vektori vaihtoehdot))
         :vaihtoehto-nayta vaihtoehdon-teksti
         :palstoja (positiivinen-numero palstoja)
         ;; Komponentti kutsuu disabloi-funktiota jokaiselle vaihtoehdolle, siksi boolean muutetaan funktioksi.
         :disabloi (when disabloi (constantly true))}
        valitut]])))

(defn- radio-group-simulaattorissa-sisalto [solmu]
  (r/with-let [valittu (r/atom nil)]
    (let [{:keys [vaihtoehdot oletusarvo nayta-rivina? vayla-tyyli? disabloitu?]} (:asetukset solmu)]
      [:div {:data-cy "ui-simulaattori-radio-group"}
       [kentat/tee-kentta
        {:tyyppi :radio-group
         :vaihtoehdot (vektori vaihtoehdot)
         :vaihtoehto-nayta vaihtoehdon-teksti
         :oletusarvo oletusarvo
         :nayta-rivina? nayta-rivina?
         :vayla-tyyli? vayla-tyyli?
         :disabloitu? disabloitu?}
        valittu]])))

(defn- valinta-simulaattorissa-sisalto [solmu]
  (r/with-let [valittu (r/atom nil)]
    (let [{:keys [valinnat disabled? data-cy]} (:asetukset solmu)]
      [kentat/tee-kentta
       {:tyyppi :valinta
        :valinnat (vektori valinnat)
        :valinta-nayta vaihtoehdon-teksti
        :disabled? disabled?
        :data-cy (or data-cy "ui-simulaattori-valinta")}
       valittu])))

(register! :tekstikentta {:esikatselu tekstikentta
                          :renderoi (sisalto-renderoijana tekstikentta-simulaattorissa-sisalto)})
(register! :numerokentta {:esikatselu numerokentta
                          :renderoi (sisalto-renderoijana numerokentta-simulaattorissa-sisalto)})
(register! :paivamakentta {:esikatselu paivamakentta
                           :renderoi (sisalto-renderoijana paivamakentta-simulaattorissa-sisalto)})
(register! :tekstialue {:esikatselu tekstialue
                        :renderoi (sisalto-renderoijana tekstialue-simulaattorissa-sisalto)})
(register! :sahkopostikentta {:esikatselu sahkopostikentta
                              :renderoi (sisalto-renderoijana sahkopostikentta-simulaattorissa-sisalto)})
(register! :puhelinkentta {:esikatselu puhelinkentta
                           :renderoi (sisalto-renderoijana puhelinkentta-simulaattorissa-sisalto)})
(register! :valintaruutu {:esikatselu valintaruutu
                          :renderoi (sisalto-renderoijana valintaruutu-simulaattorissa-sisalto)})
(register! :checkbox {:esikatselu valintaruutu
                      :renderoi (sisalto-renderoijana valintaruutu-simulaattorissa-sisalto)})
(register! :raksiboksi {:esikatselu raksiboksi
                        :renderoi (sisalto-renderoijana raksiboksi-simulaattorissa-sisalto)})
(register! :radiokentta {:esikatselu radiokentta
                         :renderoi (sisalto-renderoijana radiokentta-simulaattorissa-sisalto)})
(register! :checkbox-ryhma {:esikatselu checkbox-ryhma
                            :renderoi (sisalto-renderoijana checkbox-ryhma-simulaattorissa-sisalto)})
(register! :radio-group {:esikatselu radio-group-kentta
                         :renderoi (sisalto-renderoijana radio-group-simulaattorissa-sisalto)})
(register! :valinta {:esikatselu valinta-kentta
                     :renderoi (sisalto-renderoijana valinta-simulaattorissa-sisalto)})
(register! :dropdown {:esikatselu dropdown-valikko})

(defn- asettelu-simulaattorissa [id asettelu-fn]
  (fn [solmu _tila lapsi-fn]
    (into [asettelu-fn
           (merge {:data-cy (str "ui-simulaattori-" (name id))}
             (:asetukset solmu))]
      (map-indexed (fn [indeksi lapsi]
                     ^{:key indeksi}
                     [lapsi-fn lapsi])
        (:lapset solmu)))))

(defn- tilaviesti-simulaattorissa [solmu _tila _lapsi-fn]
  [ui/tilaviesti
   (merge {:data-cy "ui-simulaattori-tilaviesti"}
     (:asetukset solmu))])

(defn- osio-simulaattorissa [solmu _tila lapsi-fn]
  (into [ui/osio
         (merge {:id "ui-simulaattori-osio"
                 :data-cy "ui-simulaattori-osio"}
           (:asetukset solmu))]
    (map-indexed (fn [indeksi lapsi]
                   ^{:key indeksi}
                   [lapsi-fn lapsi])
      (:lapset solmu))))

(register! :pino {:esikatselu pino
                  :renderoi (asettelu-simulaattorissa :pino ui/pino)})
(register! :vaaka {:esikatselu vaaka
                   :renderoi (asettelu-simulaattorissa :vaaka ui/vaaka)})
(register! :sailio {:esikatselu sailio
                    :renderoi (asettelu-simulaattorissa :sailio ui/sailio)})
(register! :osio {:esikatselu osio
                  :renderoi osio-simulaattorissa})
(register! :ruudukko {:esikatselu ruudukko
                      :renderoi (asettelu-simulaattorissa :ruudukko ui/ruudukko)})
(register! :suodatinrivi {:esikatselu suodatinrivi
                          :renderoi (asettelu-simulaattorissa :suodatinrivi ui/suodatinrivi)})
(register! :toimintoryhma {:esikatselu toimintoryhma
                           :renderoi (asettelu-simulaattorissa :toimintoryhma ui/toimintoryhma)})
(register! :tilaviesti {:esikatselu tilaviesti
                        :renderoi tilaviesti-simulaattorissa})
