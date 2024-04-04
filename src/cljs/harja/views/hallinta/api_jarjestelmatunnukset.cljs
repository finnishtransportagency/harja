(ns harja.views.hallinta.api-jarjestelmatunnukset
  "Harja API:n järjestelmätunnuksien listaus ja muokkaus."
  (:require [clojure.string :as str]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.hallinta.api-jarjestelmatunnukset :as tiedot]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]))

(defn- api-jarjestelmatunnukset [e! {:keys [jarjestelmatunnukset mahdolliset-api-oikeudet]}]
  (let [ei-muokattava (constantly false)]
    [:div.jarjestelmatunnukset-grid
     [:div
      [:h2.header-yhteiset "API järjestelmätunnukset"]
      [:p "Tästä voi muokata käyttäjätunnuksia ja niiden api-oikeuksia, 'kirjoitus' oikeus antaa myös 'luku' oikeuden."]
      [:p "Lukuoikeudella voidaan hakea tietoja esim. yhteystiedot, urakan tiedot tms."]
      [:p "Kirjoitus oikeudella voidaan sekä hakea tietoja että kirjoittaa esim. toteumien lisäystä/poistoa tms."]]

     [grid/grid {:tallenna #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaJarjestelmatunnukset %)
                 :tyhja (if (nil? jarjestelmatunnukset)
                          [ajax-loader "Haetaan järjestelmätunnuksia..."]
                          "Järjestelmätunnuksia ei löytynyt")}
      [{:otsikko "Käyttäjänimi"
        :nimi :kayttajanimi
        :tyyppi :string
        :leveys 5}
       {:otsikko "Organisaatio"
        :nimi :organisaatio
        :fmt :nimi
        :tyyppi :valinta
        :valinnat (sort-by :nimi (tiedot/organisaatiovalinnat))
        :valinta-nayta :nimi
        :leveys 5}
       {:otsikko "Käynnissä olevat urakat"
        :nimi :urakat
        :fmt #(str/join ", " %)
        :muokattava? ei-muokattava
        :leveys 15}
       {:otsikko "Luotu"
        :nimi :luotu
        :tyyppi :pvm
        :fmt pvm/pvm-aika-opt
        :muokattava? ei-muokattava
        :leveys 5}
       {:otsikko "Kuvaus"
        :nimi :kuvaus :tyyppi :string
        :leveys 5}
       {:otsikko "Oikeudet"
        :leveys 5
        :tyyppi :komponentti
        :aseta (fn [rivi uusi]
                 (assoc rivi :oikeudet uusi))
        :komponentti (fn
                       ;; Destrukturoi ja uudelleennimeä ensimmäisestä parametrista (kayttaja) :kayttajanimi sekä :oikeudet
                       [{oikeudet :oikeudet :as rivi} {:keys [muokataan?
                                                              komp-muokkaa-fn]}]
                       (if muokataan?
                         ;; Kun gridi on muokattava, tehdään alasveto valinnat oikeuksilla 
                         [:span.label-ja-kentta
                          [:div.kentta
                           [yleiset/livi-pudotusvalikko
                            ;; Näytä dropdownissa montako oikeutta käyttäjällä on 
                            {:naytettava-arvo (str (count oikeudet) " valittu")
                             :itemit-komponentteja? true}

                            ;; Destrukturoi ja uudelleennimeä (:enumlabel @api-oikeudet-atom)
                            (mapv (fn [{oikeus :enumlabel}]
                                    [harja.ui.kentat/tee-kentta
                                     {:input-id (str "input-" oikeus "-" (:id rivi))
                                      :label-id (str "label-" oikeus "-" (:id rivi))
                                      :tyyppi :checkbox
                                      :teksti (str/replace oikeus "kirjoitus" "kirjoitus + luku")
                                      :valitse! (fn [e]
                                                  (let [valittu? (-> e .-target .-checked)]
                                                    (komp-muokkaa-fn rivi
                                                      (cond
                                                        ;; Jos halutaan poistaa, poistetaan oikeus
                                                        (not valittu?) (remove (fn [a] (= a oikeus)) oikeudet)

                                                        ;; Jos halutaan lisätä lukuoikeus, poistetaan kirjoitusoikeus
                                                        (= oikeus "luku") (as-> oikeudet oikeudet
                                                                            (remove (fn [a] (= a "kirjoitus")) oikeudet)
                                                                            (conj (set oikeudet) "luku"))
                                                        ;; Jos halutaan lisätä kirjoitusoikeus, poistetaan luku
                                                        (= oikeus "kirjoitus") (as-> oikeudet oikeudet
                                                                                 (remove (fn [a] (= a "luku")) oikeudet)
                                                                                 (conj (set oikeudet) "kirjoitus"))

                                                        ;; Muut arvot lisätään vaan jos ei ole olemassa
                                                        :else (conj (set oikeudet) oikeus))
                                                      )))}
                                     (some #(= % oikeus) oikeudet)])
                              mahdolliset-api-oikeudet)]]]
                         ;; Kun gridi ei ole muokattava, näytetään käyttäjän oikeudet 
                         [:span (str/replace (str/join ", " oikeudet) "kirjoitus" "kirjoitus + luku")]))}]
      jarjestelmatunnukset]]))

(defn jarjestelmatunnuksen-lisaoikeudet [e! _app kayttaja-id]
  (e! (tiedot/->HaeJarjestelmaTunnuksenLisaoikeudet kayttaja-id))
  (fn [e! {:keys [jarjestelmatunnuksen-lisaoikeudet urakkavalinnat]} kayttaja-id]
    (let [lisaoikeudet (or (get jarjestelmatunnuksen-lisaoikeudet kayttaja-id) [])]
    [grid/grid
     {:otsikko "Lisäoikeudet urakoihin"
      :tunniste :urakka-id
      :tyhja "Ei lisäoikeuksia"
      :tallenna #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaJarjestelmaTunnuksenLisaoikeudet % kayttaja-id)}
     [{:otsikko "Urakka"
       :nimi :urakka-id
       :fmt #(:nimi (first (filter (fn [urakka] (= (:id urakka) %)) urakkavalinnat)))
       :tyyppi :valinta
       :valinta-arvo :id
       :valinnat urakkavalinnat
       :valinta-nayta #(or (:nimi %) "- Valitse urakka -")
       :leveys 3}
      {:otsikko "Oikeus"
       :nimi :kuvaus
       :hae (fn [] "Täydet oikeudet")
       :tyyppi :string
       :muokattava? (constantly false)
       :leveys 2}]
     lisaoikeudet])))

(defn- jarjestelmatunnuksien-lisaoikeudet [e! {:keys [jarjestelmatunnukset] :as app}]
  [:div
   [:div
    [:h2.header-yhteiset "API-järjestelmätunnusten lisäoikeudet urakoihin"]
    [:p "Tästä annetaan käyttäjille urakoihin oikeuksia."]]
   [grid/grid
    {:tunniste :id
     :tallenna nil
     :vetolaatikot (into {} (map (juxt :id #(-> [jarjestelmatunnuksen-lisaoikeudet e! app (:id %)]))
                              jarjestelmatunnukset))}
    [{:tyyppi :vetolaatikon-tila :leveys 1}
     {:otsikko "Käyttäjänimi"
      :nimi :kayttajanimi
      :muokattava (constantly false)
      :tyyppi :string
      :leveys 15}
     {:otsikko "Urakoitsija"
      :nimi :organisaatio
      :fmt :nimi
      :tyyppi :string
      :muokattava (constantly false)
      :leveys 30}]
    jarjestelmatunnukset]])

(defn api-jarjestelmatunnukset-paakomponentti* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(do
         (e! (tiedot/->HaeUrakat))
         (e! (tiedot/->HaeJarjestelmatunnukset))
         (e! (tiedot/->HaeMahdollisetApiOikeudet))))
    (fn [e! app]
      (let [nakyma-alustettu? (some? (:urakkavalinnat app))]
        (if nakyma-alustettu?
          [:div
           [api-jarjestelmatunnukset e! app]
           [jarjestelmatunnuksien-lisaoikeudet e! app]]
          [ajax-loader "Ladataan..."])))))

(defn api-jarjestelmatunnukset-paakomponentti []
  [tuck/tuck tiedot/tila api-jarjestelmatunnukset-paakomponentti*])
