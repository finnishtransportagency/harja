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


(def materiaali-jo-kaytossa-str "Materiaali jo käytössä: ei voida poistaa.")

(defn- rivityyppi-fmt [tyyppi]
  (case tyyppi
    "paallyste" "päällyste"
    "alusta" "alusta"
    ""))

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
       [:div {:style {:margin "16px 0 8px"}}
        (if (not (empty? lukitut-kohteet))
              "Materiaalia on kirjattu päällystysilmoitukseen jonka tila on lukittu. Muokkaamista tai poistamista ei enää sallita. Lukitut kohteet: "
              (str "Materiaalia on kirjattu seuraavissa päällystysilmoituksissa: "))]
       [:ul
        (for [{kohdenumero :kohdenumero
               nimi :nimi
               kohteiden-lkm :kohteiden-lkm
               tyyppi :rivityyppi} materiaali-kaytossa
              :let [tyyppi (rivityyppi-fmt tyyppi)]]
          ^{:key (str kohdenumero "_" tyyppi)}
          [:li (str "#" kohdenumero " " nimi " (" kohteiden-lkm " "
                    (if (= 1 kohteiden-lkm)
                      (str tyyppi "rivi)")
                      (str tyyppi "riviä)")))])]])))

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
     {:luokka "medium pull-left"
      :disabled (or disabled lukittu?)}]))

(defn poistamisen-varmistus-dialogi-fn [materiaalin-str massa-str toiminto-fn]
  (varmista-kayttajalta/varmista-kayttajalta
    {:otsikko (str materiaalin-str " poistaminen")
     :sisalto
     [:div (str "Haluatko ihan varmasti poistaa "
                (str/lower-case materiaalin-str)
                " "
                massa-str
                "?")]
     :toiminto-fn toiminto-fn
     :hyvaksy "Kyllä"}))

(defn poista-materiaali-nappi
  [materiaali-kaytossa materiaalin-nimi toiminto-fn tyyppi]
  (assert (#{:massa :murske} tyyppi) "Poistettavan tyyppi oltava massa tai murske")
  (let [lukittu? (some #(str/includes? % "lukittu")
                       (map :tila materiaali-kaytossa))
        materiaalin-str (if (= :murske tyyppi) "Murskeen" "Massan")]
    [:div.inline-block
     [napit/poista
      "Poista"
      (fn []
        (poistamisen-varmistus-dialogi-fn materiaalin-str materiaalin-nimi toiminto-fn))
      {:disabled (not (empty? materiaali-kaytossa))
       :luokka "medium pull-left"}]
     (when (and (not lukittu?)
                (not (empty? materiaali-kaytossa)))
       [yleiset/vihje materiaali-jo-kaytossa-str])]))

(defn tallennus-ja-puutelistaus
  [e! {:keys [data validointivirheet tallenna-fn voi-tallentaa?
              peruuta-fn poista-fn tyyppi id materiaali-kaytossa voi-muokata?
              materiaalin-nimi]}]
  [:div
   [puutelistaus data validointivirheet]
   [:div
    (when voi-muokata?
      [tallenna-materiaali-nappi materiaali-kaytossa tallenna-fn
       voi-tallentaa?
       tyyppi])
    (when (and id voi-muokata?)
      [poista-materiaali-nappi materiaali-kaytossa materiaalin-nimi poista-fn tyyppi])
    [napit/yleinen (if voi-muokata? "Peruuta" "Sulje") :toissijainen peruuta-fn
     {:luokka "medium pull-right"}]]
   [materiaalin-kaytto materiaali-kaytossa]])

(defn materiaalin-tiedot [materiaali {:keys [materiaalikoodistot]} toiminto-fn]
  (when (or (::pot2-domain/massa-id materiaali)
            (::pot2-domain/murske-id materiaali))
    [:div.pot2-materiaalin-tiedot.valinta-ja-linkki-container
     [napit/nappi "" toiminto-fn {:luokka "nappi-ikoni valinnan-vierusnappi napiton-nappi"
                                  :ikoni (ikonit/livicon-external)}]
     [mk-tiedot/materiaalin-rikastettu-nimi {:tyypit ((if (::pot2-domain/murske-id materiaali)
                                                        :mursketyypit
                                                        :massatyypit) materiaalikoodistot)
                                             :materiaali materiaali
                                             :fmt :komponentti :toiminto-fn toiminto-fn}]]))

(defn materiaalirivin-toiminnot [e! rivi]
  (let [murske? (contains? rivi :harja.domain.pot2/murske-id)
        kaytossa? (boolean (not-empty (::pot2-domain/kaytossa rivi)))
        muokkaus-event (if murske?
                         mk-tiedot/->MuokkaaMursketta
                         mk-tiedot/->MuokkaaMassaa)
        poisto-event (if murske?
                       mk-tiedot/->PoistaMurske
                       mk-tiedot/->PoistaMassa)
        poiston-tooltip (if kaytossa?
                          "Materiaalia on jo kirjattu, eikä sitä voida poistaa."
                          "Poista")
        materiaalityypin-str (if murske?
                          "Murskeen "
                          "Massan ")
        materiaalin-nimi-str (if murske?
                               (::pot2-domain/murskeen-nimi rivi)
                               (::pot2-domain/massan-nimi rivi))]
    [:span.pull-right.materiaalitoiminnot
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Muokkaa"]
      [napit/nappi ""
       #(e! (muokkaus-event rivi false))
       {:luokka "napiton-nappi btn-xs"
        :ikoninappi? true
        :ikoni (ikonit/harja-icon-action-edit)}]]

     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Luo kopio"]
      [napit/nappi ""
       #(e! (muokkaus-event rivi true))
       {:luokka "napiton-nappi btn-xs"
        :ikoninappi? true
        :ikoni (ikonit/harja-icon-action-copy)}]]

     [yleiset/wrap-if true
      [yleiset/tooltip {} :% poiston-tooltip]
      [napit/nappi ""
       (when-not kaytossa?
         (fn []
           (poistamisen-varmistus-dialogi-fn materiaalityypin-str
                                             materiaalin-nimi-str
                                             (fn [_]
                                               (e! (poisto-event (if murske?
                                                                   (::pot2-domain/murske-id rivi)
                                                                   (::pot2-domain/massa-id rivi))))))))
       {:luokka (yleiset/luokat "napiton-nappi"
                                "btn-xs"
                                ;; halutaan laittaa disabledista vain luokka, koska napin disabled häiritsee tooltipin toimintaa
                                (when kaytossa? "disabled"))
        :ikoninappi? true
        :ikoni (ikonit/harja-icon-action-delete)}]]]))

(defn muokkaa-nappi [muokkaa-fn]
  {:nimi ::pot2-domain/muokkaus :otsikko " " :tyyppi :komponentti :palstoja 3
   :piilota-label? true
   :komponentti (fn [rivi]
                  [napit/muokkaa " Muokkaa "
                   #(yleiset/fn-viiveella muokkaa-fn)
                   {:luokka " napiton-nappi "}])})