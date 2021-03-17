(ns harja.views.urakka.pot2.massa-ja-murske-yhteiset
  "Massojen ja murskeiden yhteistä UI-koodia"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.pot2 :as pot2-domain]
            [harja.ui.napit :as napit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))


(def materiaali-jo-kaytossa-str "Materiaali on jo käytössä, eikä sitä voi enää poistaa.")

(defn- materiaalin-nimen-komp [{:keys [ydin tarkennukset fmt toiminto-fn]}]
  (if (= :komponentti fmt)
    [(if toiminto-fn :div :span)
     {:on-click #(when toiminto-fn
                   (do
                     (.stopPropagation %)
                     (toiminto-fn)))
      :style {:cursor "pointer"}}
     [:span.bold ydin]
     ;; Toistaiseksi Tean kanssa sovittu 23.2.2021 ettei näytetä tarkennuksia suluissa
     [:span tarkennukset]]
    (str ydin tarkennukset)))

(defn materiaalin-rikastettu-nimi
  "Formatoi massan tai murskeen nimen. Jos haluat Reagent-komponentin, anna fmt = :komponentti, muuten anna :string"
  [{:keys [tyypit materiaali fmt toiminto-fn]}]
  ;; esim AB16 (AN15, RC40, 2020/09/1234) tyyppi (raekoko, nimen tarkenne, DoP, Kuulamyllyluokka, RC%)
  (let [tyyppi (mk-tiedot/massatyypit-vai-mursketyypit? tyypit)
        [ydin tarkennukset] ((if (= :massa tyyppi)
                               pot2-domain/massan-rikastettu-nimi
                               pot2-domain/murskeen-rikastettu-nimi)
                             tyypit materiaali)
        params {:ydin ydin
                :tarkennukset tarkennukset
                :fmt fmt :toiminto-fn toiminto-fn}]
    (if (= fmt :komponentti)
      [materiaalin-nimen-komp params]
      (materiaalin-nimen-komp params))))

(defn materiaalin-kaytto
  [materiaali-kaytossa]
  (when-not (empty? materiaali-kaytossa)
    (let [lukitut-kohteet (filter #(when (str/includes? (:tila %) "lukittu")
                                     %)
                                  materiaali-kaytossa)
          materiaali-kaytossa (if-not (empty? lukitut-kohteet)
                                lukitut-kohteet
                                materiaali-kaytossa)]
      [:div
       [:h3 (if (not (empty? lukitut-kohteet))
              "Materiaalia on kirjattu päällystysilmoitukseen jonka tila on lukittu. Muokkaamista tai poistamista ei enää sallita. Lukitut kohteet: "
              (str "Materiaalia on kirjattu seuraavissa päällystysilmoituksissa: "))]
       [:ul
        (for [{kohdenumero :kohdenumero
               nimi :nimi
               kohteiden-lkm :kohteiden-lkm} materiaali-kaytossa]
          ^{:key kohdenumero}
          [:li (str "#" kohdenumero " " nimi " (" kohteiden-lkm
                    (if (= 1 kohteiden-lkm)
                      " rivi)"
                      " riviä)"))])]])))

(defn puutelistaus [data muut-validointivirheet]
  (when-not (and (empty? (ui-lomake/puuttuvat-pakolliset-kentat data))
                 (empty? muut-validointivirheet))
    [:div
     [:div "Seuraavat pakolliset kentät pitää täyttää ennen tallentamista: "]
     [:ul
      (for [puute (concat
                    (ui-lomake/puuttuvien-pakollisten-kenttien-otsikot data)
                    muut-validointivirheet)]
        ^{:key (name puute)}
        [:li (name puute)])]]))

(defn tallenna-materiaali-nappi
  [materiaali-kaytossa toiminto-fn disabled tyyppi]
  (assert (#{:massa :murske} tyyppi) "Tallennettavan tyyppi oltava massa tai murske")
  (let [lukittu? (some #(str/includes? % "lukittu")
                       (map :tila materiaali-kaytossa))]
    [napit/tallenna
     "Tallenna"
     (let [materiaalin-str (if (= :murske tyyppi) "Murskeen" "Massan")
           materiaalista-str (if (= :murske tyyppi) "murskeesta" "massasta")]
       (if (empty? materiaali-kaytossa)
         toiminto-fn
         (when-not lukittu?
           (fn []
             (varmista-kayttajalta/varmista-kayttajalta
               {:otsikko (str materiaalin-str " tallentaminen")
                :sisalto
                [:div
                 [:div (str "Materiaali on käytössä päällystysilmoituksissa joita ei ole vielä lukittu, joten muokkaaminen on mahdollista. Jos muokkaat kyseistä materiaalia, tiedot päivittyvät kaikkialla missä materiaalia on käytetty.")]
                 [materiaalin-kaytto materiaali-kaytossa]
                 [:div (str "Haluatko varmasti tallentaa muutokset? Voit myös halutessasi luoda " materiaalista-str " kopion ja muokata sitä.")]]
                :toiminto-fn toiminto-fn
                :hyvaksy "Tallenna"})))))
     {:luokka "medium"
      :disabled (or disabled lukittu?)}]))

(defn poista-materiaali-nappi
  [materiaali-kaytossa toiminto-fn tyyppi]
  (assert (#{:massa :murske} tyyppi) "Poistettavan tyyppi oltava massa tai murske")
  (let [lukittu? (some #(str/includes? % "lukittu")
                       (map :tila materiaali-kaytossa))
        materiaalin-str (if (= :murske tyyppi) "Murskeen" "Massan")]
    [:div {:style {:width "160px"}}
     [napit/poista
      "Poista"
      (fn []
        (varmista-kayttajalta/varmista-kayttajalta
          {:otsikko (str materiaalin-str " poistaminen")
           :sisalto
           [:div (str "Haluatko ihan varmasti poistaa tämän " (clojure.string/lower-case materiaalin-str) "?")]
           :toiminto-fn toiminto-fn
           :hyvaksy "Kyllä"}))
      {:disabled (not (empty? materiaali-kaytossa))
       :luokka "medium"}]
     (when (and (not lukittu?)
                (not (empty? materiaali-kaytossa)))
       [yleiset/vihje materiaali-jo-kaytossa-str])]))

(defn tallennus-ja-puutelistaus
  [e! {:keys [data validointivirheet tallenna-fn voi-tallentaa?
              peruuta-fn poista-fn tyyppi id materiaali-kaytossa voi-muokata?]}]
  [:div
   [puutelistaus (ui-lomake/puuttuvat-pakolliset-kentat data) validointivirheet]
   [:div.flex-row {:style {:align-items "start"}}
    [:div.tallenna-peruuta
     (when voi-muokata?
       [tallenna-materiaali-nappi materiaali-kaytossa tallenna-fn
        voi-tallentaa?
        tyyppi])
     [napit/yleinen (if voi-muokata? "Peruuta" "Sulje") :toissijainen peruuta-fn
      {:luokka "medium"}]]

    (when (and id voi-muokata?)
      [poista-materiaali-nappi materiaali-kaytossa poista-fn tyyppi])]
   [materiaalin-kaytto materiaali-kaytossa]])

(defn materiaalin-tiedot [materiaali {:keys [materiaalikoodistot]} toiminto-fn]
  (when (or (::pot2-domain/massa-id materiaali)
            (::pot2-domain/murske-id materiaali))
    [:div.pot2-materiaalin-tiedot.valinta-ja-linkki-container
     [napit/nappi "" toiminto-fn {:luokka "nappi-ikoni valinnan-vierusnappi napiton-nappi"
                                  :ikoni (ikonit/livicon-external)}]
     [materiaalin-rikastettu-nimi {:tyypit ((if (::pot2-domain/murske-id materiaali)
                                              :mursketyypit
                                              :massatyypit) materiaalikoodistot)
                                   :materiaali materiaali
                                   :fmt :komponentti :toiminto-fn toiminto-fn}]]))

(defn materiaalirivin-toiminnot [e! rivi]
  (let [muokkaus-event (if (contains? rivi :harja.domain.pot2/murske-id)
                         mk-tiedot/->MuokkaaMursketta
                         mk-tiedot/->MuokkaaMassaa)]
    [:span.pull-right
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Muokkaa"]
      [napit/nappi ""
       #(e! (muokkaus-event rivi false))
       {:ikoninappi? true :luokka "klikattava"
        :ikoni (ikonit/livicon-pen)}]]

     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Luo kopio"]
      [napit/nappi ""
       #(e! (muokkaus-event rivi true))
       {:ikoninappi? true :luokka "klikattava"
        :ikoni (ikonit/livicon-duplicate)}]]]))

(defn materiaali
  [massat-tai-murskeet {:keys [massa-id murske-id]}]
  (first (filter #(or (= (::pot2-domain/massa-id %) massa-id)
                      (= (::pot2-domain/murske-id %) murske-id))
                 massat-tai-murskeet)))

(defn muokkaa-nappi [muokkaa-fn]
  {:nimi ::pot2-domain/muokkaus :otsikko "" :tyyppi :komponentti :palstoja 3
   :piilota-label? true
   :komponentti (fn [rivi]
                  [napit/muokkaa "Muokkaa"  #(js/setTimeout (fn []
                                                              (muokkaa-fn))
                                                            ;; jos ei timeria, menee lomake kiinni kun klikataan Muokkaa
                                                            ;; Muuten stopPropagation eventtiä ei keretä käsitellä ennen unmountia
                                                            10)
                   {:luokka "napiton-nappi"}])})