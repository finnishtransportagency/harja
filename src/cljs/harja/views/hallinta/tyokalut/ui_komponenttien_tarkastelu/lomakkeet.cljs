(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.lomakkeet
  (:require [reagent.core :as r]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentit :as ui]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]))

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

(def esikatselut
  {:suodatin suodatin
   :valintaryhma valintaryhma
   :lomake lomake
   :rivi rivi
   :tekstikentta tekstikentta
   :numerokentta numerokentta})
