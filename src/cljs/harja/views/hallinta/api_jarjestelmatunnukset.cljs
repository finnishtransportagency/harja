(ns harja.views.hallinta.api-jarjestelmatunnukset
  "Harja API:n järjestelmätunnuksien listaus ja muokkaus."
  (:require [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.api-jarjestelmatunnukset :as tiedot]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- api-jarjestelmatunnukset [jarjestelmatunnukset-atom api-oikeudet-atom]
  (let [ei-muokattava (constantly false)
        ;; Päivittää jarjestelmatunnukset-atom :oikeus arvon valitulle tunnukselle
        fn-paivita-tunnuksen-oikeudet (fn [kayttajanimi oikeus poista?]
                                        (swap! jarjestelmatunnukset-atom
                                          (fn [users]
                                            (map-indexed
                                              (fn [_ user]
                                                (if (= (:kayttajanimi user) kayttajanimi)
                                                  (cond
                                                    ;; Jos halutaan poistaa, poistetaan oikeus
                                                    poista? (update user :oikeudet #(remove (fn [a] (= a oikeus)) %))

                                                    ;; Jos halutaan lisätä lukuoikeus, poistetaan kirjoitusoikeus
                                                    (= oikeus "luku") (-> user
                                                                        (update :oikeudet #(remove (fn [a] (= a "kirjoitus")) %))
                                                                        (update :oikeudet #(conj (set %) "luku")))
                                                    ;; Jos halutaan lisätä kirjoitusoikeus, poistetaan luku
                                                    (= oikeus "kirjoitus") (-> user
                                                                             (update :oikeudet #(remove (fn [a] (= a "luku")) %))
                                                                             (update :oikeudet #(conj (set %) "kirjoitus")))

                                                    ;; Muut arvot lisätään vaan jos ei ole olemassa 
                                                    :else (update-in user [:oikeudet] #(conj (set %) oikeus)))
                                                  user))
                                              users))))]
    [:div.jarjestelmatunnukset-grid
     [:div
      [:h2.header-yhteiset "API järjestelmätunnukset"]
      [:p "Tästä voi muokata käyttäjätunnuksia ja niiden api-oikeuksia, 'kirjoitus' oikeus antaa myös 'luku' oikeuden."]
      [:p "Lukuoikeudella voidaan hakea tietoja esim. yhteystiedot, urakan tiedot tms."]
      [:p "Kirjoitus oikeudella voidaan sekä hakea tietoja että kirjoittaa esim. toteumien lisäystä/poistoa tms."]]

     [grid/grid {:tallenna tiedot/tallenna-jarjestelmatunnukset
                 :tyhja (if (nil? @jarjestelmatunnukset-atom)
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
        ;; Komponentti käyttäjän oikeuksien päivittämiseen
        ;; Ei saanut niin nätisti että muokkausnäkymässä voitaisiin valita checkboxit ja tallenna- funktiossa tehdään muutokset, vaatisi tuckin käyttöä
        :komponentti (fn
                       ;; Destrukturoi ja uudelleennimeä ensimmäisestä parametrista (kayttaja) :kayttajanimi sekä :oikeudet
                       [{kayttajanimi :kayttajanimi kayttajan-oikeudet :oikeudet} {:keys [muokataan?]}]
                       (if muokataan?
                         ;; Kun gridi on muokattava, tehdään alasveto valinnat oikeuksilla 
                         [:span.label-ja-kentta
                          [:div.kentta
                           [yleiset/livi-pudotusvalikko
                            ;; Näytä dropdownissa montako oikeutta käyttäjällä on 
                            {:naytettava-arvo (str (count kayttajan-oikeudet) " valittu")
                             :itemit-komponentteja? true}

                            ;; Destrukturoi ja uudelleennimeä (:enumlabel @api-oikeudet-atom)
                            (mapv (fn [{oikeus :enumlabel}]
                                    [:span.api-tunnus-alasveto-valinnat
                                     (str/replace oikeus "kirjoitus" "kirjoitus + luku")
                                     [:div [:input {:type "checkbox"
                                                    :checked (some #(= % oikeus) kayttajan-oikeudet)
                                                    :on-change #(let [valittu? (-> % .-target .-checked)]
                                                                  ;; Päivitä gridin atomi, tämä triggeröi uudelleenrenderöimisen ja queryttää muokatun oikeuden suoraan tietokantaan 
                                                                  (fn-paivita-tunnuksen-oikeudet kayttajanimi oikeus (not valittu?))
                                                                  (tiedot/aseta-oikeudet-kayttajalle kayttajanimi oikeus valittu?))}]]])
                              @api-oikeudet-atom)]]]
                         ;; Kun gridi ei ole muokattava, näytetään käyttäjän oikeudet 
                         [:span (str/replace (str/join ", " kayttajan-oikeudet) "kirjoitus" "kirjoitus + luku")]))}]
      @jarjestelmatunnukset-atom]]))

(defn jarjestelmatunnuksen-lisaoikeudet [kayttaja-id]
  (let [tunnuksen-oikeudet (atom nil)]
    (tiedot/hae-jarjestelmatunnuksen-lisaoikeudet kayttaja-id tunnuksen-oikeudet)
    (fn []
      [grid/grid
       {:otsikko "Lisäoikeudet urakoihin"
        :tunniste :urakka-id
        :tyhja "Ei lisäoikeuksia"
        :tallenna #(tiedot/tallenna-jarjestelmatunnuksen-lisaoikeudet % kayttaja-id tunnuksen-oikeudet)}
       [{:otsikko "Urakka"
         :nimi :urakka-id
         :fmt #(:nimi (first (filter
                               (fn [urakka] (= (:id urakka) %))
                               @tiedot/urakkavalinnat)))
         :tyyppi :valinta
         :valinta-arvo :id
         :valinnat @tiedot/urakkavalinnat
         :valinta-nayta #(or (:nimi %) "- Valitse urakka -")
         :leveys 3}
        {:otsikko "Oikeus"
         :nimi :kuvaus
         :hae (fn [] "Täydet oikeudet")
         :tyyppi :string
         :muokattava? (constantly false)
         :leveys 2}]
       @tunnuksen-oikeudet])))

(defn- jarjestelmatunnuksien-lisaoikeudet [jarjestelmatunnukset-atom]
  [:div
   [:div
    [:h2.header-yhteiset "API-järjestelmätunnusten lisäoikeudet urakoihin"]
    [:p "Tästä annetaan käyttäjille urakoihin oikeuksia."]]
   [grid/grid
    {:tunniste :id
     :tallenna nil
     :vetolaatikot (into {} (map (juxt :id #(-> [jarjestelmatunnuksen-lisaoikeudet (:id %)]))
                              @jarjestelmatunnukset-atom))}
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
    @jarjestelmatunnukset-atom]])

(defn api-jarjestelmatunnukset-paakomponentti []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      (let [nakyma-alustettu? (some? @tiedot/urakkavalinnat)]
        (if nakyma-alustettu?
          [:div
           [api-jarjestelmatunnukset tiedot/jarjestelmatunnukset tiedot/kaikki-api-oikeudet]
           [jarjestelmatunnuksien-lisaoikeudet tiedot/jarjestelmatunnukset]]
          [ajax-loader "Ladataan..."])))))
